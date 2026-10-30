package app.quadro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class DataFormatException(message: String) : Exception(message)

data class FeedInfo(val id: String, val name: String, val tag: String, val unit: String)

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Secondi o millisecondi: sopra 1e11 è sicuramente un valore in millisecondi. */
fun normalizeTimeMillis(raw: Double): Long =
    if (raw > 1e11) raw.toLong() else (raw * 1000.0).toLong()

private fun JsonElement.asDouble(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val direct = p.doubleOrNull
    if (direct != null) return direct
    return parseNumber(p.content)
}

private fun JsonElement.asText(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
}

private fun parseRoot(text: String): JsonElement =
    try {
        lenientJson.parseToJsonElement(text)
    } catch (e: Exception) {
        throw DataFormatException("La risposta non è nel formato atteso")
    }

private fun failIfErrorObject(root: JsonElement) {
    if (root is JsonObject) {
        val msg = root["message"]?.asText()
        throw DataFormatException(msg ?: "Risposta inattesa dal server")
    }
}

/** Risposta di feed/data.json: [[tempo, valore], ...]; i valori null vengono saltati. */
fun parseEmoncmsData(text: String): List<Point> {
    val root = parseRoot(text)
    failIfErrorObject(root)
    val arr = root as? JsonArray ?: throw DataFormatException("Risposta inattesa dal server")
    val out = ArrayList<Point>(arr.size)
    for (item in arr) {
        val pair = item as? JsonArray ?: continue
        if (pair.size < 2) continue
        val t = pair[0].asDouble() ?: continue
        val v = pair[1].asDouble() ?: continue
        if (v.isNaN() || v.isInfinite()) continue
        out.add(Point(normalizeTimeMillis(t), v))
    }
    return out
}

/** Risposta di feed/value.json: numero nudo, numero tra virgolette o oggetto con "value". */
fun parseEmoncmsValue(text: String): Double {
    val trimmed = text.trim()
    val el = try {
        lenientJson.parseToJsonElement(trimmed)
    } catch (e: Exception) {
        null
    }
    val fromJson = when (el) {
        is JsonObject -> el["value"]?.asDouble()
        is JsonPrimitive -> el.asDouble()
        else -> null
    }
    return fromJson
        ?: trimmed.trim('"').toDoubleOrNull()
        ?: throw DataFormatException("Valore non valido dal server")
}

/** Risposta di feed/list.json: elenco di oggetti con almeno id e name. */
fun parseEmoncmsFeedList(text: String): List<FeedInfo> {
    val root = parseRoot(text)
    failIfErrorObject(root)
    val arr = root as? JsonArray ?: throw DataFormatException("Risposta inattesa dal server")
    val out = ArrayList<FeedInfo>()
    for (item in arr) {
        val obj = item as? JsonObject ?: continue
        val id = obj["id"]?.asText() ?: continue
        val name = obj["name"]?.asText() ?: id
        out.add(FeedInfo(id, name, obj["tag"]?.asText() ?: "", obj["unit"]?.asText() ?: ""))
    }
    return out
}

/**
 * Link pubblico JSON. [arrayPath] è un percorso con punti verso l'elenco ("data.points"),
 * vuoto se la radice è già un elenco. Gli elementi sono coppie [tempo, valore] oppure oggetti
 * con [timeKey] e [valueKey].
 */
fun parseLinkJson(
    text: String,
    arrayPath: String,
    timeKey: String,
    valueKey: String,
    zone: ZoneId,
): List<Point> {
    var node: JsonElement = parseRoot(text)
    for (part in arrayPath.split('.').map { it.trim() }.filter { it.isNotEmpty() }) {
        val current = node
        node = when (current) {
            is JsonObject -> current[part] ?: throw DataFormatException("Percorso non trovato: $part")
            is JsonArray -> current.getOrNull(part.toIntOrNull() ?: -1)
                ?: throw DataFormatException("Percorso non trovato: $part")
            else -> throw DataFormatException("Percorso non trovato: $part")
        }
    }
    val arr = node as? JsonArray ?: throw DataFormatException("Il percorso indicato non porta a un elenco")
    val out = ArrayList<Point>(arr.size)
    for (item in arr) {
        val tEl: JsonElement?
        val vEl: JsonElement?
        when (item) {
            is JsonArray -> {
                val ti = timeKey.toIntOrNull() ?: 0
                val vi = valueKey.toIntOrNull() ?: 1
                tEl = item.getOrNull(ti)
                vEl = item.getOrNull(vi)
            }
            is JsonObject -> {
                if (timeKey.isBlank() || valueKey.isBlank()) {
                    throw DataFormatException("Indica la chiave del tempo e quella del valore")
                }
                tEl = item[timeKey]
                vEl = item[valueKey]
            }
            else -> continue
        }
        val t = timeOf(tEl, zone) ?: continue
        val v = vEl?.asDouble() ?: continue
        if (v.isNaN() || v.isInfinite()) continue
        out.add(Point(t, v))
    }
    if (out.isEmpty()) throw DataFormatException("Nessun dato valido nel link")
    return out.sortedBy { it.t }
}

private fun timeOf(el: JsonElement?, zone: ZoneId): Long? {
    val p = el as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return parseTimeFlexible(p.content, zone)
}

/** Link pubblico CSV con riga di intestazione. Separatore riconosciuto tra , ; e tabulazione. */
fun parseLinkCsv(text: String, timeKey: String, valueKey: String, zone: ZoneId): List<Point> {
    val lines = text.lineSequence()
        .map { it.trimStart('﻿').trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.size < 2) throw DataFormatException("Il file CSV è vuoto")
    val delimiter = detectDelimiter(lines[0])
    val header = splitCsvLine(lines[0], delimiter).map { it.trim() }
    val ti = columnIndex(header, timeKey, 0)
    val vi = columnIndex(header, valueKey, 1)
    val out = ArrayList<Point>()
    for (line in lines.drop(1)) {
        val cols = splitCsvLine(line, delimiter)
        val t = cols.getOrNull(ti)?.let { parseTimeFlexible(it, zone) } ?: continue
        val v = cols.getOrNull(vi)?.let { parseNumber(it) } ?: continue
        out.add(Point(t, v))
    }
    if (out.isEmpty()) throw DataFormatException("Nessun dato valido nel CSV")
    return out.sortedBy { it.t }
}

private fun detectDelimiter(headerLine: String): Char {
    val candidates = charArrayOf(';', '\t', ',')
    return candidates.maxByOrNull { c -> headerLine.count { it == c } }
        ?.takeIf { c -> headerLine.contains(c) } ?: ','
}

private fun columnIndex(header: List<String>, key: String, default: Int): Int {
    if (key.isBlank()) return default
    val byName = header.indexOfFirst { it.equals(key.trim(), ignoreCase = true) }
    if (byName >= 0) return byName
    val asIndex = key.trim().toIntOrNull()
    if (asIndex != null && asIndex in header.indices) return asIndex
    throw DataFormatException("Colonna non trovata: $key")
}

internal fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                cur.append('"')
                i++
            }
            c == '"' -> inQuotes = !inQuotes
            c == delimiter && !inQuotes -> {
                out.add(cur.toString())
                cur.setLength(0)
            }
            else -> cur.append(c)
        }
        i++
    }
    out.add(cur.toString())
    return out
}

/** Accetta "12.5", "12,5" e "1,234.5". Restituisce null se non è un numero. */
fun parseNumber(raw: String): Double? {
    var s = raw.trim().trim('"').replace(" ", "")
    if (s.isEmpty()) return null
    val hasComma = s.contains(',')
    val hasDot = s.contains('.')
    s = when {
        hasComma && hasDot -> s.replace(",", "")
        hasComma -> s.replace(',', '.')
        else -> s
    }
    val d = s.toDoubleOrNull() ?: return null
    return if (d.isNaN() || d.isInfinite()) null else d
}

private val localDateTimeFormats = listOf(
    "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
    "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm",
    "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm",
).map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }

private val localDateFormats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd.MM.yyyy")
    .map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }

/** Numero (secondi o millisecondi), ISO-8601 con o senza fuso, oppure formati locali comuni. */
fun parseTimeFlexible(raw: String, zone: ZoneId): Long? {
    val s = raw.trim().trim('"')
    if (s.isEmpty()) return null
    s.toDoubleOrNull()?.let { return normalizeTimeMillis(it) }
    try {
        return Instant.parse(s).toEpochMilli()
    } catch (_: DateTimeParseException) {
    }
    try {
        return OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
    }
    for (f in localDateTimeFormats) {
        try {
            return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }
    for (f in localDateFormats) {
        try {
            return LocalDate.parse(s, f).atStartOfDay(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }
    return null
}

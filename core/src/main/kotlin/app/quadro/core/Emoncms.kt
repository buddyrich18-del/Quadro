package app.quadro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder

enum class DataMode { AVERAGE, DELTA }

/** Usa HTTP per host di rete locale (un emonPi di solito non ha HTTPS), HTTPS per tutto il resto. */
fun defaultScheme(hostPart: String): String {
    val host = hostPart.substringBefore('/').substringBefore(':').lowercase()
    val privateHost = host == "localhost" || host.endsWith(".local") ||
        Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|127\\.)").containsMatchIn(host)
    return if (privateHost) "http" else "https"
}

/** Toglie spazi e barra finale; aggiunge lo schema se manca; solleva IllegalArgumentException se non valido. */
fun normalizeBaseUrl(raw: String): String {
    var s = raw.trim().trimEnd('/')
    require(s.isNotEmpty()) { "Indirizzo vuoto" }
    if (!s.contains("://")) s = defaultScheme(s) + "://" + s
    val uri = try {
        URI(s)
    } catch (e: Exception) {
        throw IllegalArgumentException("Indirizzo non valido")
    }
    require(!uri.host.isNullOrBlank() && (uri.scheme == "http" || uri.scheme == "https")) { "Indirizzo non valido" }
    return s
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

object EmoncmsUrls {
    fun feedList(base: String) = "${normalizeBaseUrl(base)}/feed/list.json"

    fun feedValue(base: String, id: String) = "${normalizeBaseUrl(base)}/feed/value.json?id=${enc(id)}"

    fun feedData(base: String, id: String, startMs: Long, endMs: Long, intervalSec: Int, mode: DataMode): String {
        val flag = if (mode == DataMode.DELTA) "delta=1" else "average=1"
        return "${normalizeBaseUrl(base)}/feed/data.json?id=${enc(id)}&start=$startMs&end=$endMs" +
            "&interval=$intervalSec&$flag&skipmissing=1&timeformat=unixms"
    }
}

private val intervalSteps = intArrayOf(10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 14400, 21600, 43200, 86400)

/** Intervallo in secondi per avere circa [targetPoints] punti nel periodo, scelto tra valori tondi. */
fun suggestIntervalSeconds(periodMs: Long, targetPoints: Int = 600): Int {
    val exact = (periodMs / 1000.0 / targetPoints).coerceAtLeast(1.0)
    return intervalSteps.firstOrNull { it >= exact } ?: intervalSteps.last()
}

data class QrAccount(val baseUrl: String, val apiKey: String)

private val hexKey = Regex("^[0-9a-fA-F]{32}$")
private val keyParams = setOf("apikey", "key", "readkey", "read_key")

/**
 * Legge il contenuto di un QR di configurazione. Il formato dell'app ufficiale non è documentato,
 * quindi si accettano più forme: JSON, URL con parametro della chiave, oppure indirizzo e chiave
 * separati da spazio, a capo, | o ;. Restituisce null se non riconosce nulla.
 */
fun parseAccountQr(text: String): QrAccount? {
    val s = text.trim()
    if (s.isEmpty()) return null
    if (s.startsWith("{")) return parseQrJson(s)
    var key: String? = null
    var base: String? = null
    for (tok in s.split(Regex("[\\s|;]+")).filter { it.isNotBlank() }) {
        if (hexKey.matches(tok)) {
            if (key == null) key = tok
            continue
        }
        val query = tok.substringAfter('?', "")
        if (query.isNotEmpty()) {
            for (pair in query.split('&')) {
                val k = pair.substringBefore('=').lowercase()
                val v = pair.substringAfter('=', "")
                if (k in keyParams && v.isNotBlank() && key == null) key = v
            }
        }
        val candidate = tok.substringBefore('?')
        if (base == null && (candidate.contains("://") || candidate.contains('.') || candidate.equals("localhost", true))) {
            base = candidate
        }
    }
    val finalKey = key ?: return null
    val finalBase = base ?: return null
    return try {
        QrAccount(normalizeBaseUrl(finalBase), finalKey)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun parseQrJson(s: String): QrAccount? {
    val obj = try {
        Json.parseToJsonElement(s) as? JsonObject
    } catch (e: Exception) {
        null
    } ?: return null
    fun pick(vararg names: String): String? =
        names.firstNotNullOfOrNull { n -> (obj[n] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
    val url = pick("url", "server", "host", "emoncms_url") ?: return null
    val key = pick("apikey", "key", "readkey", "read_key", "emoncms_apikey") ?: return null
    return try {
        QrAccount(normalizeBaseUrl(url), key)
    } catch (e: IllegalArgumentException) {
        null
    }
}

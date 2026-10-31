package app.quadro.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.max

/** Un valore letto da una pagina web, con la sua etichetta e l'unità. */
data class KioskItem(val label: String, val value: String, val unit: String)

data class KioskResult(val items: List<KioskItem>, val lines: List<String>)

enum class UnitCategory { POWER, ENERGY, OTHER }

fun unitCategory(unit: String): UnitCategory = when (unit.lowercase()) {
    "w", "kw", "mw", "gw" -> UnitCategory.POWER
    "wh", "kwh", "mwh", "gwh" -> UnitCategory.ENERGY
    else -> UnitCategory.OTHER
}

private class Frag(val text: String, val top: Double, val left: Double, val height: Double, val width: Double) {
    val cy: Double get() = top + height / 2.0
    val right: Double get() = left + width
}

private class Cluster(val text: String, val top: Double, val left: Double, val right: Double, val height: Double) {
    val cx: Double get() = (left + right) / 2.0
    val cy: Double get() = top + height / 2.0
    val width: Double get() = right - left
}

private const val UNIT_PATTERN =
    "kW|MW|GW|W|kWh|MWh|GWh|Wh|kWp|Wp|%|€|\\$|£|EUR|USD|kg|t|h|V|A|Hz|°C|°F|m²|m³|kvar|kVA|CO2"

private const val NUMBER_PATTERN = "[-+]?\\d[\\d.,]*"

/** Solo un numero con unità facoltativa, per esempio "12.3 kWh" o "85%". */
private val valueRegex = Regex("^($NUMBER_PATTERN)\\s*($UNIT_PATTERN)?$", RegexOption.IGNORE_CASE)

/** Etichetta e valore sulla stessa riga, per esempio "Potenza attuale 3.2 kW" o "Oggi: 12 kWh". */
private val labelValueRegex = Regex(
    "^(.*\\p{L}.*?[^\\d\\s:])[\\s:]+($NUMBER_PATTERN)\\s*($UNIT_PATTERN)?$",
    RegexOption.IGNORE_CASE,
)

private val unitOnlyRegex = Regex("^($UNIT_PATTERN)$", RegexOption.IGNORE_CASE)

private val unitInLabelRegex = Regex("\\(([^)]+)\\)\\s*$")

private fun makeCluster(group: List<Frag>): Cluster = Cluster(
    text = group.joinToString(" ") { it.text },
    top = group.minOf { it.top },
    left = group.first().left,
    right = group.maxOf { it.right },
    height = group.maxOf { it.height },
)

private fun cleanLabel(raw: String): String = raw.trim().trimEnd(':').trim()

/** Sceglie l'etichetta più vicina al valore: a sinistra sulla stessa riga, sopra nella stessa colonna, o sotto. */
private fun pickLabel(v: Cluster, texts: List<Cluster>, used: MutableSet<Cluster>): Cluster? {
    var best: Cluster? = null
    var bestScore = Double.MAX_VALUE
    for (l in texts) {
        if (l in used) continue
        val hMax = max(l.height, v.height)
        val sameRow = abs(l.cy - v.cy) <= 0.6 * hMax
        val dx = abs(l.cx - v.cx)
        val near = dx <= max(l.width, v.width)
        val dy = v.top - l.top
        val limit = 4 * hMax + 40
        val score = when {
            sameRow && l.right <= v.left + 2 -> v.left - l.right
            !sameRow && dy > 0 && dy <= limit && near -> dy + 2 * dx
            !sameRow && dy < 0 && -dy <= limit && near -> -dy + 2 * dx + 60
            else -> continue
        }
        if (score < bestScore) {
            best = l
            bestScore = score
        }
    }
    if (best != null) used.add(best)
    return best
}

/**
 * Trasforma i frammenti di testo letti da una pagina web in valori con etichetta.
 * Ogni frammento è [testo, alto, sinistra, altezza, larghezza] (pixel della pagina).
 * L'euristica abbina un numero con unità all'etichetta più vicina; non conosce la pagina.
 */
fun parseKioskFragments(json: String): KioskResult {
    val empty = KioskResult(emptyList(), emptyList())
    val root = try {
        Json.parseToJsonElement(json)
    } catch (e: Exception) {
        return empty
    }
    val arr = root as? JsonArray ?: return empty
    val frags = ArrayList<Frag>()
    for (el in arr) {
        val a = el as? JsonArray ?: continue
        if (a.size < 5) continue
        val text = (a[0] as? JsonPrimitive)?.content?.replace(Regex("\\s+"), " ")?.trim() ?: continue
        if (text.isEmpty()) continue
        val n = (1..4).map { (a[it] as? JsonPrimitive)?.doubleOrNull }
        if (n.any { it == null }) continue
        frags.add(Frag(text, n[0]!!, n[1]!!, n[2]!!, n[3]!!))
    }
    if (frags.isEmpty()) return empty

    val sorted = frags.sortedWith(compareBy({ it.top }, { it.left }))
    val rows = ArrayList<MutableList<Frag>>()
    for (f in sorted) {
        val row = rows.lastOrNull()
        if (row != null) {
            val maxH = max(f.height, row.maxOf { it.height })
            if (abs(f.cy - row.first().cy) <= 0.6 * maxH) {
                row.add(f)
                continue
            }
        }
        rows.add(mutableListOf(f))
    }

    val clusters = ArrayList<Cluster>()
    for (row in rows) {
        var group = mutableListOf<Frag>()
        for (f in row.sortedBy { it.left }) {
            val prev = group.lastOrNull()
            if (prev != null && f.left - prev.right > 0.9 * max(f.height, prev.height)) {
                clusters.add(makeCluster(group))
                group = mutableListOf()
            }
            group.add(f)
        }
        if (group.isNotEmpty()) clusters.add(makeCluster(group))
    }

    val valueClusters = HashSet<Cluster>()
    val directItems = HashMap<Cluster, KioskItem>()
    val texts = ArrayList<Cluster>()
    for (c in clusters) {
        val v = valueRegex.matchEntire(c.text)
        if (v != null) {
            valueClusters.add(c)
            continue
        }
        val lv = labelValueRegex.matchEntire(c.text)
        if (lv != null) {
            directItems[c] = KioskItem(cleanLabel(lv.groupValues[1]), lv.groupValues[2], lv.groupValues[3])
            continue
        }
        if (c.text.any { it.isLetter() }) texts.add(c)
    }

    val used = HashSet<Cluster>()
    val items = ArrayList<KioskItem>()
    for (c in clusters) {
        val direct = directItems[c]
        if (direct != null) {
            items.add(direct)
            continue
        }
        if (c !in valueClusters) continue
        val m = valueRegex.matchEntire(c.text) ?: continue
        val labelCluster = pickLabel(c, texts, used) ?: continue
        var label = cleanLabel(labelCluster.text)
        var unit = m.groupValues[2]
        if (unit.isEmpty()) {
            val inLabel = unitInLabelRegex.find(label)
            if (inLabel != null && unitOnlyRegex.matches(inLabel.groupValues[1].trim())) {
                unit = inLabel.groupValues[1].trim()
                label = cleanLabel(label.removeRange(inLabel.range))
            }
        }
        if (label.isNotEmpty()) items.add(KioskItem(label, m.groupValues[1], unit))
    }
    return KioskResult(items, clusters.map { it.text })
}

/** Vero se l'indirizzo è una pagina FusionSolar (per esempio il link kiosk pubblico). */
fun isFusionSolarUrl(url: String): Boolean =
    Regex("^https?://[^/]*fusionsolar[^/]*", RegexOption.IGNORE_CASE).containsMatchIn(url.trim())

/** Un riquadro della scheda FusionSolar: didascalia fissa e valore letto dalla pagina (null se non trovato). */
data class SolarTile(val caption: String, val item: KioskItem?)

val SOLAR_CAPTIONS = listOf(
    "Potenza in tempo reale",
    "Resa di oggi",
    "Resa del mese",
    "Resa dell'anno",
    "Resa totale",
)

private val SOLAR_KEYS = listOf(
    listOf("today", "daily", "day", "oggi", "giorn", "当日", "今日"),
    listOf("month", "mese", "mensil", "当月"),
    listOf("year", "anno", "annu", "当年"),
    listOf("total", "totale", "lifetime", "cumul", "总"),
)

/**
 * Abbina i valori letti dalla pagina alle cinque didascalie, in ordine: potenza, oggi, mese, anno, totale.
 * Si usano le parole dell'etichetta (inglese o italiano) se permettono di riconoscere tutte e quattro le rese;
 * altrimenti si prendono i valori di energia nell'ordine in cui compaiono nella pagina.
 */
fun fusionSolarTiles(items: List<KioskItem>): List<SolarTile> {
    val power = items.firstOrNull { unitCategory(it.unit) == UnitCategory.POWER }
    val energy = items.filter { unitCategory(it.unit) == UnitCategory.ENERGY }
    val byKey = SOLAR_KEYS.map { keys ->
        energy.firstOrNull { e -> keys.any { e.label.lowercase().contains(it) } }
    }
    val chosen: List<KioskItem?> =
        if (byKey.all { it != null } && byKey.toSet().size == 4) byKey else List(4) { energy.getOrNull(it) }
    return SOLAR_CAPTIONS.mapIndexed { i, caption ->
        SolarTile(caption, if (i == 0) power else chosen[i - 1])
    }
}

private val SOLAR_VALUE = Regex("(\\d[\\d.,]*)\\s*(GWh|MWh|kWh|Wh|GW|MW|kW|W)(?![A-Za-z])")

/**
 * Per le pagine FusionSolar: legge tutti i numeri con unità di potenza o energia nell'ordine in cui
 * compaiono (righe dall'alto in basso, da sinistra a destra). Le etichette sono vuote perché la pagina
 * mostra i valori in una riga e i nomi in un'altra: l'abbinamento lo fa fusionSolarTiles per posizione.
 */
fun parseSolarValues(json: String): List<KioskItem> {
    val arr = try {
        Json.parseToJsonElement(json) as? JsonArray
    } catch (e: Exception) {
        null
    } ?: return emptyList()
    val frags = ArrayList<Frag>()
    for (el in arr) {
        val a = el as? JsonArray ?: continue
        if (a.size < 5) continue
        val text = (a[0] as? JsonPrimitive)?.content?.replace(Regex("\\s+"), " ")?.trim() ?: continue
        if (text.isEmpty()) continue
        val n = (1..4).map { (a[it] as? JsonPrimitive)?.doubleOrNull }
        if (n.any { it == null }) continue
        frags.add(Frag(text, n[0]!!, n[1]!!, n[2]!!, n[3]!!))
    }
    val rows = ArrayList<MutableList<Frag>>()
    for (f in frags.sortedWith(compareBy({ it.top }, { it.left }))) {
        val row = rows.lastOrNull()
        if (row != null && abs(f.cy - row.first().cy) <= 0.6 * max(f.height, row.maxOf { it.height })) {
            row.add(f)
        } else {
            rows.add(mutableListOf(f))
        }
    }
    val out = ArrayList<KioskItem>()
    for (row in rows) {
        val text = row.sortedBy { it.left }.joinToString(" ") { it.text }
        for (m in SOLAR_VALUE.findAll(text)) out.add(KioskItem("", m.groupValues[1], m.groupValues[2]))
    }
    return out
}

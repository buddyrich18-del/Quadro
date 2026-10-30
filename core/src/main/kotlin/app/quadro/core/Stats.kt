package app.quadro.core

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.ln

data class SeriesStats(val min: Double, val max: Double, val avg: Double, val last: Double, val count: Int)

fun statsOf(points: List<Point>): SeriesStats? {
    if (points.isEmpty()) return null
    val values = points.map { it.v }
    return SeriesStats(
        min = values.min(),
        max = values.max(),
        avg = values.average(),
        last = points.maxByOrNull { it.t }!!.v,
        count = points.size,
    )
}

/** Riduce una serie a circa [maxPoints] punti facendo la media di gruppi contigui. */
fun downsample(points: List<Point>, maxPoints: Int): List<Point> {
    if (maxPoints <= 1 || points.size <= maxPoints) return points
    val size = ceil(points.size / maxPoints.toDouble()).toInt()
    return points.chunked(size).map { chunk -> Point(chunk[chunk.size / 2].t, chunk.map { it.v }.average()) }
}

private fun aggregate(values: List<Double>, agg: Aggregate): Double = when (agg) {
    Aggregate.AVG -> values.average()
    Aggregate.SUM -> values.sum()
    Aggregate.MIN -> values.min()
    Aggregate.MAX -> values.max()
    Aggregate.LAST -> values.last()
}

/** Raggruppa per ora o per giorno sul fuso indicato (non in UTC). */
fun bucketize(points: List<Point>, bucket: Bucket, agg: Aggregate, zone: ZoneId): List<Point> {
    if (bucket == Bucket.NONE || points.isEmpty()) return points
    val groups = LinkedHashMap<Long, MutableList<Double>>()
    for (p in points.sortedBy { it.t }) {
        val z = Instant.ofEpochMilli(p.t).atZone(zone)
        val start = when (bucket) {
            Bucket.HOUR -> z.truncatedTo(ChronoUnit.HOURS)
            Bucket.DAY -> z.toLocalDate().atStartOfDay(zone)
            Bucket.NONE -> z
        }
        groups.getOrPut(start.toInstant().toEpochMilli()) { mutableListOf() }.add(p.v)
    }
    return groups.map { (start, values) -> Point(start, aggregate(values, agg)) }
}

/** kWh per giorno da delta orari: somma per giorno locale; i valori negativi (azzeramenti del contatore) valgono 0. */
fun dailyKwh(hourlyDeltas: List<Point>, zone: ZoneId): List<Point> =
    bucketize(hourlyDeltas.map { Point(it.t, maxOf(0.0, it.v)) }, Bucket.DAY, Aggregate.SUM, zone)

fun startOfDayMillis(nowMs: Long, zone: ZoneId, daysBack: Int = 0): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().minusDays(daysBack.toLong())
        .atStartOfDay(zone).toInstant().toEpochMilli()

/** kWh consumati dall'inizio della giornata locale. */
fun todayKwh(hourlyDeltas: List<Point>, nowMs: Long, zone: ZoneId): Double {
    val start = startOfDayMillis(nowMs, zone)
    return hourlyDeltas.filter { it.t >= start }.sumOf { maxOf(0.0, it.v) }
}

fun costOf(kwh: Double, unitCost: Double): Double = kwh * unitCost

/** Punto di rugiada in °C con la formula di Magnus. L'umidità relativa è limitata tra 1 e 100. */
fun dewPointC(tempC: Double, humidityPct: Double): Double {
    val a = 17.62
    val b = 243.12
    val rh = humidityPct.coerceIn(1.0, 100.0)
    val gamma = ln(rh / 100.0) + a * tempC / (b + tempC)
    return b * gamma / (a - gamma)
}

enum class Comfort { LOW, OK, HIGH }

fun classify(value: Double, band: Band): Comfort = when {
    value < band.min -> Comfort.LOW
    value > band.max -> Comfort.HIGH
    else -> Comfort.OK
}

enum class Trend { UP, DOWN, FLAT }

/**
 * Confronta la media degli ultimi [recentMs] con quella dei [recentMs] * 2 precedenti.
 * Differenze entro [flatBand] contano come stabili.
 */
fun trendOf(points: List<Point>, flatBand: Double, recentMs: Long = 30L * 60_000L): Trend {
    if (points.size < 2) return Trend.FLAT
    val last = points.maxOf { it.t }
    val recent = points.filter { it.t > last - recentMs }.map { it.v }
    val before = points.filter { it.t > last - 3 * recentMs && it.t <= last - recentMs }.map { it.v }
    if (recent.isEmpty() || before.isEmpty()) return Trend.FLAT
    val diff = recent.average() - before.average()
    return when {
        diff > flatBand -> Trend.UP
        diff < -flatBand -> Trend.DOWN
        else -> Trend.FLAT
    }
}

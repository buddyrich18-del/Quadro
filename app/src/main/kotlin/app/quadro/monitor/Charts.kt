package app.quadro.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quadro.core.Point
import kotlin.math.abs

data class ChartSeries(val label: String, val unit: String, val color: Color, val points: List<Point>)

data class ChartBand(val min: Double, val max: Double, val color: Color)

private fun axisText(v: Double): String = if (abs(v) >= 100) fmtNum(v, 0) else fmtNum(v, 1)

/** Scrive un testo allineato a sinistra (-1), al centro (0) o a destra (1) rispetto a x. */
private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    align: Int,
) {
    val layout = measurer.measure(text, style)
    val w = layout.size.width.toFloat()
    val left = when (align) {
        -1 -> x
        0 -> x - w / 2f
        else -> x - w
    }
    drawText(layout, topLeft = Offset(left, y))
}

@Composable
fun EmptyChart(modifier: Modifier, height: Dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text("Nessun dato", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    yMin: Double? = null,
    yMax: Double? = null,
    bands: List<ChartBand> = emptyList(),
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) {
        EmptyChart(modifier, height)
        return
    }
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val tMin = all.minOf { it.t }
    val tMax = all.maxOf { it.t }
    var lo = yMin ?: all.minOf { it.v }
    var hi = yMax ?: all.maxOf { it.v }
    if (yMin == null && bands.isNotEmpty()) lo = minOf(lo, bands.minOf { it.min })
    if (yMax == null && bands.isNotEmpty()) hi = maxOf(hi, bands.maxOf { it.max })
    if (hi - lo < 1e-9) hi = lo + 1.0
    if (yMin == null) lo -= (hi - lo) * 0.05
    if (yMax == null) hi += (hi - lo) * 0.05
    val finalLo = lo
    val finalHi = hi
    val longSpan = tMax - tMin > 36L * 3_600_000L
    val pattern = if (longSpan) "dd/MM" else "HH:mm"
    var touchX by remember { mutableStateOf<Float?>(null) }
    val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val tipStyle = TextStyle(fontSize = 11.sp, color = textColor)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { offset ->
                    touchX = offset.x
                    tryAwaitRelease()
                    touchX = null
                })
            },
    ) {
        val left = 40.dp.toPx()
        val right = 8.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = 18.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        val span = (tMax - tMin).coerceAtLeast(1L).toFloat()

        fun xOf(t: Long): Float = left + (t - tMin).toFloat() / span * w
        fun yOf(v: Double): Float = top + h - ((v - finalLo) / (finalHi - finalLo)).toFloat() * h

        for (b in bands) {
            val yTop = yOf(b.max).coerceIn(top, top + h)
            val yBot = yOf(b.min).coerceIn(top, top + h)
            if (yBot > yTop) drawRect(b.color, Offset(left, yTop), Size(w, yBot - yTop))
        }
        for (i in 0..3) {
            val v = finalLo + (finalHi - finalLo) * i / 3.0
            val yy = yOf(v)
            drawLine(gridColor, Offset(left, yy), Offset(left + w, yy), strokeWidth = 1f)
            label(measurer, axisText(v), axisStyle, left - 4.dp.toPx(), yy - 7.dp.toPx(), 1)
        }
        val yLabel = top + h + 3.dp.toPx()
        label(measurer, fmtTime(tMin, pattern), axisStyle, left, yLabel, -1)
        label(measurer, fmtTime((tMin + tMax) / 2, pattern), axisStyle, left + w / 2f, yLabel, 0)
        label(measurer, fmtTime(tMax, pattern), axisStyle, left + w, yLabel, 1)

        for (s in series) {
            if (s.points.isEmpty()) continue
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val px = xOf(p.t)
                val py = yOf(p.v)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path,
                s.color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        val tx = touchX
        if (tx != null && w > 0f) {
            val frac = ((tx - left) / w).coerceIn(0f, 1f).toDouble()
            val tSel = tMin + (frac * (tMax - tMin)).toLong()
            val picks = series.mapNotNull { s ->
                s.points.minByOrNull { abs(it.t - tSel) }?.let { s to it }
            }
            if (picks.isNotEmpty()) {
                val xSel = xOf(picks.first().second.t)
                drawLine(axisColor, Offset(xSel, top), Offset(xSel, top + h), strokeWidth = 1.dp.toPx())
                for ((s, p) in picks) {
                    drawCircle(s.color, 4.dp.toPx(), Offset(xSel, yOf(p.v)))
                }
                val text = picks.joinToString("   ") { (s, p) ->
                    val u = if (s.unit.isBlank()) "" else " ${s.unit}"
                    "${s.label} ${fmtNum(p.v, 1)}$u"
                } + "   " + fmtTime(picks.first().second.t, "dd/MM HH:mm")
                label(measurer, text, tipStyle, left, 0f, -1)
            }
        }
    }
}

@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    format: (Double) -> String,
) {
    if (values.isEmpty()) {
        EmptyChart(modifier, height)
        return
    }
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val valueStyle = TextStyle(fontSize = 10.sp, color = textColor)
    val showValues = values.size <= 16

    Canvas(modifier.fillMaxWidth().height(height)) {
        val top = 16.dp.toPx()
        val bottom = 16.dp.toPx()
        val h = size.height - top - bottom
        val maxV = maxOf(values.max(), 1e-9)
        val minV = minOf(values.min(), 0.0)
        val range = maxV - minV
        val slot = size.width / values.size
        val barW = slot * 0.7f
        val baseY = top + h * (maxV / range).toFloat()
        values.forEachIndexed { i, v ->
            val bh = ((abs(v) / range).toFloat() * h).coerceAtLeast(1f)
            val x0 = i * slot + (slot - barW) / 2f
            val yTop = if (v >= 0) baseY - bh else baseY
            drawRoundRect(color, Offset(x0, yTop), Size(barW, bh), CornerRadius(3.dp.toPx()))
            val cx = i * slot + slot / 2f
            if (showValues) {
                val ty = if (v >= 0) yTop - 13.dp.toPx() else yTop + bh + 1.dp.toPx()
                label(measurer, format(v), valueStyle, cx, ty.coerceAtLeast(0f), 0)
            }
            val lbl = labels.getOrNull(i)
            if (lbl != null) label(measurer, lbl, labelStyle, cx, size.height - 14.dp.toPx(), 0)
        }
    }
}

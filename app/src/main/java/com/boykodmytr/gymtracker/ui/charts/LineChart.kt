package com.boykodmytr.gymtracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle
import kotlin.math.abs

/** One point of a time series; [x] is any monotonic position, e.g. epoch day. */
data class ChartPoint(val x: Double, val y: Double, val xLabel: String)

/**
 * Single-series line chart: 2dp line, 10% area wash, 8dp markers with a surface ring, hairline
 * grid on round ticks. Tapping or dragging moves a crosshair that snaps to the nearest point; the
 * readout above the plot shows it (the latest point by default). The screen lists the same values
 * as text below, so nothing is only reachable by touch.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    includeZero: Boolean = false,
    description: String = "",
) {
    if (points.isEmpty()) return
    val sorted = remember(points) { points.sortedBy { it.x } }
    var selected by remember(sorted) { mutableStateOf(sorted.lastIndex) }
    val colors = ChartColors.current()
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.merge(NumberTextStyle).copy(color = colors.axisText)
    val scale = remember(sorted, includeZero) { AxisScale.nice(sorted.minOf { it.y }, sorted.maxOf { it.y }, includeZero = includeZero) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val point = sorted[selected.coerceIn(sorted.indices)]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(valueFormatter(point.y), style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle))
            Text(point.xLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { if (description.isNotEmpty()) contentDescription = description }
                .pointerInput(sorted) {
                    val left = scale.ticks.maxOf { measurer.measure(formatTick(it), axisStyle).size.width } + 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    detectTapGestures { offset -> selected = nearestIndex(sorted, offset.x, left, right) }
                }
                .pointerInput(sorted) {
                    val left = scale.ticks.maxOf { measurer.measure(formatTick(it), axisStyle).size.width } + 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    detectHorizontalDragGestures { change, _ -> selected = nearestIndex(sorted, change.position.x, left, right) }
                },
        ) {
            val plot = plotArea(scale, measurer, axisStyle)
            drawGrid(scale, plot, measurer, axisStyle, colors)
            drawXLabels(sorted, plot, measurer, axisStyle)

            val offsets = sorted.map { plot.offset(it, sorted, scale) }
            if (offsets.size > 1) {
                val line = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(offsets.last().x, plot.bottom)
                    lineTo(offsets.first().x, plot.bottom)
                    close()
                }
                drawPath(area, colors.series.copy(alpha = 0.10f))
                drawPath(line, colors.series, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val sel = offsets[selected.coerceIn(offsets.indices)]
            drawLine(colors.grid, Offset(sel.x, plot.top), Offset(sel.x, plot.bottom), strokeWidth = 1.dp.toPx())

            val showAllMarkers = offsets.size <= MAX_MARKERS
            offsets.forEachIndexed { index, o ->
                if (showAllMarkers || index == offsets.lastIndex || index == selected) {
                    drawMarker(o, colors, emphasized = index == selected)
                }
            }
        }
    }
}

private const val MAX_MARKERS = 24

internal data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun offset(point: ChartPoint, all: List<ChartPoint>, scale: AxisScale): Offset {
        val minX = all.first().x
        val maxX = all.last().x
        val fx = if (maxX > minX) ((point.x - minX) / (maxX - minX)).toFloat() else 0.5f
        return Offset(left + fx * width, bottom - scale.fraction(point.y) * height)
    }
}

internal fun DrawScope.plotArea(scale: AxisScale, measurer: TextMeasurer, style: TextStyle): PlotArea {
    val labelWidth = scale.ticks.maxOf { measurer.measure(formatTick(it), style).size.width }
    val labelHeight = measurer.measure("0", style).size.height
    val gap = 8.dp.toPx()
    return PlotArea(
        left = labelWidth + gap,
        top = labelHeight / 2f,
        right = size.width - 8.dp.toPx(),
        bottom = size.height - labelHeight - gap,
    )
}

private fun nearestIndex(points: List<ChartPoint>, x: Float, left: Float, right: Float): Int {
    val minX = points.first().x
    val maxX = points.last().x
    return points.indices.minByOrNull { i ->
        val fx = if (maxX > minX) ((points[i].x - minX) / (maxX - minX)).toFloat() else 0.5f
        abs(left + fx * (right - left) - x)
    } ?: points.lastIndex
}

internal fun formatTick(value: Double): String =
    if (abs(value - Math.round(value)) < 1e-6) Math.round(value).toString() else "%.1f".format(value).replace('.', ',')

internal fun DrawScope.drawGrid(scale: AxisScale, plot: PlotArea, measurer: TextMeasurer, style: TextStyle, colors: ChartColors) {
    scale.ticks.forEach { tick ->
        val y = plot.bottom - scale.fraction(tick) * plot.height
        drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val layout = measurer.measure(formatTick(tick), style)
        drawText(layout, topLeft = Offset(plot.left - 8.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
    }
}

/** First, middle and last date only: enough to orient without colliding labels. */
private fun DrawScope.drawXLabels(points: List<ChartPoint>, plot: PlotArea, measurer: TextMeasurer, style: TextStyle) {
    val indices = when (points.size) {
        1 -> listOf(0)
        2 -> listOf(0, 1)
        else -> listOf(0, points.size / 2, points.lastIndex)
    }.distinct()
    val minX = points.first().x
    val maxX = points.last().x
    var lastRight = Float.NEGATIVE_INFINITY
    indices.forEach { i ->
        val layout = measurer.measure(points[i].xLabel, style)
        val fx = if (maxX > minX) ((points[i].x - minX) / (maxX - minX)).toFloat() else 0.5f
        val center = plot.left + fx * plot.width
        val x = (center - layout.size.width / 2f).coerceIn(plot.left - 4.dp.toPx(), size.width - layout.size.width)
        if (x > lastRight + 8.dp.toPx()) {
            drawText(layout, topLeft = Offset(x, plot.bottom + 6.dp.toPx()))
            lastRight = x + layout.size.width
        }
    }
}

private fun DrawScope.drawMarker(center: Offset, colors: ChartColors, emphasized: Boolean) {
    val radius = (if (emphasized) 5.dp else 4.dp).toPx()
    drawCircle(colors.surface, radius + 2.dp.toPx(), center)
    drawCircle(colors.series, radius, center)
}

internal data class ChartColors(val series: Color, val grid: Color, val axisText: Color, val surface: Color, val muted: Color) {
    companion object {
        @Composable
        fun current(surface: Color = MaterialTheme.colorScheme.surfaceContainerLow) = ChartColors(
            series = MaterialTheme.colorScheme.primary,
            grid = MaterialTheme.colorScheme.outlineVariant,
            axisText = MaterialTheme.colorScheme.onSurfaceVariant,
            surface = surface,
            muted = MaterialTheme.colorScheme.outline,
        )
    }
}

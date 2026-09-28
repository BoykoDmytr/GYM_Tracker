package com.boykodmytr.gymtracker.ui.charts

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle

data class BarDatum(val label: String, val value: Double, val detail: String)

/**
 * Single-series column chart: columns capped at 24dp, 4dp rounded tops, square at the baseline.
 * An optional [target] is drawn as a labelled hairline. Tapping a column shows its value above.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    target: Double? = null,
    targetLabel: String = "",
    description: String = "",
) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableStateOf(bars.lastIndex) }
    val colors = ChartColors.current()
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.merge(NumberTextStyle).copy(color = colors.axisText)
    val scale = remember(bars, target) {
        AxisScale.nice(0.0, maxOf(bars.maxOf { it.value }, target ?: 0.0, 1.0), targetTicks = 4, includeZero = true)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val bar = bars[selected.coerceIn(bars.indices)]
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(valueFormatter(bar.value), style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle))
            Text(bar.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { if (description.isNotEmpty()) contentDescription = description }
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val labelWidth = scale.ticks.maxOf { measurer.measure(formatTick(it), axisStyle).size.width }
                        val left = labelWidth + 8.dp.toPx()
                        val slot = (size.width - 8.dp.toPx() - left) / bars.size
                        selected = ((offset.x - left) / slot).toInt().coerceIn(bars.indices)
                    }
                },
        ) {
            val plot = plotArea(scale, measurer, axisStyle)
            drawGrid(scale, plot, measurer, axisStyle, colors)

            val slot = plot.width / bars.size
            val barWidth = minOf(24.dp.toPx(), slot * 0.6f)
            val radius = 4.dp.toPx()
            bars.forEachIndexed { index, datum ->
                val centerX = plot.left + slot * (index + 0.5f)
                val top = plot.bottom - scale.fraction(datum.value) * plot.height
                if (datum.value > 0) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = centerX - barWidth / 2,
                                top = top,
                                right = centerX + barWidth / 2,
                                bottom = plot.bottom,
                                topLeftCornerRadius = CornerRadius(radius),
                                topRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, if (index == selected) colors.series else colors.series.copy(alpha = 0.55f))
                }
                // Label every third column (and the last) so dates never collide.
                if (index % 3 == bars.lastIndex % 3) {
                    val layout = measurer.measure(datum.label, axisStyle)
                    val x = (centerX - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(x, plot.bottom + 6.dp.toPx()))
                }
            }

            if (target != null && target > 0) {
                val y = plot.bottom - scale.fraction(target) * plot.height
                drawLine(colors.axisText, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
                if (targetLabel.isNotEmpty()) {
                    val layout = measurer.measure(targetLabel, axisStyle)
                    drawRect(colors.surface, Offset(plot.right - layout.size.width - 4.dp.toPx(), y - layout.size.height - 2.dp.toPx()), Size(layout.size.width + 4.dp.toPx(), layout.size.height.toFloat()))
                    drawText(layout, topLeft = Offset(plot.right - layout.size.width - 2.dp.toPx(), y - layout.size.height - 2.dp.toPx()))
                }
            }
        }
    }
}

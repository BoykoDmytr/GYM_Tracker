package com.boykodmytr.gymtracker.ui.charts

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Y-axis range with round tick values (0 / 5 / 10 …) so the axis carries readable numbers. */
data class AxisScale(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = (max - min).takeIf { it > 0 } ?: 1.0

    fun fraction(value: Double): Float = ((value - min) / span).toFloat()

    companion object {
        fun nice(dataMin: Double, dataMax: Double, targetTicks: Int = 4, includeZero: Boolean = false): AxisScale {
            var lo = if (includeZero) minOf(0.0, dataMin) else dataMin
            var hi = dataMax
            if (abs(hi - lo) < 1e-9) {
                val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
                lo -= pad
                hi += pad
                if (includeZero || dataMin >= 0) lo = maxOf(lo, 0.0)
            }
            val step = niceStep((hi - lo) / targetTicks)
            val niceMin = floor(lo / step) * step
            val niceMax = ceil(hi / step) * step
            val ticks = generateSequence(niceMin) { it + step }.takeWhile { it <= niceMax + step / 2 }.toList()
            return AxisScale(niceMin, niceMax, ticks)
        }

        private fun niceStep(rough: Double): Double {
            if (rough <= 0) return 1.0
            val exponent = floor(log10(rough))
            val base = 10.0.pow(exponent)
            val fraction = rough / base
            val nice = when {
                fraction <= 1.0 -> 1.0
                fraction <= 2.0 -> 2.0
                fraction <= 2.5 -> 2.5
                fraction <= 5.0 -> 5.0
                else -> 10.0
            }
            return nice * base
        }
    }
}

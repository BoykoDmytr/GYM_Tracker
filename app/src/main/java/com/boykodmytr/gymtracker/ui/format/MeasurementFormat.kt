package com.boykodmytr.gymtracker.ui.format

import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.WeightUnit

/** Body weight follows the kg/lb setting; girths are always centimetres. */
object MeasurementFormat {
    fun unitLabel(unit: MeasurementUnit, weightUnit: WeightUnit): String = when (unit) {
        MeasurementUnit.KG -> Fmt.weightUnitLabel(weightUnit)
        MeasurementUnit.CM -> "см"
        MeasurementUnit.PERCENT -> "%"
    }

    fun toDisplay(value: Double, unit: MeasurementUnit, weightUnit: WeightUnit): Double =
        if (unit == MeasurementUnit.KG) weightUnit.fromKg(value) else value

    fun fromDisplay(value: Double, unit: MeasurementUnit, weightUnit: WeightUnit): Double =
        if (unit == MeasurementUnit.KG) weightUnit.toKg(value) else value

    fun value(value: Double, unit: MeasurementUnit, weightUnit: WeightUnit): String =
        "${Fmt.oneDecimal(toDisplay(value, unit, weightUnit))} ${unitLabel(unit, weightUnit)}"

    /** Signed change: "−0,6", "+1,2", "0". */
    fun delta(delta: Double, unit: MeasurementUnit, weightUnit: WeightUnit): String {
        val d = toDisplay(delta, unit, weightUnit)
        val text = Fmt.oneDecimal(kotlin.math.abs(d))
        return when {
            text == "0" -> "0"
            d > 0 -> "+$text"
            else -> "−$text"
        }
    }
}

package com.boykodmytr.gymtracker.ui.format

import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WeightUnit

/** Compact list of sets: "60×8, 60×8, 57,5×7 кг"; bodyweight sets show reps only. */
fun setsSummary(sets: List<SetLog>, unit: WeightUnit): String {
    if (sets.isEmpty()) return ""
    val weighted = sets.any { it.weightKg > 0.0 }
    val body = sets.joinToString(", ") { set ->
        if (weighted) "${Fmt.weight(set.weightKg, unit)}×${set.reps}" else "${set.reps}"
    }
    return if (weighted) "$body ${Fmt.weightUnitLabel(unit)}" else "$body повт."
}

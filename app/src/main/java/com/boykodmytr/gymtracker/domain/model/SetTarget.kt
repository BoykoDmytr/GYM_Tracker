package com.boykodmytr.gymtracker.domain.model

/**
 * Planned volume for one exercise. Ranges come straight from the program table, e.g. "2–3×10–15" is
 * setsMin=2, setsMax=3, repsMin=10, repsMax=15. A fixed prescription like "3×8" has min == max.
 */
data class SetTarget(
    val setsMin: Int,
    val setsMax: Int,
    val repsMin: Int,
    val repsMax: Int,
    val weightKg: Double? = null,
) {
    val isValid: Boolean
        get() = setsMin in 1..setsMax && repsMin in 1..repsMax && (weightKg == null || weightKg >= 0.0)
}

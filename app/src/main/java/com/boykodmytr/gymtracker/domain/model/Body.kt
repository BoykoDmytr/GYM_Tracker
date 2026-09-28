package com.boykodmytr.gymtracker.domain.model

import java.time.LocalDate

enum class MeasurementUnit { KG, CM, PERCENT }

data class MeasurementType(
    val id: String,
    val name: String,
    val unit: MeasurementUnit,
    val isBuiltIn: Boolean,
    val orderIndex: Int,
)

data class Measurement(
    val id: String,
    val typeId: String,
    /** Stored in the canonical unit of the type: kg for body weight, cm for girths. */
    val value: Double,
    val date: LocalDate,
    val note: String,
)

data class UserProfile(
    val name: String = "",
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
)

object BuiltInMeasurementTypes {
    const val WEIGHT = "weight"
}

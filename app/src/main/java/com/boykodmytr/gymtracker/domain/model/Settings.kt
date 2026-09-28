package com.boykodmytr.gymtracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class WeightUnit(val kgFactor: Double, val step: Double) {
    KG(1.0, 2.5),
    LB(0.45359237, 5.0);

    fun fromKg(kg: Double): Double = kg / kgFactor
    fun toKg(value: Double): Double = value * kgFactor
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class TrainingDay(
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
)

data class AppSettings(
    val trainingDays: List<TrainingDay> = DEFAULT_TRAINING_DAYS,
    val remindersEnabled: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val autoStartRest: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val timerSound: Boolean = true,
    val timerVibration: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** First day the app was used; missed workouts are not counted before it. */
    val trackingStartDate: LocalDate? = null,
) {
    val trainingDaysPerWeek: Int get() = trainingDays.size

    companion object {
        val REST_PRESETS_SECONDS = listOf(30, 60, 90, 120, 180)
        val DEFAULT_TRAINING_DAYS = listOf(
            TrainingDay(DayOfWeek.MONDAY, LocalTime.of(18, 0)),
            TrainingDay(DayOfWeek.WEDNESDAY, LocalTime.of(18, 0)),
            TrainingDay(DayOfWeek.FRIDAY, LocalTime.of(18, 0)),
        )
    }
}

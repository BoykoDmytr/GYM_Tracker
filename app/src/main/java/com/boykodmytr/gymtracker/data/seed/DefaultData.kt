package com.boykodmytr.gymtracker.data.seed

import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit

data class SeedExercise(
    val name: String,
    val setsMin: Int,
    val setsMax: Int,
    val repsMin: Int,
    val repsMax: Int,
)

data class SeedWorkout(val name: String, val exercises: List<SeedExercise>)

data class SeedProgram(val name: String, val description: String, val workouts: List<SeedWorkout>)

data class SeedMeasurementType(val id: String, val name: String, val unit: MeasurementUnit)

/**
 * The user's program, transcribed 1:1 from the provided tables. Exercises with identical names are
 * stored once and shared between workouts, so progress for e.g. "Жим штанги лежачи" is tracked
 * across A and C. No weights or rest times are invented: target weight is empty and rest falls back
 * to the default from settings.
 */
object DefaultData {

    val fullBody = SeedProgram(
        name = "Full Body — 3 рази на тиждень",
        description = "Три тренування на все тіло (A, B, C), які чергуються по черзі у дні тренувань.",
        workouts = listOf(
            SeedWorkout(
                "Full Body A",
                listOf(
                    SeedExercise("Жим штанги лежачи", 3, 3, 6, 8),
                    SeedExercise("Румунська тяга зі штангою", 3, 3, 8, 10),
                    SeedExercise("Тяга гантелі однією рукою", 3, 3, 8, 12),
                    SeedExercise("Жим гантелей сидячи", 3, 3, 8, 10),
                    SeedExercise("Тяга верхнього блока до грудей", 3, 3, 8, 12),
                    SeedExercise("Згинання рук з EZ-штангою", 2, 2, 10, 12),
                    SeedExercise("Прес", 2, 3, 10, 15),
                ),
            ),
            SeedWorkout(
                "Full Body B",
                listOf(
                    SeedExercise("Жим гантелей лежачи", 3, 3, 8, 12),
                    SeedExercise("Присідання до лавки / боксу", 3, 3, 8, 10),
                    SeedExercise("Тяга штанги в нахилі", 3, 3, 8, 10),
                    SeedExercise("Румунська тяга з гантелями/штангою", 3, 3, 8, 12),
                    SeedExercise("Верхній блок вузьким/нейтральним хватом", 3, 3, 10, 12),
                    SeedExercise("Розгинання рук на верхньому блоці", 2, 3, 10, 15),
                    SeedExercise("Підйоми на носки стоячи", 3, 3, 12, 20),
                ),
            ),
            SeedWorkout(
                "Full Body C",
                listOf(
                    SeedExercise("Жим штанги лежачи", 3, 3, 8, 10),
                    SeedExercise("Румунська тяга з гантелями/штангою", 3, 3, 8, 12),
                    SeedExercise("Підтягування / верхній блок", 3, 3, 8, 12),
                    SeedExercise("Жим гантелей сидячи", 3, 3, 8, 12),
                    SeedExercise("Тяга гантелей/штанги", 3, 3, 10, 12),
                    SeedExercise("Молоткові згинання", 2, 2, 10, 15),
                    SeedExercise("Розгинання рук на блоці", 2, 2, 10, 15),
                ),
            ),
        ),
    )

    val measurementTypes = listOf(
        SeedMeasurementType(BuiltInMeasurementTypes.WEIGHT, "Вага", MeasurementUnit.KG),
        SeedMeasurementType("chest", "Обхват грудей", MeasurementUnit.CM),
        SeedMeasurementType("waist", "Обхват талії", MeasurementUnit.CM),
        SeedMeasurementType("hips", "Обхват стегон (таз)", MeasurementUnit.CM),
        SeedMeasurementType("biceps", "Обхват біцепса", MeasurementUnit.CM),
        SeedMeasurementType("forearm", "Обхват передпліччя", MeasurementUnit.CM),
        SeedMeasurementType("thigh", "Обхват стегна", MeasurementUnit.CM),
        SeedMeasurementType("calf", "Обхват гомілки", MeasurementUnit.CM),
        SeedMeasurementType("neck", "Обхват шиї", MeasurementUnit.CM),
        SeedMeasurementType("shoulders", "Обхват плечей", MeasurementUnit.CM),
        SeedMeasurementType("body_fat", "Відсоток жиру", MeasurementUnit.PERCENT),
    )
}

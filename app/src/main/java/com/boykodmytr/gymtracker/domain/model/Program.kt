package com.boykodmytr.gymtracker.domain.model

import java.time.LocalDate

data class Program(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean,
    val startedOn: LocalDate?,
    val workouts: List<WorkoutTemplate>,
)

/** One workout day of a program, e.g. "Full Body A". */
data class WorkoutTemplate(
    val id: String,
    val programId: String,
    val name: String,
    val orderIndex: Int,
    val exercises: List<TemplateExercise>,
)

data class TemplateExercise(
    val id: String,
    val templateId: String,
    val exercise: Exercise,
    val orderIndex: Int,
    val target: SetTarget,
    /** Rest override for this exercise; null means "use the default from settings". */
    val restSeconds: Int?,
    val notes: String,
    /** Neighbouring exercises with the same id are done as a superset; null = on its own. */
    val supersetId: String? = null,
)

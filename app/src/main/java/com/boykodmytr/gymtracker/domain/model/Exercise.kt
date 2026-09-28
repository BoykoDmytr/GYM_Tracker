package com.boykodmytr.gymtracker.domain.model

/** An exercise from the user's library. Shared between programs and history, so progress is tracked per exercise. */
data class Exercise(
    val id: String,
    val name: String,
    val notes: String = "",
)

data class ExerciseImage(
    val id: String,
    val exerciseId: String,
    /** Absolute path of the stored copy inside app storage. */
    val path: String,
    val position: Int,
)

data class ExerciseDetails(
    val exercise: Exercise,
    val images: List<ExerciseImage>,
)

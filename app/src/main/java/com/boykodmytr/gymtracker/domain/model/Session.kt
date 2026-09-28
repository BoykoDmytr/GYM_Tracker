package com.boykodmytr.gymtracker.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class SessionStatus { IN_PROGRESS, COMPLETED }

enum class ExerciseStatus { PENDING, COMPLETED, SKIPPED }

data class WorkoutSession(
    val id: String,
    val programId: String?,
    val templateId: String?,
    val name: String,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: SessionStatus,
    val notes: String,
    val currentExerciseId: String?,
    val rest: RestState?,
    val exercises: List<SessionExercise>,
) {
    val duration: Duration? get() = endedAt?.let { Duration.between(startedAt, it) }
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val totalVolumeKg: Double get() = exercises.sumOf { ex -> ex.sets.sumOf { it.volumeKg } }

    /** Time of the last logged set, used to close sessions the user forgot to finish. */
    val lastActivityAt: Instant
        get() = exercises.flatMap { it.sets }.maxOfOrNull { it.completedAt } ?: startedAt
}

data class RestState(
    val startedAt: Instant,
    val endsAt: Instant,
) {
    val totalSeconds: Long get() = Duration.between(startedAt, endsAt).seconds.coerceAtLeast(0)
}

/**
 * Snapshot of a template exercise taken when the session started. Editing the program later
 * never rewrites history.
 */
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val target: SetTarget,
    val restSeconds: Int?,
    val status: ExerciseStatus,
    val notes: String,
    val sets: List<SetLog>,
)

data class SetLog(
    val id: String,
    val sessionExerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isFailure: Boolean,
    val note: String?,
    val completedAt: Instant,
) {
    val volumeKg: Double get() = weightKg * reps
}

/** What the user enters after a set. */
data class SetInput(
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isFailure: Boolean = false,
    val note: String? = null,
)

/** Lightweight row for lists, calendar and statistics. */
data class SessionSummary(
    val id: String,
    val name: String,
    val date: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: SessionStatus,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double,
) {
    val duration: Duration? get() = endedAt?.let { Duration.between(startedAt, it) }
}

/** One logged set of a specific exercise together with the session it belongs to. */
data class ExerciseSetRecord(
    val sessionId: String,
    val date: LocalDate,
    val startedAt: Instant,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val isFailure: Boolean,
)

data class ExerciseHistoryEntry(
    val exerciseId: String,
    val name: String,
    val sessionCount: Int,
    val lastPerformedAt: Instant,
)

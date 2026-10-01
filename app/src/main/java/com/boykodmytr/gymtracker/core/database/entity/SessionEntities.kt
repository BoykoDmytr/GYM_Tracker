package com.boykodmytr.gymtracker.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["program_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("program_id"), Index("template_id"), Index("date"), Index("status")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "program_id") val programId: String?,
    @ColumnInfo(name = "template_id") val templateId: String?,
    /** Name snapshot, so history reads correctly after the template is renamed or deleted. */
    val name: String,
    /** Local calendar date of the start, for calendar queries. */
    val date: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
    val status: SessionStatus,
    val notes: String,
    @ColumnInfo(name = "current_exercise_id") val currentExerciseId: String?,
    @ColumnInfo(name = "rest_started_at") val restStartedAt: Instant?,
    @ColumnInfo(name = "rest_ends_at") val restEndsAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // RESTRICT: history must never lose the link to its exercise.
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "sets_min") val setsMin: Int,
    @ColumnInfo(name = "sets_max") val setsMax: Int,
    @ColumnInfo(name = "reps_min") val repsMin: Int,
    @ColumnInfo(name = "reps_max") val repsMax: Int,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int?,
    val status: ExerciseStatus,
    val notes: String,
    /** Copied from the template: neighbouring exercises with the same id form a superset. */
    @ColumnInfo(name = "superset_id") val supersetId: String? = null,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_exercise_id")],
)
data class SetLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: String,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    @ColumnInfo(name = "is_failure") val isFailure: Boolean,
    val note: String?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

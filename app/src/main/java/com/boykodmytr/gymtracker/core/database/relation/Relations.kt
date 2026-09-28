package com.boykodmytr.gymtracker.core.database.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseImageEntity
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import java.time.Instant
import java.time.LocalDate

data class ExerciseWithImages(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val images: List<ExerciseImageEntity>,
)

data class TemplateExerciseWithExercise(
    @Embedded val templateExercise: TemplateExerciseEntity,
    @Relation(parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = TemplateExerciseEntity::class, parentColumn = "id", entityColumn = "template_id")
    val exercises: List<TemplateExerciseWithExercise>,
)

data class ProgramWithTemplates(
    @Embedded val program: ProgramEntity,
    @Relation(entity = WorkoutTemplateEntity::class, parentColumn = "id", entityColumn = "program_id")
    val templates: List<TemplateWithExercises>,
)

data class SessionExerciseWithSets(
    @Embedded val exercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "session_exercise_id")
    val sets: List<SetLogEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "session_id")
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionSummaryRow(
    val id: String,
    val name: String,
    val date: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant?,
    val status: SessionStatus,
    @ColumnInfo(name = "exercise_count") val exerciseCount: Int,
    @ColumnInfo(name = "set_count") val setCount: Int,
    @ColumnInfo(name = "volume_kg") val volumeKg: Double,
)

data class ExerciseSetRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val date: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    @ColumnInfo(name = "is_failure") val isFailure: Boolean,
)

data class ExerciseHistoryRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val name: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "last_performed_at") val lastPerformedAt: Instant,
)

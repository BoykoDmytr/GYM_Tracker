package com.boykodmytr.gymtracker.data.repository

import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.UserProfileEntity
import com.boykodmytr.gymtracker.core.database.relation.ExerciseHistoryRow
import com.boykodmytr.gymtracker.core.database.relation.ExerciseSetRow
import com.boykodmytr.gymtracker.core.database.relation.ProgramWithTemplates
import com.boykodmytr.gymtracker.core.database.relation.SessionExerciseWithSets
import com.boykodmytr.gymtracker.core.database.relation.SessionSummaryRow
import com.boykodmytr.gymtracker.core.database.relation.SessionWithExercises
import com.boykodmytr.gymtracker.core.database.relation.TemplateWithExercises
import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.model.ExerciseHistoryEntry
import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.RestState
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.TemplateExercise
import com.boykodmytr.gymtracker.domain.model.UserProfile
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate

internal fun ExerciseEntity.toDomain() = Exercise(id = id, name = name, notes = notes)

internal fun TemplateWithExercises.toDomain() = WorkoutTemplate(
    id = template.id,
    programId = template.programId,
    name = template.name,
    orderIndex = template.orderIndex,
    exercises = exercises
        .sortedBy { it.templateExercise.orderIndex }
        .map { row ->
            val te = row.templateExercise
            TemplateExercise(
                id = te.id,
                templateId = te.templateId,
                exercise = row.exercise.toDomain(),
                orderIndex = te.orderIndex,
                target = SetTarget(te.setsMin, te.setsMax, te.repsMin, te.repsMax, te.targetWeightKg),
                restSeconds = te.restSeconds,
                notes = te.notes,
                supersetId = te.supersetId,
            )
        },
)

internal fun ProgramWithTemplates.toDomain() = Program(
    id = program.id,
    name = program.name,
    description = program.description,
    isActive = program.isActive,
    startedOn = program.startedOn,
    workouts = templates.sortedBy { it.template.orderIndex }.map { it.toDomain() },
)

internal fun SetLogEntity.toDomain() = SetLog(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isFailure = isFailure,
    note = note,
    completedAt = completedAt,
)

internal fun SessionExerciseWithSets.toDomain() = SessionExercise(
    id = exercise.id,
    sessionId = exercise.sessionId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.exerciseName,
    orderIndex = exercise.orderIndex,
    target = SetTarget(exercise.setsMin, exercise.setsMax, exercise.repsMin, exercise.repsMax, exercise.targetWeightKg),
    restSeconds = exercise.restSeconds,
    status = exercise.status,
    notes = exercise.notes,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
    supersetId = exercise.supersetId,
)

internal fun SessionWithExercises.toDomain(): WorkoutSession {
    val restStart = session.restStartedAt
    val restEnd = session.restEndsAt
    return WorkoutSession(
        id = session.id,
        programId = session.programId,
        templateId = session.templateId,
        name = session.name,
        date = session.date,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        status = session.status,
        notes = session.notes,
        currentExerciseId = session.currentExerciseId,
        rest = if (restStart != null && restEnd != null) RestState(restStart, restEnd) else null,
        exercises = exercises.sortedBy { it.exercise.orderIndex }.map { it.toDomain() },
    )
}

internal fun SessionSummaryRow.toDomain() = SessionSummary(
    id = id,
    name = name,
    date = date,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    exerciseCount = exerciseCount,
    setCount = setCount,
    volumeKg = volumeKg,
)

internal fun ExerciseSetRow.toDomain() = ExerciseSetRecord(
    exerciseId = exerciseId,
    sessionId = sessionId,
    date = date,
    startedAt = startedAt,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isFailure = isFailure,
)

internal fun ExerciseHistoryRow.toDomain() = ExerciseHistoryEntry(
    exerciseId = exerciseId,
    name = name,
    sessionCount = sessionCount,
    lastPerformedAt = lastPerformedAt,
)

internal fun MeasurementTypeEntity.toDomain() = MeasurementType(
    id = id,
    name = name,
    unit = unit,
    isBuiltIn = isBuiltIn,
    orderIndex = orderIndex,
)

internal fun BodyMeasurementEntity.toDomain() = Measurement(
    id = id,
    typeId = typeId,
    value = value,
    date = date,
    note = note,
)

internal fun UserProfileEntity.toDomain() = UserProfile(name = name, birthDate = birthDate, heightCm = heightCm)

package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import java.time.Instant
import java.time.LocalDate

val T0: Instant = Instant.parse("2026-09-28T15:00:00Z")

fun set(exerciseId: String, number: Int, weight: Double = 60.0, reps: Int = 8) = SetLog(
    id = "$exerciseId-set$number",
    sessionExerciseId = exerciseId,
    setNumber = number,
    weightKg = weight,
    reps = reps,
    rpe = null,
    isFailure = false,
    note = null,
    completedAt = T0.plusSeconds(number * 120L),
)

fun exercise(
    id: String,
    order: Int,
    target: SetTarget = SetTarget(3, 3, 8, 10),
    status: ExerciseStatus = ExerciseStatus.PENDING,
    sets: Int = 0,
) = SessionExercise(
    id = id,
    sessionId = "s",
    exerciseId = "ex-$id",
    exerciseName = "Exercise $id",
    orderIndex = order,
    target = target,
    restSeconds = null,
    status = status,
    notes = "",
    sets = (1..sets).map { set(id, it) },
)

fun session(exercises: List<SessionExercise>, currentId: String? = null) = WorkoutSession(
    id = "s",
    programId = "p",
    templateId = "t",
    name = "Full Body A",
    date = LocalDate.of(2026, 9, 28),
    startedAt = T0,
    endedAt = null,
    status = SessionStatus.IN_PROGRESS,
    notes = "",
    currentExerciseId = currentId,
    rest = null,
    exercises = exercises,
)

fun template(id: String, order: Int) = WorkoutTemplate(id, "p", "Workout $id", order, emptyList())

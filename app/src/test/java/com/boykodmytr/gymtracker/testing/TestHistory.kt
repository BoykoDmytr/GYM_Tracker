package com.boykodmytr.gymtracker.testing

import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Six weeks of Mon/Wed/Fri training before [today] following the A/B/C rotation, with a steadily
 * growing bench press and two missed days, so history, stats and charts have something to show.
 */
suspend fun seedHistory(db: AppDatabase, program: Program, today: LocalDate, missed: Set<LocalDate> = emptySet()) {
    val dao = db.workoutDao()
    val start = today.minusWeeks(6).with(DayOfWeek.MONDAY)
    val dates = generateSequence(start) { it.plusDays(1) }
        .takeWhile { it.isBefore(today) }
        .filter { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY) && it !in missed }
        .toList()
    dates.forEachIndexed { index, date ->
        val template = program.workouts[index % program.workouts.size]
        val startedAt = date.atTime(LocalTime.of(18, 5)).atZone(TEST_ZONE).toInstant()
        val sessionId = newId()
        dao.insertSession(
            WorkoutSessionEntity(
                id = sessionId, programId = program.id, templateId = template.id, name = template.name, date = date,
                startedAt = startedAt, endedAt = startedAt.plusSeconds(60L * (55 + index % 4 * 5)), status = SessionStatus.COMPLETED,
                notes = "", currentExerciseId = null, restStartedAt = null, restEndsAt = null, createdAt = startedAt, updatedAt = startedAt,
            ),
        )
        template.exercises.forEachIndexed { order, te ->
            val seId = newId()
            dao.insertSessionExercises(
                listOf(
                    SessionExerciseEntity(
                        seId, sessionId, te.exercise.id, te.exercise.name, order, te.target.setsMin, te.target.setsMax,
                        te.target.repsMin, te.target.repsMax, null, null, ExerciseStatus.COMPLETED, "",
                    ),
                ),
            )
            val isBench = te.exercise.name == "Жим штанги лежачи"
            val weight = when {
                isBench -> 50.0 + index * 1.25
                te.exercise.name == "Прес" -> 0.0
                else -> 20.0 + order * 5 + index
            }
            repeat(te.target.setsMax) { n ->
                dao.insertSet(
                    SetLogEntity(
                        newId(), seId, n + 1, weight, te.target.repsMax - n, null, false, null,
                        startedAt.plusSeconds(120L * (order * 3 + n + 1)), startedAt,
                    ),
                )
            }
        }
    }
}

package com.boykodmytr.gymtracker.data.seed

import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts the built-in program and measurement types once. Guarded by a version flag instead of
 * "table is empty", so a program the user deliberately deleted does not come back.
 */
@Singleton
class DataSeeder @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun seedIfNeeded() {
        settings.ensureTrackingStartDate(LocalDate.now(clock))
        if (settings.seedVersion() >= SEED_VERSION) return
        db.withTransaction {
            insertMeasurementTypes()
            if (db.programDao().countPrograms() == 0) insertProgram(DefaultData.fullBody)
        }
        settings.setSeedVersion(SEED_VERSION)
    }

    private suspend fun insertMeasurementTypes() {
        val now = clock.instant()
        db.bodyDao().insertTypes(
            DefaultData.measurementTypes.mapIndexed { index, type ->
                MeasurementTypeEntity(type.id, type.name, type.unit, isBuiltIn = true, orderIndex = index, now, now)
            },
        )
    }

    private suspend fun insertProgram(seed: SeedProgram) {
        val now = clock.instant()
        val exerciseDao = db.exerciseDao()
        val programDao = db.programDao()
        val exerciseIds = mutableMapOf<String, String>()
        for (name in seed.workouts.flatMap { w -> w.exercises.map { it.name } }.distinct()) {
            val existing = exerciseDao.findByName(name)
            exerciseIds[name] = existing?.id ?: newId().also { exerciseDao.insert(ExerciseEntity(it, name, "", now, now)) }
        }

        val programId = newId()
        programDao.insertProgram(
            ProgramEntity(programId, seed.name, seed.description, isActive = true, LocalDate.now(clock), now, now),
        )
        seed.workouts.forEachIndexed { workoutIndex, workout ->
            val templateId = newId()
            programDao.insertTemplate(WorkoutTemplateEntity(templateId, programId, workout.name, workoutIndex, now, now))
            programDao.insertTemplateExercises(
                workout.exercises.mapIndexed { index, e ->
                    TemplateExerciseEntity(
                        id = newId(),
                        templateId = templateId,
                        exerciseId = exerciseIds.getValue(e.name),
                        orderIndex = index,
                        setsMin = e.setsMin,
                        setsMax = e.setsMax,
                        repsMin = e.repsMin,
                        repsMax = e.repsMax,
                        targetWeightKg = null,
                        restSeconds = null,
                        notes = "",
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
    }

    companion object {
        const val SEED_VERSION = 1
    }
}

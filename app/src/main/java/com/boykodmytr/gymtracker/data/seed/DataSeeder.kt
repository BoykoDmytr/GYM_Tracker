package com.boykodmytr.gymtracker.data.seed

import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity
import com.boykodmytr.gymtracker.domain.logic.Supersets
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.transfer.ParsedProgram
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The program a fresh install starts with, as its active program. Tests swap in a stable fixture. */
data class StarterProgram(val program: ParsedProgram)

/**
 * Inserts the starter program and measurement types once. Guarded by a version flag instead of
 * "table is empty", so a program the user deliberately deleted does not come back. An existing
 * install never gets a program pushed into it: it adds a built-in one from the Programs screen.
 */
@Singleton
class DataSeeder @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val starter: StarterProgram,
    private val clock: Clock,
) {
    suspend fun seedIfNeeded() {
        settings.ensureTrackingStartDate(LocalDate.now(clock))
        if (settings.seedVersion() >= SEED_VERSION) return
        db.withTransaction {
            insertMeasurementTypes()
            if (db.programDao().countPrograms() == 0) insertProgram(starter.program)
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

    private suspend fun insertProgram(program: ParsedProgram) {
        val now = clock.instant()
        val exerciseDao = db.exerciseDao()
        val programDao = db.programDao()
        val exerciseIds = mutableMapOf<String, String>()
        for (name in program.workouts.flatMap { w -> w.exercises.map { it.name } }.distinct()) {
            val existing = exerciseDao.findByName(name)
            exerciseIds[name] = existing?.id ?: newId().also { exerciseDao.insert(ExerciseEntity(it, name, "", now, now)) }
        }

        val programId = newId()
        programDao.insertProgram(
            ProgramEntity(programId, program.name, program.description, isActive = true, LocalDate.now(clock), now, now),
        )
        program.workouts.forEachIndexed { workoutIndex, workout ->
            val templateId = newId()
            programDao.insertTemplate(WorkoutTemplateEntity(templateId, programId, workout.name, workoutIndex, now, now))
            val supersets = Supersets.fromLabels(workout.exercises.map { it.superset }, ::newId)
            programDao.insertTemplateExercises(
                workout.exercises.mapIndexed { index, e ->
                    TemplateExerciseEntity(
                        id = newId(),
                        templateId = templateId,
                        exerciseId = exerciseIds.getValue(e.name),
                        orderIndex = index,
                        setsMin = e.target.setsMin,
                        setsMax = e.target.setsMax,
                        repsMin = e.target.repsMin,
                        repsMax = e.target.repsMax,
                        targetWeightKg = e.target.weightKg,
                        restSeconds = e.restSeconds,
                        notes = e.note,
                        createdAt = now,
                        updatedAt = now,
                        supersetId = supersets[index],
                    )
                },
            )
        }
    }

    companion object {
        const val SEED_VERSION = 1
    }
}

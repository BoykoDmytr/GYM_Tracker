package com.boykodmytr.gymtracker.data.repository

import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.dao.ProgramDao
import com.boykodmytr.gymtracker.core.database.dao.WorkoutDao
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.domain.model.ExerciseHistoryEntry
import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val clock: Clock,
) : WorkoutRepository {

    override fun observeSession(id: String): Flow<WorkoutSession?> =
        workoutDao.observeSession(id).map { it?.toDomain() }

    override fun observeInProgressSession(): Flow<WorkoutSession?> =
        workoutDao.observeInProgressSession().map { it?.toDomain() }

    override fun observeCompletedSummaries(from: LocalDate, to: LocalDate): Flow<List<SessionSummary>> =
        workoutDao.observeCompletedSummaries(from, to).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllCompletedSummaries(): Flow<List<SessionSummary>> =
        observeCompletedSummaries(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31))

    override fun observeLastCompletedTemplateId(programId: String): Flow<String?> =
        workoutDao.observeLastCompletedTemplateId(programId)

    override suspend fun startSession(templateId: String): String = db.withTransaction {
        workoutDao.getInProgressSessionId()?.let { return@withTransaction it }
        val template = requireNotNull(programDao.getTemplateWithExercises(templateId)) { "Template $templateId not found" }
        val now = clock.instant()
        val sessionId = newId()
        val exercises = template.exercises
            .sortedBy { it.templateExercise.orderIndex }
            .mapIndexed { index, row ->
                val te = row.templateExercise
                SessionExerciseEntity(
                    id = newId(),
                    sessionId = sessionId,
                    exerciseId = row.exercise.id,
                    exerciseName = row.exercise.name,
                    orderIndex = index,
                    setsMin = te.setsMin,
                    setsMax = te.setsMax,
                    repsMin = te.repsMin,
                    repsMax = te.repsMax,
                    targetWeightKg = te.targetWeightKg,
                    restSeconds = te.restSeconds,
                    status = ExerciseStatus.PENDING,
                    notes = te.notes,
                )
            }
        workoutDao.insertSession(
            WorkoutSessionEntity(
                id = sessionId,
                programId = template.template.programId,
                templateId = template.template.id,
                name = template.template.name,
                date = now.atZone(clock.zone).toLocalDate(),
                startedAt = now,
                endedAt = null,
                status = SessionStatus.IN_PROGRESS,
                notes = "",
                currentExerciseId = exercises.firstOrNull()?.id,
                restStartedAt = null,
                restEndsAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        workoutDao.insertSessionExercises(exercises)
        sessionId
    }

    override suspend fun finishSession(sessionId: String, endedAt: Instant) = db.withTransaction {
        val session = workoutDao.getSession(sessionId) ?: return@withTransaction
        workoutDao.getSessionExercises(sessionId)
            .filter { it.status == ExerciseStatus.PENDING }
            .forEach { exercise ->
                val hasSets = workoutDao.countSets(exercise.id) > 0
                workoutDao.setExerciseStatus(exercise.id, if (hasSets) ExerciseStatus.COMPLETED else ExerciseStatus.SKIPPED)
            }
        workoutDao.updateSession(
            session.copy(
                status = SessionStatus.COMPLETED,
                endedAt = maxOf(endedAt, session.startedAt),
                restStartedAt = null,
                restEndsAt = null,
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun deleteSession(sessionId: String) = workoutDao.deleteSession(sessionId)

    override suspend fun updateSessionNotes(sessionId: String, notes: String) =
        workoutDao.setNotes(sessionId, notes.trim(), clock.instant())

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = db.withTransaction {
        val id = newId()
        val now = clock.instant()
        workoutDao.insertSet(
            SetLogEntity(
                id = id,
                sessionExerciseId = sessionExerciseId,
                setNumber = workoutDao.countSets(sessionExerciseId) + 1,
                weightKg = input.weightKg,
                reps = input.reps,
                rpe = input.rpe,
                isFailure = input.isFailure,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                completedAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    override suspend fun updateSet(setId: String, input: SetInput) {
        val existing = workoutDao.getSet(setId) ?: return
        workoutDao.updateSet(
            existing.copy(
                weightKg = input.weightKg,
                reps = input.reps,
                rpe = input.rpe,
                isFailure = input.isFailure,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun deleteSet(setId: String) = db.withTransaction {
        val existing = workoutDao.getSet(setId) ?: return@withTransaction
        workoutDao.deleteSet(setId)
        workoutDao.getSets(existing.sessionExerciseId).forEachIndexed { index, set ->
            if (set.setNumber != index + 1) workoutDao.setSetNumber(set.id, index + 1)
        }
    }

    override suspend fun setCurrentExercise(sessionId: String, sessionExerciseId: String) =
        workoutDao.setCurrentExercise(sessionId, sessionExerciseId, clock.instant())

    override suspend fun setExerciseStatus(sessionExerciseId: String, status: ExerciseStatus) =
        workoutDao.setExerciseStatus(sessionExerciseId, status)

    override suspend fun startRest(sessionId: String, startedAt: Instant, endsAt: Instant) =
        workoutDao.setRest(sessionId, startedAt, endsAt, clock.instant())

    override suspend fun clearRest(sessionId: String) =
        workoutDao.setRest(sessionId, null, null, clock.instant())

    override suspend fun lastPerformance(exerciseId: String, excludeSessionId: String?): List<SetLog> {
        val sessionExerciseId = workoutDao.lastSessionExerciseId(exerciseId, excludeSessionId) ?: return emptyList()
        return workoutDao.getSets(sessionExerciseId).map { it.toDomain() }
    }

    override fun observeExerciseRecords(exerciseId: String): Flow<List<ExerciseSetRecord>> =
        workoutDao.observeExerciseSets(exerciseId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllExerciseRecords(): Flow<List<ExerciseSetRecord>> =
        workoutDao.observeAllExerciseSets().map { rows -> rows.map { it.toDomain() } }

    override fun observeExercisesWithHistory(): Flow<List<ExerciseHistoryEntry>> =
        workoutDao.observeExercisesWithHistory().map { rows -> rows.map { it.toDomain() } }
}

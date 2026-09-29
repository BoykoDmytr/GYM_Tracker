package com.boykodmytr.gymtracker.data.transfer

import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.data.repository.toDomain
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.repository.DataTransferRepository
import com.boykodmytr.gymtracker.domain.repository.ExportResult
import com.boykodmytr.gymtracker.domain.repository.ImportContext
import com.boykodmytr.gymtracker.domain.repository.MergePreview
import com.boykodmytr.gymtracker.domain.repository.TransferEntry
import com.boykodmytr.gymtracker.domain.repository.TransferKind
import com.boykodmytr.gymtracker.domain.repository.UndoResult
import com.boykodmytr.gymtracker.domain.transfer.BodyImportPlan
import com.boykodmytr.gymtracker.domain.transfer.CsvDialect
import com.boykodmytr.gymtracker.domain.transfer.CsvExport
import com.boykodmytr.gymtracker.domain.transfer.ExerciseTarget
import com.boykodmytr.gymtracker.domain.transfer.ExistingExercise
import com.boykodmytr.gymtracker.domain.transfer.ExistingMeasurement
import com.boykodmytr.gymtracker.domain.transfer.ExistingSession
import com.boykodmytr.gymtracker.domain.transfer.ExistingSet
import com.boykodmytr.gymtracker.domain.transfer.HeaderText
import com.boykodmytr.gymtracker.domain.transfer.ImportPlan
import com.boykodmytr.gymtracker.domain.transfer.MeasurementTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataTransferRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val history: TransferHistoryStore,
    private val clock: Clock,
) : DataTransferRepository {

    private val dao get() = db.transferDao()

    override suspend fun exportWorkouts(dialect: CsvDialect): ExportResult {
        val sessions = dao.getCompletedSessions().map { it.toDomain() }
        val text = CsvExport.workouts(sessions, clock.zone, dialect)
        return ExportResult(text, rows = sessions.sumOf { s -> s.exercises.sumOf { it.sets.size }.coerceAtLeast(1) })
    }

    override suspend fun exportMeasurements(dialect: CsvDialect): ExportResult {
        val types = dao.getMeasurementTypes().map { it.toDomain() }
        val measurements = dao.getMeasurements().map { it.toDomain() }
        return ExportResult(CsvExport.measurements(measurements, types, dialect), rows = measurements.size)
    }

    override suspend fun importContext(): ImportContext {
        val zone = clock.zone
        val sessions = dao.getCompletedSetRows().groupBy { it.sessionId }.map { (id, rows) ->
            val first = rows.first()
            val time = first.startedAt.atZone(zone).toLocalTime()
            ExistingSession(
                id = id,
                date = first.date,
                // Imported workouts without a known time are stored at midnight.
                startTime = time.takeUnless { it == LocalTime.MIDNIGHT },
                name = first.name,
                sets = rows.mapNotNull { r ->
                    if (r.exerciseId != null && r.weightKg != null && r.reps != null) ExistingSet(r.exerciseId, r.weightKg, r.reps) else null
                },
            )
        }
        return ImportContext(
            exercises = dao.getExercises().map { ExistingExercise(it.id, it.name) },
            sessions = sessions,
            measurements = dao.getMeasurements().map { ExistingMeasurement(it.typeId, it.date, it.value) },
            measurementTypes = dao.getMeasurementTypes().map { it.toDomain() },
        )
    }

    override suspend fun applyWorkoutImport(plan: ImportPlan, sourceName: String): TransferEntry {
        val now = clock.instant()
        val zone = clock.zone
        val record = db.withTransaction {
            // Exercises: reuse by name if one appeared since the plan was made, else create.
            val all = dao.getExercises()
            val byId = all.associateBy { it.id }
            val byKey = all.associateBy { HeaderText.key(it.name) }.toMutableMap()
            val createdExercises = mutableListOf<ExerciseEntity>()
            fun exerciseFor(target: ExerciseTarget): ExerciseEntity = when (target) {
                is ExerciseTarget.Existing -> byId[target.id] ?: error("Exercise ${target.id} disappeared during import")
                is ExerciseTarget.New -> byKey.getOrPut(HeaderText.key(target.name)) {
                    ExerciseEntity(newId(), target.name.trim(), "", now, now).also { createdExercises += it }
                }
            }

            val sessions = mutableListOf<WorkoutSessionEntity>()
            val sessionExercises = mutableListOf<SessionExerciseEntity>()
            val sets = mutableListOf<SetLogEntity>()
            for (planned in plan.sessions) {
                val s = planned.session
                val startedAt = s.date.atTime(s.startTime ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
                val endedAt = s.durationMinutes?.let { startedAt.plusSeconds(it * 60) }
                val sessionId = newId()
                sessions += WorkoutSessionEntity(
                    id = sessionId,
                    programId = null,
                    templateId = null,
                    name = s.name,
                    date = s.date,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    status = SessionStatus.COMPLETED,
                    notes = s.notes,
                    currentExerciseId = null,
                    restStartedAt = null,
                    restEndsAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
                s.exercises.forEachIndexed { order, parsed ->
                    val exercise = exerciseFor(planned.targets.getValue(parsed.name))
                    val seId = newId()
                    val reps = parsed.sets.map { it.reps }
                    val plan = parsed.plan ?: intArrayOf(parsed.sets.size, parsed.sets.size, reps.min(), reps.max())
                    // Keep the spelling from the file when the exercise was renamed to its standard name.
                    val originalName = if (HeaderText.key(parsed.name) != HeaderText.key(exercise.name)) "У файлі: «${parsed.name}»" else null
                    sessionExercises += SessionExerciseEntity(
                        id = seId,
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        orderIndex = order,
                        setsMin = plan[0],
                        setsMax = plan[1],
                        repsMin = plan[2],
                        repsMax = plan[3],
                        targetWeightKg = null,
                        restSeconds = null,
                        status = ExerciseStatus.COMPLETED,
                        notes = listOfNotNull(parsed.note, originalName).joinToString("; "),
                    )
                    parsed.sets.forEach { set ->
                        sets += SetLogEntity(
                            id = newId(),
                            sessionExerciseId = seId,
                            setNumber = set.setNumber,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            rpe = set.rpe,
                            isFailure = set.isFailure,
                            note = set.note,
                            completedAt = endedAt ?: startedAt,
                            updatedAt = now,
                        )
                    }
                }
            }
            val measurements = plan.bodyWeights.map { (date, value) ->
                BodyMeasurementEntity(newId(), BuiltInMeasurementTypes.WEIGHT, value, date, "", now, now)
            }
            dao.insertExercises(createdExercises)
            dao.insertSessions(sessions)
            dao.insertSessionExercises(sessionExercises)
            dao.insertSets(sets)
            dao.insertMeasurements(measurements)
            TransferRecord(
                id = newId(),
                kind = TransferKind.WORKOUT_IMPORT.name,
                createdAt = now.toEpochMilli(),
                title = sourceName,
                sessionIds = sessions.map { it.id },
                measurementIds = measurements.map { it.id },
                exerciseIds = createdExercises.map { it.id },
                sets = sets.size,
            )
        }
        history.add(record)
        return record.toEntry()
    }

    override suspend fun applyBodyImport(plan: BodyImportPlan, sourceName: String): TransferEntry {
        val now = clock.instant()
        val record = db.withTransaction {
            var order = dao.maxMeasurementTypeOrder()
            val newTypes = plan.newTypes.associateWith { t ->
                MeasurementTypeEntity(newId(), t.name.trim(), t.unit, isBuiltIn = false, orderIndex = ++order, now, now)
            }
            val measurements = plan.measurements.map { m ->
                val typeId = when (val t = m.target) {
                    is MeasurementTarget.Existing -> t.typeId
                    is MeasurementTarget.New -> newTypes.getValue(t).id
                }
                BodyMeasurementEntity(newId(), typeId, m.value, m.date, m.note.trim(), now, now)
            }
            dao.insertMeasurementTypes(newTypes.values.toList())
            dao.insertMeasurements(measurements)
            TransferRecord(
                id = newId(),
                kind = TransferKind.BODY_IMPORT.name,
                createdAt = now.toEpochMilli(),
                title = sourceName,
                measurementIds = measurements.map { it.id },
                measurementTypeIds = newTypes.values.map { it.id },
            )
        }
        history.add(record)
        return record.toEntry()
    }

    override fun observeHistory(): Flow<List<TransferEntry>> = flow {
        emitAll(history.observe().filterNotNull().map { list -> list.map { it.toEntry() } })
    }

    override suspend fun undo(entryId: String): UndoResult {
        val record = history.all().firstOrNull { it.id == entryId && !it.undone } ?: return UndoResult(0, 0, 0)
        val result = db.withTransaction {
            val merge = record.merge
            if (merge != null) {
                undoMerge(merge)
                UndoResult(0, 0, 0)
            } else {
                val sessions = record.sessionIds.chunked(CHUNK).sumOf { dao.deleteSessions(it) }
                val measurements = record.measurementIds.chunked(CHUNK).sumOf { dao.deleteMeasurements(it) }
                // Exercises the user has since put into a program or trained again stay.
                val kept = record.exerciseIds.count { dao.deleteExerciseIfUnused(it) == 0 && db.exerciseDao().get(it) != null }
                record.measurementTypeIds.forEach { dao.deleteMeasurementTypeIfUnused(it) }
                UndoResult(sessions, measurements, kept)
            }
        }
        history.markUndone(record.id)
        return result
    }

    override suspend fun previewMerge(sourceId: String, targetId: String): MergePreview {
        val exercises = db.exerciseDao()
        val source = requireNotNull(exercises.get(sourceId)) { "Exercise $sourceId not found" }
        val target = requireNotNull(exercises.get(targetId)) { "Exercise $targetId not found" }
        return MergePreview(
            sourceName = source.name,
            targetName = target.name,
            sessions = dao.countSessions(sourceId),
            templates = dao.templateExerciseIds(sourceId).size,
            images = dao.images(sourceId).size,
            sessionsWithBoth = dao.countSessionsWithBoth(sourceId, targetId),
        )
    }

    override suspend fun mergeExercises(sourceId: String, targetId: String): TransferEntry {
        require(sourceId != targetId) { "Cannot merge an exercise into itself" }
        val now = clock.instant()
        val record = db.withTransaction {
            val exercises = db.exerciseDao()
            val source = requireNotNull(exercises.get(sourceId)) { "Exercise $sourceId not found" }
            val target = requireNotNull(exercises.get(targetId)) { "Exercise $targetId not found" }

            val movedSessions = dao.sessionExerciseNames(sourceId).map { MovedSessionExercise(it.id, it.exerciseName) }
            val sessionCount = dao.countSessions(sourceId)
            val movedTemplates = dao.templateExerciseIds(sourceId)
            val sourceImages = dao.images(sourceId)
            var position = (dao.images(targetId).maxOfOrNull { it.position } ?: -1)
            sourceImages.forEach { dao.moveImage(it.id, targetId, ++position) }
            dao.moveSessionExercises(sourceId, targetId, target.name)
            dao.moveTemplateExercises(sourceId, targetId)

            val mergedNotes = when {
                source.notes.isBlank() || target.notes.contains(source.notes.trim()) -> target.notes
                target.notes.isBlank() -> source.notes
                else -> target.notes.trimEnd() + "\n\n" + source.notes.trim()
            }
            if (mergedNotes != target.notes) dao.setExerciseNotes(targetId, mergedNotes, now)
            exercises.delete(sourceId)

            TransferRecord(
                id = newId(),
                kind = TransferKind.EXERCISE_MERGE.name,
                createdAt = now.toEpochMilli(),
                title = "${source.name} → ${target.name}",
                sessionCount = sessionCount,
                merge = MergeUndo(
                    source = StoredExercise(source.id, source.name, source.notes, source.createdAt.toEpochMilli(), source.updatedAt.toEpochMilli()),
                    targetId = targetId,
                    targetNotesBefore = target.notes,
                    targetNotesAfter = mergedNotes,
                    sessionExercises = movedSessions,
                    templateExerciseIds = movedTemplates,
                    images = sourceImages.map { MovedImage(it.id, it.position) },
                ),
            )
        }
        history.add(record)
        return record.toEntry()
    }

    private suspend fun undoMerge(merge: MergeUndo) {
        val s = merge.source
        val exercises = db.exerciseDao()
        if (exercises.get(s.id) == null) {
            exercises.insert(ExerciseEntity(s.id, s.name, s.notes, Instant.ofEpochMilli(s.createdAt), Instant.ofEpochMilli(s.updatedAt)))
        }
        // Rows only go back if they still point at the target, so later edits are never overwritten.
        merge.sessionExercises.forEach { dao.restoreSessionExercise(it.id, merge.targetId, s.id, it.oldName) }
        merge.templateExerciseIds.forEach { dao.restoreTemplateExercise(it, merge.targetId, s.id) }
        merge.images.forEach { dao.moveImage(it.id, s.id, it.oldPosition) }
        val target = exercises.get(merge.targetId)
        if (target != null && target.notes == merge.targetNotesAfter && merge.targetNotesAfter != merge.targetNotesBefore) {
            dao.setExerciseNotes(merge.targetId, merge.targetNotesBefore, clock.instant())
        }
    }

    private fun TransferRecord.toEntry() = TransferEntry(
        id = id,
        kind = TransferKind.valueOf(kind),
        createdAt = Instant.ofEpochMilli(createdAt),
        title = title,
        sessions = if (merge != null) sessionCount else sessionIds.size,
        sets = sets,
        measurements = measurementIds.size,
        exercisesCreated = exerciseIds.size,
        undone = undone,
    )

    private companion object {
        /** Stays below SQLite's 999 bound parameters on old Android versions. */
        const val CHUNK = 500
    }
}

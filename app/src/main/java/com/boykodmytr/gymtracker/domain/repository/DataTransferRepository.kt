package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.transfer.BodyImportPlan
import com.boykodmytr.gymtracker.domain.transfer.CsvDialect
import com.boykodmytr.gymtracker.domain.transfer.ExistingExercise
import com.boykodmytr.gymtracker.domain.transfer.ExistingMeasurement
import com.boykodmytr.gymtracker.domain.transfer.ExistingSession
import com.boykodmytr.gymtracker.domain.transfer.ImportPlan
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class ExportResult(val text: String, val rows: Int)

/** What is already in the app, for matching exercise names and skipping duplicates. */
data class ImportContext(
    val exercises: List<ExistingExercise>,
    val sessions: List<ExistingSession>,
    val measurements: List<ExistingMeasurement>,
    val measurementTypes: List<MeasurementType>,
)

enum class TransferKind { WORKOUT_IMPORT, BODY_IMPORT, EXERCISE_MERGE }

/** A finished import or merge that can still be undone (until it is). */
data class TransferEntry(
    val id: String,
    val kind: TransferKind,
    val createdAt: Instant,
    /** File name for imports, "source → target" for merges. */
    val title: String,
    val sessions: Int,
    val sets: Int,
    val measurements: Int,
    val exercisesCreated: Int,
    val undone: Boolean,
)

data class UndoResult(
    val sessionsRemoved: Int,
    val measurementsRemoved: Int,
    /** Created exercises that were kept because they are now used in a program or a newer workout. */
    val exercisesKept: Int,
)

data class MergePreview(
    val sourceName: String,
    val targetName: String,
    val sessions: Int,
    val templates: Int,
    val images: Int,
    /** Workouts that already contain both exercises: they will list the exercise twice. */
    val sessionsWithBoth: Int,
)

interface DataTransferRepository {
    suspend fun exportWorkouts(dialect: CsvDialect): ExportResult
    suspend fun exportMeasurements(dialect: CsvDialect): ExportResult

    suspend fun importContext(): ImportContext

    /** Writes the whole plan in one transaction: either everything is imported or nothing is. */
    suspend fun applyWorkoutImport(plan: ImportPlan, sourceName: String): TransferEntry
    suspend fun applyBodyImport(plan: BodyImportPlan, sourceName: String): TransferEntry

    fun observeHistory(): Flow<List<TransferEntry>>

    /** Removes exactly what the import added, or moves merged history back. */
    suspend fun undo(entryId: String): UndoResult

    suspend fun previewMerge(sourceId: String, targetId: String): MergePreview

    /**
     * Moves all history, program entries and photos of [sourceId] to [targetId] and deletes the
     * now-empty source. History rows take the target's name; the old names are kept for undo.
     */
    suspend fun mergeExercises(sourceId: String, targetId: String): TransferEntry
}

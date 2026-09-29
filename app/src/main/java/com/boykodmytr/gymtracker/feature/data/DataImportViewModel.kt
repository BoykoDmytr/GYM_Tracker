package com.boykodmytr.gymtracker.feature.data

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.data.transfer.DocumentFiles
import com.boykodmytr.gymtracker.data.transfer.DocumentTooLargeException
import com.boykodmytr.gymtracker.domain.repository.DataTransferRepository
import com.boykodmytr.gymtracker.domain.repository.ImportContext
import com.boykodmytr.gymtracker.domain.repository.TransferEntry
import com.boykodmytr.gymtracker.domain.repository.UndoResult
import com.boykodmytr.gymtracker.domain.transfer.BodyColumnMapping
import com.boykodmytr.gymtracker.domain.transfer.BodyImport
import com.boykodmytr.gymtracker.domain.transfer.BodyImportPlan
import com.boykodmytr.gymtracker.domain.transfer.ColumnMapping
import com.boykodmytr.gymtracker.domain.transfer.Csv
import com.boykodmytr.gymtracker.domain.transfer.ExerciseMatch
import com.boykodmytr.gymtracker.domain.transfer.ExerciseTarget
import com.boykodmytr.gymtracker.domain.transfer.ExistingExercise
import com.boykodmytr.gymtracker.domain.transfer.ImportPlan
import com.boykodmytr.gymtracker.domain.transfer.ImportPlanner
import com.boykodmytr.gymtracker.domain.transfer.MatchReason
import com.boykodmytr.gymtracker.domain.transfer.ParsedWorkoutFile
import com.boykodmytr.gymtracker.domain.transfer.WorkoutField
import com.boykodmytr.gymtracker.domain.transfer.WorkoutFileParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A workout file that is read and matched, waiting for the user to check and confirm. */
data class WorkoutReview(
    val fileName: String,
    val charset: String,
    val delimiter: Char,
    val rows: List<List<String>>,
    val mapping: ColumnMapping,
    val parsed: ParsedWorkoutFile,
    val matches: List<ExerciseMatch>,
    val plan: ImportPlan,
    val exercises: List<ExistingExercise>,
)

data class BodyReview(val fileName: String, val charset: String, val plan: BodyImportPlan)

sealed interface ImportStep {
    data object Idle : ImportStep
    data object Working : ImportStep
    data class Failed(val reason: FailureReason, val detail: String? = null) : ImportStep
    data class ReviewWorkouts(val review: WorkoutReview) : ImportStep
    data class ReviewBody(val review: BodyReview) : ImportStep
    data class Done(val entry: TransferEntry) : ImportStep
}

enum class FailureReason { UNREADABLE, TOO_LARGE, SPREADSHEET, NO_HEADER, EMPTY, IMPORT_FAILED }

sealed interface ImportEvent {
    data class Undone(val result: UndoResult) : ImportEvent
    data object UndoFailed : ImportEvent
}

@HiltViewModel
class DataImportViewModel @Inject constructor(
    private val repository: DataTransferRepository,
    private val files: DocumentFiles,
) : ViewModel() {

    private val _step = MutableStateFlow<ImportStep>(ImportStep.Idle)
    val step: StateFlow<ImportStep> = _step.asStateFlow()

    val history: StateFlow<List<TransferEntry>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    private var context: ImportContext? = null

    fun open(uri: Uri) {
        viewModelScope.launch {
            _step.value = ImportStep.Working
            _step.value = try {
                val document = files.readText(uri)
                withContext(Dispatchers.Default) { review(document.name, document.charset, document.text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentTooLargeException) {
                ImportStep.Failed(FailureReason.TOO_LARGE)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read $uri", e)
                ImportStep.Failed(FailureReason.UNREADABLE)
            }
        }
    }

    private suspend fun review(name: String, charset: String, text: String): ImportStep {
        // An .xlsx/.ods file is a zip archive; its text would be garbage.
        if (text.startsWith("PK\u0003\u0004")) return ImportStep.Failed(FailureReason.SPREADSHEET)
        val delimiter = Csv.detectDelimiter(text)
        val rows = Csv.parse(text, delimiter)
        if (rows.none { row -> row.any { it.isNotBlank() } }) return ImportStep.Failed(FailureReason.EMPTY)
        val ctx = repository.importContext().also { context = it }

        val mapping = ColumnMapping.detect(rows)
        if (mapping != null) {
            val parsed = WorkoutFileParser(rows, mapping, defaultWorkoutName(name)).parse()
            val matches = ImportPlanner.suggestMatches(parsed, ctx.exercises)
            return ImportStep.ReviewWorkouts(buildReview(name, charset, delimiter, rows, mapping, parsed, matches, ctx))
        }
        val body = BodyColumnMapping.detect(rows)
        if (body != null) {
            return ImportStep.ReviewBody(BodyReview(name, charset, BodyImport.plan(rows, body, ctx.measurementTypes, ctx.measurements)))
        }
        return ImportStep.Failed(FailureReason.NO_HEADER, rows.firstOrNull { r -> r.any { it.isNotBlank() } }?.filter { it.isNotBlank() }?.joinToString(" | "))
    }

    /** Points [field] at another column (or none) and re-reads the file with the new layout. */
    fun remapColumn(field: WorkoutField, column: Int?) {
        val review = (step.value as? ImportStep.ReviewWorkouts)?.review ?: return
        val ctx = context ?: return
        viewModelScope.launch {
            val mapping = review.mapping.withField(field, column)
            val parsed = withContext(Dispatchers.Default) { WorkoutFileParser(review.rows, mapping, defaultWorkoutName(review.fileName)).parse() }
            // Keep the user's choices for names that are still in the file.
            val manual = review.matches.filter { it.reason == MatchReason.MANUAL }.associateBy { it.sourceName }
            val matches = ImportPlanner.suggestMatches(parsed, ctx.exercises).map { m -> manual[m.sourceName]?.copy(setCount = m.setCount) ?: m }
            _step.value = ImportStep.ReviewWorkouts(buildReview(review.fileName, review.charset, review.delimiter, review.rows, mapping, parsed, matches, ctx))
        }
    }

    /** The user's decision for one exercise name from the file. */
    fun setTarget(sourceName: String, target: ExerciseTarget) {
        val review = (step.value as? ImportStep.ReviewWorkouts)?.review ?: return
        val ctx = context ?: return
        val matches = review.matches.map { if (it.sourceName == sourceName) it.copy(target = target, reason = MatchReason.MANUAL) else it }
        _step.update { ImportStep.ReviewWorkouts(buildReview(review.fileName, review.charset, review.delimiter, review.rows, review.mapping, review.parsed, matches, ctx)) }
    }

    fun confirm() {
        val current = step.value
        viewModelScope.launch {
            _step.value = ImportStep.Working
            _step.value = try {
                when (current) {
                    is ImportStep.ReviewWorkouts -> ImportStep.Done(repository.applyWorkoutImport(current.review.plan, current.review.fileName))
                    is ImportStep.ReviewBody -> ImportStep.Done(repository.applyBodyImport(current.review.plan, current.review.fileName))
                    else -> current
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import failed; the transaction was rolled back", e)
                ImportStep.Failed(FailureReason.IMPORT_FAILED, e.message)
            }
        }
    }

    fun reset() {
        _step.value = ImportStep.Idle
        context = null
    }

    fun undo(entryId: String) {
        viewModelScope.launch {
            try {
                val result = repository.undo(entryId)
                _events.send(ImportEvent.Undone(result))
                if ((step.value as? ImportStep.Done)?.entry?.id == entryId) _step.value = ImportStep.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Undo failed", e)
                _events.send(ImportEvent.UndoFailed)
            }
        }
    }

    private fun buildReview(
        name: String,
        charset: String,
        delimiter: Char,
        rows: List<List<String>>,
        mapping: ColumnMapping,
        parsed: ParsedWorkoutFile,
        matches: List<ExerciseMatch>,
        ctx: ImportContext,
    ): WorkoutReview {
        val plan = ImportPlanner.plan(parsed, matches, ctx.exercises, ctx.sessions, ctx.measurements.filter { it.typeId == WEIGHT_TYPE })
        return WorkoutReview(name, charset, delimiter, rows, mapping, parsed, matches, plan, ctx.exercises)
    }

    private companion object {
        const val TAG = "DataImport"
        const val WEIGHT_TYPE = "weight"

        /** "Журнал - Понеділок.csv" → "Журнал - Понеділок": the workout name when the file has none. */
        fun defaultWorkoutName(fileName: String): String = fileName.substringBeforeLast('.').trim().ifEmpty { "Імпорт" }
    }
}

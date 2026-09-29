package com.boykodmytr.gymtracker.feature.data

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.repository.TransferEntry
import com.boykodmytr.gymtracker.domain.repository.TransferKind
import com.boykodmytr.gymtracker.domain.transfer.ExerciseMatch
import com.boykodmytr.gymtracker.domain.transfer.ExerciseTarget
import com.boykodmytr.gymtracker.domain.transfer.ImportIssue
import com.boykodmytr.gymtracker.domain.transfer.IssueKind
import com.boykodmytr.gymtracker.domain.transfer.IssueSeverity
import com.boykodmytr.gymtracker.domain.transfer.MatchReason
import com.boykodmytr.gymtracker.domain.transfer.SetValueKind
import com.boykodmytr.gymtracker.domain.transfer.WorkoutField
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.LocalClock
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.format.Fmt
import java.text.Collator
import java.util.Locale

private const val MAX_ISSUES_SHOWN = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportScreen(
    onBack: () -> Unit,
    viewModel: DataImportViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmUndo by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    val choose = { picker.launch(CSV_MIME_TYPES) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is ImportEvent.Undone -> if (event.result.exercisesKept > 0) {
                        resources.getString(R.string.import_undone_kept, event.result.sessionsRemoved, event.result.measurementsRemoved, event.result.exercisesKept)
                    } else {
                        resources.getString(R.string.import_undone, event.result.sessionsRemoved, event.result.measurementsRemoved)
                    }
                    ImportEvent.UndoFailed -> resources.getString(R.string.import_undo_failed)
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = step) {
                ImportStep.Idle -> {
                    item(key = "intro") {
                        SectionCard {
                            Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyMedium)
                            BigButton(stringResource(R.string.import_choose), choose, icon = Icons.Outlined.FileOpen)
                        }
                    }
                    item(key = "help") {
                        SectionCard(title = stringResource(R.string.import_format_help_title)) { Hint(stringResource(R.string.import_format_help)) }
                    }
                    historyItems(history) { confirmUndo = it }
                }
                ImportStep.Working -> item(key = "working") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.import_reading))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                is ImportStep.Failed -> item(key = "failed") {
                    SectionCard {
                        Text(failureText(resources, s.reason), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        if (s.reason == FailureReason.NO_HEADER && !s.detail.isNullOrBlank()) {
                            Hint(stringResource(R.string.import_failed_first_row, s.detail.take(200)))
                        }
                        SecondaryButton(stringResource(R.string.import_choose_other), choose, Modifier.fillMaxWidth(), icon = Icons.Outlined.FileOpen)
                    }
                }
                is ImportStep.ReviewWorkouts -> workoutReview(s.review, viewModel, resources)
                is ImportStep.ReviewBody -> bodyReview(s.review, viewModel, resources)
                is ImportStep.Done -> {
                    item(key = "done") {
                        SectionCard(title = stringResource(R.string.import_done_title)) {
                            val e = s.entry
                            Text(stringResource(R.string.import_done_text, e.sessions, e.sets, e.measurements, e.exercisesCreated))
                            SecondaryButton(stringResource(R.string.import_undo), { confirmUndo = e.id }, Modifier.fillMaxWidth())
                            SecondaryButton(stringResource(R.string.import_choose_other), choose, Modifier.fillMaxWidth(), icon = Icons.Outlined.FileOpen)
                        }
                    }
                    historyItems(history.filter { it.id != s.entry.id }) { confirmUndo = it }
                }
            }
        }
    }

    confirmUndo?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.import_undo_title),
            text = stringResource(R.string.import_undo_text),
            confirmLabel = stringResource(R.string.action_undo),
            onConfirm = {
                confirmUndo = null
                viewModel.undo(id)
            },
            onDismiss = { confirmUndo = null },
            destructive = true,
            dismissLabel = stringResource(R.string.action_close),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.workoutReview(
    review: WorkoutReview,
    viewModel: DataImportViewModel,
    resources: Resources,
) {
    val plan = review.plan
    item(key = "file") { FileCard(review.fileName, review.charset, review.delimiter) }
    item(key = "summary") {
        SectionCard(title = stringResource(R.string.import_summary_title)) {
            Text(stringResource(R.string.import_sum_workouts, plan.sessions.size), fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.import_sum_sets, plan.setCount))
            val dates = plan.sessions.map { it.session.date }
            if (dates.isNotEmpty()) Text(stringResource(R.string.import_sum_period, Fmt.shortDate(dates.min()), Fmt.shortDate(dates.max())))
            if (plan.bodyWeights.isNotEmpty()) Text(stringResource(R.string.import_sum_body_weight, plan.bodyWeights.size))
            if (plan.newExercises.isNotEmpty()) Text(stringResource(R.string.import_sum_new_exercises, plan.newExercises.size))
            if (plan.duplicateSessions > 0) Hint(stringResource(R.string.import_sum_duplicates, plan.duplicateSessions))
            if (plan.conflictingSessions > 0) {
                Text(stringResource(R.string.import_sum_conflicts, plan.conflictingSessions), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            ImportButtons(enabled = !plan.isEmpty, onImport = viewModel::confirm, onCancel = viewModel::reset)
        }
    }
    item(key = "exercises") {
        SectionCard(title = stringResource(R.string.import_exercises_title)) {
            Hint(stringResource(R.string.import_exercises_hint))
            review.matches.forEachIndexed { index, match ->
                if (index > 0) HorizontalDivider()
                MatchRow(match, review) { viewModel.setTarget(match.sourceName, it) }
            }
        }
    }
    item(key = "columns") { ColumnsCard(review, viewModel) }
    issueItems(plan.issues, resources)
}

private fun androidx.compose.foundation.lazy.LazyListScope.bodyReview(
    review: BodyReview,
    viewModel: DataImportViewModel,
    resources: Resources,
) {
    val plan = review.plan
    item(key = "file") { FileCard(review.fileName, review.charset, null) }
    item(key = "summary") {
        SectionCard(title = stringResource(R.string.import_summary_title)) {
            Text(stringResource(R.string.import_sum_measurements, plan.measurements.size), fontWeight = FontWeight.Medium)
            val dates = plan.measurements.map { it.date }
            if (dates.isNotEmpty()) Text(stringResource(R.string.import_sum_period, Fmt.shortDate(dates.min()), Fmt.shortDate(dates.max())))
            if (plan.newTypes.isNotEmpty()) Text(stringResource(R.string.import_sum_new_types, plan.newTypes.size) + ": " + plan.newTypes.joinToString { it.name })
            if (plan.duplicates > 0) Hint(stringResource(R.string.import_sum_duplicates, plan.duplicates))
            ImportButtons(enabled = !plan.isEmpty, onImport = viewModel::confirm, onCancel = viewModel::reset)
        }
    }
    issueItems(plan.issues, resources)
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyItems(history: List<TransferEntry>, onUndo: (String) -> Unit) {
    if (history.isEmpty()) return
    item(key = "history") {
        SectionCard(title = stringResource(R.string.import_history_title)) {
            history.take(10).forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (entry.kind == TransferKind.EXERCISE_MERGE) {
                                stringResource(R.string.import_history_merge, entry.title)
                            } else {
                                stringResource(R.string.import_history_import, entry.title, entry.sessions, entry.measurements)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Hint(Fmt.shortDate(entry.createdAt.atZone(LocalClock.current.zone).toLocalDate()))
                    }
                    if (entry.undone) {
                        Hint(stringResource(R.string.import_history_undone))
                    } else {
                        TextButton(onClick = { onUndo(entry.id) }) { Text(stringResource(R.string.action_undo)) }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.issueItems(issues: List<ImportIssue>, resources: Resources) {
    if (issues.isEmpty()) return
    // Errors first: they are what the user most likely wants to fix in the file.
    val sorted = issues.sortedWith(compareBy<ImportIssue> { it.severity.ordinal }.thenBy { it.row ?: Int.MAX_VALUE })
    item(key = "issues") {
        SectionCard(title = stringResource(R.string.import_issues_title, issues.size)) {
            sorted.take(MAX_ISSUES_SHOWN).forEach { issue ->
                val text = issueText(resources, issue)
                Text(
                    if (issue.row != null) resources.getString(R.string.import_row, issue.row, text) else text,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (issue.severity) {
                        IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                        IssueSeverity.WARNING -> MaterialTheme.colorScheme.onSurface
                        IssueSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (sorted.size > MAX_ISSUES_SHOWN) Hint(stringResource(R.string.import_issues_more, sorted.size - MAX_ISSUES_SHOWN))
        }
    }
}

@Composable
private fun FileCard(name: String, charset: String, delimiter: Char?) {
    SectionCard {
        Text(name, style = MaterialTheme.typography.titleMedium)
        val separator = when (delimiter) {
            null -> null
            '\t' -> stringResource(R.string.import_tab)
            else -> delimiter.toString()
        }
        if (separator != null) Hint(stringResource(R.string.import_file_info, charset, separator)) else Hint(charset)
    }
}

@Composable
private fun ImportButtons(enabled: Boolean, onImport: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(stringResource(if (enabled) R.string.import_confirm else R.string.import_nothing), onImport, enabled = enabled)
        SecondaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MatchRow(match: ExerciseMatch, review: WorkoutReview, onPick: (ExerciseTarget) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { picking = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(match.sourceName, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(match.target.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            val reason = stringResource(
                when (match.reason) {
                    MatchReason.SAME_NAME -> R.string.import_match_same
                    MatchReason.ALIAS -> R.string.import_match_alias
                    MatchReason.NEW -> R.string.import_match_new
                    MatchReason.MANUAL -> R.string.import_match_manual
                },
            )
            Hint("$reason · ${stringResource(R.string.import_sum_sets, match.setCount)}")
        }
    }
    if (picking) {
        val collator = remember { Collator.getInstance(Locale.forLanguageTag("uk")) }
        val options = remember(review.exercises) { review.exercises.sortedWith(compareBy(collator) { it.name }) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.import_pick_title, match.sourceName)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item(key = "new") {
                        PickRow(stringResource(R.string.import_pick_new, match.sourceName), bold = true) {
                            picking = false
                            onPick(ExerciseTarget.New(match.sourceName))
                        }
                    }
                    items(options, key = { it.id }) { exercise ->
                        PickRow(exercise.name, bold = (match.target as? ExerciseTarget.Existing)?.id == exercise.id) {
                            picking = false
                            onPick(ExerciseTarget.Existing(exercise.id, exercise.name))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

@Composable
private fun PickRow(text: String, bold: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
private fun ColumnsCard(review: WorkoutReview, viewModel: DataImportViewModel) {
    val mapping = review.mapping
    SectionCard(title = stringResource(R.string.import_columns_title)) {
        Hint(stringResource(R.string.import_columns_hint))
        if (mapping.isWide) {
            val sets = mapping.wideSets.values.map { it.setNumber }.distinct().sorted()
            val kinds = mapping.wideSets.values.map { it.kind }.toSet()
            val what = listOfNotNull(
                stringResource(R.string.import_field_weight).takeIf { SetValueKind.WEIGHT in kinds },
                stringResource(R.string.import_field_reps).takeIf { SetValueKind.REPS in kinds },
            ).joinToString(" + ")
            Text(stringResource(R.string.import_wide_sets, "${sets.joinToString()} ($what)"), style = MaterialTheme.typography.bodyMedium)
        }
        if (mapping.weightInPounds) Hint(stringResource(R.string.import_pounds))
        WorkoutField.entries.forEach { field ->
            if (mapping.isWide && (field == WorkoutField.WEIGHT || field == WorkoutField.REPS || field == WorkoutField.SET_NUMBER) && field !in mapping.fields) return@forEach
            ColumnRow(field, mapping.fields[field], mapping.headers) { viewModel.remapColumn(field, it) }
        }
    }
}

@Composable
private fun ColumnRow(field: WorkoutField, column: Int?, headers: List<String>, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fieldLabel(field), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                column?.let { columnLabel(it, headers) } ?: stringResource(R.string.import_column_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (column == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.import_column_none)) }, onClick = { open = false; onSelect(null) })
            headers.indices.filter { headers[it].isNotBlank() }.forEach { index ->
                DropdownMenuItem(text = { Text(columnLabel(index, headers)) }, onClick = { open = false; onSelect(index) })
            }
        }
    }
}

/** "D · С1 вага (кг)": spreadsheet column letter and header. */
private fun columnLabel(index: Int, headers: List<String>): String {
    var n = index
    val letters = StringBuilder()
    do {
        letters.insert(0, 'A' + n % 26)
        n = n / 26 - 1
    } while (n >= 0)
    return "$letters · ${headers.getOrElse(index) { "" }}"
}

@Composable
private fun fieldLabel(field: WorkoutField): String = stringResource(
    when (field) {
        WorkoutField.DATE -> R.string.import_field_date
        WorkoutField.START_TIME -> R.string.import_field_start_time
        WorkoutField.DURATION -> R.string.import_field_duration
        WorkoutField.WORKOUT -> R.string.import_field_workout
        WorkoutField.WORKOUT_NOTES -> R.string.import_field_workout_notes
        WorkoutField.EXERCISE -> R.string.import_field_exercise
        WorkoutField.PLAN -> R.string.import_field_plan
        WorkoutField.SET_NUMBER -> R.string.import_field_set_number
        WorkoutField.WEIGHT -> R.string.import_field_weight
        WorkoutField.REPS -> R.string.import_field_reps
        WorkoutField.RPE -> R.string.import_field_rpe
        WorkoutField.FAILURE -> R.string.import_field_failure
        WorkoutField.SET_NOTE -> R.string.import_field_set_note
        WorkoutField.EXERCISE_NOTE -> R.string.import_field_exercise_note
        WorkoutField.BODY_WEIGHT -> R.string.import_field_body_weight
    },
)

private fun failureText(resources: Resources, reason: FailureReason): String = resources.getString(
    when (reason) {
        FailureReason.UNREADABLE -> R.string.import_failed_unreadable
        FailureReason.TOO_LARGE -> R.string.import_failed_too_large
        FailureReason.SPREADSHEET -> R.string.import_failed_spreadsheet
        FailureReason.NO_HEADER -> R.string.import_failed_no_header
        FailureReason.EMPTY -> R.string.import_failed_empty
        FailureReason.IMPORT_FAILED -> R.string.import_failed_write
    },
)

internal fun issueText(resources: Resources, issue: ImportIssue): String {
    val value = issue.value.orEmpty()
    val date = issue.date?.let(Fmt::shortDate).orEmpty()
    val workout = issue.workout.orEmpty()
    return when (issue.kind) {
        IssueKind.NO_DATE -> resources.getString(R.string.issue_no_date)
        IssueKind.BAD_DATE -> resources.getString(R.string.issue_bad_date, value)
        IssueKind.NO_EXERCISE -> resources.getString(R.string.issue_no_exercise)
        IssueKind.BAD_WEIGHT -> resources.getString(R.string.issue_bad_weight, value)
        IssueKind.BAD_REPS -> resources.getString(R.string.issue_bad_reps, value)
        IssueKind.MISSING_REPS -> resources.getString(R.string.issue_missing_reps)
        IssueKind.BAD_TIME -> resources.getString(R.string.issue_bad_time, value)
        IssueKind.BAD_DURATION -> resources.getString(R.string.issue_bad_duration, value)
        IssueKind.BAD_RPE -> resources.getString(R.string.issue_bad_rpe, value)
        IssueKind.BAD_FAILURE -> resources.getString(R.string.issue_bad_failure, value)
        IssueKind.BAD_BODY_WEIGHT -> resources.getString(R.string.issue_bad_body_weight, value)
        IssueKind.ANNOTATED_VALUE -> resources.getString(R.string.issue_annotated, value)
        IssueKind.DUPLICATE_ROW -> resources.getString(R.string.issue_duplicate_row, value)
        IssueKind.SESSION_CONFLICT -> resources.getString(R.string.issue_session_conflict, date, workout)
        IssueKind.SESSION_DUPLICATE -> resources.getString(R.string.issue_session_duplicate, date, workout)
        IssueKind.SAME_DATE -> resources.getString(R.string.issue_same_date, date, workout, value)
        IssueKind.MEASUREMENT_DUPLICATE -> resources.getString(R.string.issue_measurement_duplicate)
        IssueKind.BAD_VALUE -> resources.getString(R.string.issue_bad_value, value)
        IssueKind.NO_PARAMETER -> resources.getString(R.string.issue_no_parameter)
    }
}

/** CSV files come with many MIME types depending on the app that saved them. */
private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/tab-separated-values",
    "text/plain",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

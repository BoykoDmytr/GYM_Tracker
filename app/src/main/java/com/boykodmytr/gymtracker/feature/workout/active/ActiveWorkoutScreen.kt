package com.boykodmytr.gymtracker.feature.workout.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.logic.ProgressionHint
import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.logic.WorkoutStep
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.SetInputSheet
import com.boykodmytr.gymtracker.ui.components.SetRow
import com.boykodmytr.gymtracker.ui.components.toInput
import com.boykodmytr.gymtracker.ui.components.rememberNow
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.setsSummary
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle
import java.time.Duration

private enum class WorkoutDialog { FINISH, DISCARD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onExit: () -> Unit,
    onFinished: (sessionId: String) -> Unit,
    onEditExercise: (exerciseId: String) -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ActiveWorkoutEvent.Finished -> onFinished(event.sessionId)
                ActiveWorkoutEvent.Discarded -> onExit()
            }
        }
    }
    LaunchedEffect(state.closed) { if (state.closed) onExit() }

    // The phone lies on a bench between sets; letting it lock would hide the rest timer.
    val view = LocalView.current
    val keepScreenOn = state.settings.keepScreenOn
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val session = state.session ?: return
    val step = state.step
    var dialog by rememberSaveable { mutableStateOf<WorkoutDialog?>(null) }
    var showLogSheet by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showExerciseList by rememberSaveable { mutableStateOf(false) }
    var showTechnique by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val unit = state.settings.weightUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.name, style = MaterialTheme.typography.titleMedium)
                        val now by rememberNow()
                        Text(
                            Fmt.clock(Duration.between(session.startedAt, now)),
                            style = MaterialTheme.typography.titleLarge.merge(NumberTextStyle),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.workout_back_home))
                    }
                },
                actions = {
                    IconButton(onClick = { showExerciseList = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, stringResource(R.string.workout_exercises))
                    }
                    TextButton(onClick = { dialog = WorkoutDialog.FINISH }) { Text(stringResource(R.string.workout_finish)) }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workout_discard)) },
                            onClick = {
                                menuOpen = false
                                dialog = WorkoutDialog.DISCARD
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (step != null) {
                Surface(tonalElevation = 3.dp) {
                    BottomActions(
                        step = step,
                        onCompleteSet = { showLogSheet = true },
                        onLeaveExercise = viewModel::leaveExercise,
                        onCompleteEarly = viewModel::completeExerciseEarly,
                        onExtraSet = viewModel::requestExtraSet,
                        onFinish = { dialog = WorkoutDialog.FINISH },
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.workout_exercise_progress, state.exerciseNumber, state.exerciseCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (state.exerciseCount == 0) 0f else state.closedExercises.toFloat() / state.exerciseCount },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )

            val rest = session.rest
            if (rest != null) {
                RestPanel(
                    rest = rest,
                    onAdjust = viewModel::adjustRest,
                    onPreset = viewModel::setRestDuration,
                    onSkip = viewModel::skipRest,
                )
            } else if (!state.settings.autoStartRest && session.totalSets > 0) {
                RestStarter(onStart = viewModel::startRest, defaultSeconds = state.restSeconds)
            }

            if (step != null) {
                ExerciseHeader(name = step.exercise.exerciseName, onTechnique = { showTechnique = true })
                when (step) {
                    is WorkoutStep.PerformSet -> PerformSetContent(step, state.lastPerformance, state.hint, unit)
                    is WorkoutStep.ExerciseDone -> ExerciseDoneContent(step)
                }
                if (step.exercise.sets.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.workout_logged_sets)) {
                        step.exercise.sets.forEachIndexed { index, set ->
                            if (index > 0) HorizontalDivider()
                            SetRow(set = set, weightUnit = unit, onClick = { editingSetId = set.id })
                        }
                    }
                }
            }
        }
    }

    if (showLogSheet && step is WorkoutStep.PerformSet) {
        SetInputSheet(
            title = stringResource(R.string.set_result_title, step.setNumber),
            initial = state.suggestedInput,
            weightUnit = unit,
            onSave = {
                showLogSheet = false
                viewModel.logSet(it)
            },
            onDismiss = { showLogSheet = false },
        )
    }

    val editingSet = editingSetId?.let { id -> session.exercises.flatMap { it.sets }.firstOrNull { it.id == id } }
    if (editingSet != null) {
        SetInputSheet(
            title = stringResource(R.string.set_edit_title, editingSet.setNumber),
            initial = editingSet.toInput(),
            weightUnit = unit,
            onSave = {
                editingSetId = null
                viewModel.updateSet(editingSet.id, it)
            },
            onDismiss = { editingSetId = null },
            onDelete = {
                editingSetId = null
                viewModel.deleteSet(editingSet.id)
            },
        )
    }

    if (showExerciseList) {
        ExerciseListSheet(
            exercises = WorkoutFlow.ordered(session),
            currentId = step?.exercise?.id,
            onSelect = {
                showExerciseList = false
                viewModel.jumpTo(it)
            },
            onDismiss = { showExerciseList = false },
        )
    }

    if (showTechnique) {
        TechniqueSheet(
            details = state.technique,
            onEdit = step?.exercise?.exerciseId?.let { id -> { showTechnique = false; onEditExercise(id) } },
            onDismiss = { showTechnique = false },
        )
    }

    when (dialog) {
        WorkoutDialog.FINISH -> if (session.totalSets == 0) {
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_title),
                text = stringResource(R.string.workout_finish_empty),
                confirmLabel = stringResource(R.string.workout_discard),
                onConfirm = { dialog = null; viewModel.discard() },
                onDismiss = { dialog = null },
                destructive = true,
            )
        } else {
            val text = stringResource(R.string.workout_finish_text) +
                if (WorkoutFlow.hasUnfinishedExercises(session)) "\n\n" + stringResource(R.string.workout_finish_unfinished) else ""
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_title),
                text = text,
                confirmLabel = stringResource(R.string.workout_finish),
                onConfirm = { dialog = null; viewModel.finish() },
                onDismiss = { dialog = null },
            )
        }
        WorkoutDialog.DISCARD -> ConfirmDialog(
            title = stringResource(R.string.workout_discard_title),
            text = stringResource(R.string.workout_discard_text),
            confirmLabel = stringResource(R.string.workout_discard),
            onConfirm = { dialog = null; viewModel.discard() },
            onDismiss = { dialog = null },
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun ExerciseHeader(name: String, onTechnique: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onTechnique, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.workout_technique))
        }
    }
}

@Composable
private fun PerformSetContent(
    step: WorkoutStep.PerformSet,
    lastPerformance: List<SetLog>,
    hint: ProgressionHint?,
    unit: WeightUnit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (step.isExtra) {
                stringResource(R.string.workout_extra_set, step.setNumber)
            } else {
                stringResource(R.string.workout_set_of, step.setNumber, step.plannedSets)
            },
            style = MaterialTheme.typography.displaySmall.merge(NumberTextStyle),
        )
        if (step.isOptional) AssistChip(onClick = {}, label = { Text(stringResource(R.string.workout_optional)) })
    }
    val target = step.exercise.target
    SectionCard(title = stringResource(R.string.workout_plan), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(R.string.workout_plan_reps, Fmt.range(target.repsMin, target.repsMax)),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            target.weightKg?.let { stringResource(R.string.workout_plan_weight, Fmt.weightWithUnit(it, unit)) }
                ?: stringResource(R.string.workout_plan_no_weight),
            style = if (target.weightKg != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
        )
    }
    if (lastPerformance.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.workout_last_time)) {
            Text(setsSummary(lastPerformance, unit), style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle))
            when (hint) {
                is ProgressionHint.IncreaseWeight -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.workout_hint_increase), style = MaterialTheme.typography.bodyMedium)
                }
                is ProgressionHint.AddReps -> Text(
                    stringResource(R.string.workout_hint_reps, Fmt.weightWithUnit(hint.currentWeightKg, unit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun ExerciseDoneContent(step: WorkoutStep.ExerciseDone) {
    val skipped = step.exercise.status == ExerciseStatus.SKIPPED
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (skipped) Icons.Outlined.RemoveCircleOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Text(
                stringResource(if (skipped) R.string.workout_exercise_skipped else R.string.workout_exercise_done),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        val next = step.next
        Text(
            if (next != null) stringResource(R.string.workout_next_is, next.exerciseName) else stringResource(R.string.workout_all_done),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun BottomActions(
    step: WorkoutStep,
    onCompleteSet: () -> Unit,
    onLeaveExercise: () -> Unit,
    onCompleteEarly: () -> Unit,
    onExtraSet: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            is WorkoutStep.PerformSet -> {
                BigButton(stringResource(R.string.workout_complete_set), onCompleteSet, icon = Icons.Filled.Check)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.workout_skip_exercise), onLeaveExercise, Modifier.weight(1f))
                    if (WorkoutFlow.canCompleteEarly(step.exercise) && !step.isExtra) {
                        SecondaryButton(stringResource(R.string.workout_complete_exercise), onCompleteEarly, Modifier.weight(1f))
                    }
                }
            }
            is WorkoutStep.ExerciseDone -> {
                if (step.next != null) {
                    BigButton(stringResource(R.string.workout_next_exercise), onLeaveExercise, icon = Icons.AutoMirrored.Filled.ArrowForward)
                } else {
                    BigButton(stringResource(R.string.workout_finish_workout), onFinish, icon = Icons.Filled.Check)
                }
                SecondaryButton(stringResource(R.string.workout_add_set), onExtraSet, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
            }
        }
    }
}

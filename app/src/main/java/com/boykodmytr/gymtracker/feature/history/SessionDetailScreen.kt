package com.boykodmytr.gymtracker.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.LocalClock
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.SetInputSheet
import com.boykodmytr.gymtracker.ui.components.SetRow
import com.boykodmytr.gymtracker.ui.components.StatRow
import com.boykodmytr.gymtracker.ui.components.StatTile
import com.boykodmytr.gymtracker.ui.components.toInput
import com.boykodmytr.gymtracker.ui.format.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExerciseProgress: (exerciseId: String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<String?>(null) }
    val session = state.session
    val unit = state.weightUnit
    val zone = LocalClock.current.zone

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.session_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (session == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                val start = Fmt.knownTime(session.startedAt, zone)
                val end = if (start != null) session.endedAt?.atZone(zone)?.toLocalTime() else null
                Text(Fmt.fullDate(session.date).replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleLarge)
                if (start != null) {
                    Text(
                        listOfNotNull(Fmt.time(start), end?.let(Fmt::time)).joinToString("–"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "tiles") {
                StatRow {
                    StatTile(stringResource(R.string.summary_duration), session.duration?.let(Fmt::minutes) ?: "–", Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_sets), session.totalSets.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_volume), Fmt.volume(session.totalVolumeKg, unit), Modifier.weight(1f))
                }
            }
            if (session.notes.isNotBlank()) {
                item(key = "notes") {
                    SectionCard(title = stringResource(R.string.summary_notes)) { Text(session.notes) }
                }
            }
            items(session.exercises, key = { it.id }) { exercise ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onOpenExerciseProgress(exercise.exerciseId) }) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, stringResource(R.string.session_exercise_progress))
                        }
                    }
                    Text(
                        stringResource(R.string.session_plan, Fmt.target(exercise.target)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Notes of imported workouts (the journal's comment, its spelling of the name).
                    if (exercise.notes.isNotBlank()) {
                        Text(exercise.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (exercise.sets.isEmpty() || exercise.status == ExerciseStatus.SKIPPED) {
                        Text(stringResource(R.string.summary_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    exercise.sets.forEachIndexed { index, set ->
                        if (index > 0) HorizontalDivider()
                        SetRow(set, unit, onClick = { editingSetId = set.id })
                    }
                }
            }
        }
    }

    val editing = editingSetId?.let { id -> session?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == id } }
    if (editing != null) {
        SetInputSheet(
            title = stringResource(R.string.set_edit_title, editing.setNumber),
            initial = editing.toInput(),
            weightUnit = unit,
            onSave = {
                editingSetId = null
                viewModel.updateSet(editing.id, it)
            },
            onDismiss = { editingSetId = null },
            onDelete = {
                editingSetId = null
                viewModel.deleteSet(editing.id)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.session_delete_title),
            text = stringResource(R.string.session_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSession()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

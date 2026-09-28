package com.boykodmytr.gymtracker.feature.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.TextInputDialog
import com.boykodmytr.gymtracker.ui.format.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    pickedExerciseId: String?,
    onPickedExerciseConsumed: () -> Unit,
    onBack: () -> Unit,
    onPickExercise: (templateId: String) -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.deleted.collect { onBack() } }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val template = state.template
    val unit = state.weightUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.template_rename)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { padding ->
        if (template == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (template.exercises.isEmpty()) item(key = "empty") { EmptyState(stringResource(R.string.programs_workout_empty)) }
            itemsIndexed(template.exercises, key = { _, e -> e.id }) { index, item ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${item.exercise.name}", style = MaterialTheme.typography.titleMedium)
                            val details = buildList {
                                add(Fmt.target(item.target))
                                item.target.weightKg?.let { add(Fmt.weightWithUnit(it, unit)) }
                                add(
                                    item.restSeconds?.let { stringResource(R.string.preview_rest, Fmt.restLabel(it)) }
                                        ?: stringResource(R.string.template_rest_default, Fmt.restLabel(state.defaultRestSeconds)),
                                )
                            }
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                            if (item.notes.isNotBlank()) {
                                Text(item.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row {
                        IconButton(onClick = { viewModel.move(item.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up))
                        }
                        IconButton(onClick = { viewModel.move(item.id, 1) }, enabled = index < template.exercises.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down))
                        }
                        IconButton(onClick = { editingId = item.id }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                        IconButton(onClick = { viewModel.remove(item.id) }) {
                            Icon(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.template_remove_exercise))
                        }
                    }
                }
            }
            item(key = "add") {
                SecondaryButton(
                    stringResource(R.string.template_add_exercise),
                    { onPickExercise(template.id) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add,
                )
            }
        }
    }

    val editing = editingId?.let { id -> template?.exercises?.firstOrNull { it.id == id } }
    if (editing != null) {
        TargetSheet(
            title = editing.exercise.name,
            initial = editing.target,
            initialRestSeconds = editing.restSeconds,
            initialNotes = editing.notes,
            defaultRestSeconds = state.defaultRestSeconds,
            weightUnit = unit,
            onConfirm = { target, rest, notes ->
                editingId = null
                viewModel.update(editing.id, target, rest, notes)
            },
            onDismiss = { editingId = null },
        )
    }

    // An exercise came back from the library picker: ask for its sets and reps before adding it.
    val picked = pickedExerciseId?.let { id -> state.exercises.firstOrNull { it.id == id } }
    if (picked != null) {
        TargetSheet(
            title = picked.name,
            initial = null,
            initialRestSeconds = null,
            initialNotes = "",
            defaultRestSeconds = state.defaultRestSeconds,
            weightUnit = unit,
            onConfirm = { target, rest, _ ->
                onPickedExerciseConsumed()
                viewModel.add(picked.id, target, rest)
            },
            onDismiss = onPickedExerciseConsumed,
        )
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.template_rename),
            label = stringResource(R.string.programs_workout_name),
            initial = template?.name.orEmpty(),
            onConfirm = { name, _ ->
                renaming = false
                viewModel.rename(name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.template_delete_title),
            text = stringResource(R.string.template_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import com.boykodmytr.gymtracker.ui.components.BigTonalButton
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.TextInputDialog
import com.boykodmytr.gymtracker.ui.format.Fmt

private enum class ProgramDialog { EDIT, ADD_WORKOUT, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    viewModel: ProgramDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<ProgramDialog?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramDetailEvent.TemplateCreated -> onOpenTemplate(event.templateId)
                ProgramDetailEvent.Deleted -> onBack()
            }
        }
    }
    val program = state.program

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { dialog = ProgramDialog.EDIT }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { dialog = ProgramDialog.DELETE }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                },
            )
        },
    ) { padding ->
        if (program == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (program.description.isNotBlank()) Text(program.description, style = MaterialTheme.typography.bodyLarge)
                    if (program.isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.programs_active_long), style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        BigTonalButton(stringResource(R.string.programs_make_active), viewModel::makeActive)
                    }
                    Text(
                        stringResource(R.string.programs_rotation_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(program.workouts, key = { _, w -> w.id }) { index, workout ->
                SectionCard(onClick = { onOpenTemplate(workout.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(workout.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.moveTemplate(workout.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up))
                        }
                        IconButton(onClick = { viewModel.moveTemplate(workout.id, 1) }, enabled = index < program.workouts.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down))
                        }
                    }
                    workout.exercises.forEach { e ->
                        Text("${e.exercise.name} — ${Fmt.target(e.target)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (workout.exercises.isEmpty()) {
                        Text(stringResource(R.string.programs_workout_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "add") {
                SecondaryButton(
                    stringResource(R.string.programs_add_workout),
                    { dialog = ProgramDialog.ADD_WORKOUT },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add,
                )
            }
        }
    }

    when (dialog) {
        ProgramDialog.EDIT -> TextInputDialog(
            title = stringResource(R.string.programs_edit),
            label = stringResource(R.string.programs_name),
            initial = program?.name.orEmpty(),
            secondaryLabel = stringResource(R.string.programs_description),
            initialSecondary = program?.description.orEmpty(),
            onConfirm = { name, description ->
                dialog = null
                viewModel.update(name, description)
            },
            onDismiss = { dialog = null },
        )
        ProgramDialog.ADD_WORKOUT -> TextInputDialog(
            title = stringResource(R.string.programs_add_workout),
            label = stringResource(R.string.programs_workout_name),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name, _ ->
                dialog = null
                viewModel.addTemplate(name)
            },
            onDismiss = { dialog = null },
        )
        ProgramDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.programs_delete_title),
            text = stringResource(R.string.programs_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                dialog = null
                viewModel.delete()
            },
            onDismiss = { dialog = null },
            destructive = true,
        )
        null -> Unit
    }
}

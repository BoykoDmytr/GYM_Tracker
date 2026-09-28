package com.boykodmytr.gymtracker.feature.workout.summary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.StatRow
import com.boykodmytr.gymtracker.ui.components.StatTile
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.setsSummary

@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session
    var notes by rememberSaveable(session?.id) { mutableStateOf(session?.notes.orEmpty()) }
    val done = {
        if (session != null && notes.trim() != session.notes) viewModel.saveNotes(notes)
        onDone()
    }
    BackHandler(onBack = done)

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    BigButton(stringResource(R.string.action_done), done)
                }
            }
        },
    ) { padding ->
        if (session == null) return@Scaffold
        val unit = state.weightUnit
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${session.name} · ${Fmt.dayMonth(session.date)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                StatRow {
                    StatTile(stringResource(R.string.summary_duration), session.duration?.let(Fmt::clock) ?: "–", Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_sets), session.totalSets.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_volume), Fmt.volume(session.totalVolumeKg, unit), Modifier.weight(1f))
                }
            }
            items(session.exercises, key = { it.id }) { exercise ->
                SectionCard {
                    Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                    if (exercise.status == ExerciseStatus.SKIPPED || exercise.sets.isEmpty()) {
                        Text(stringResource(R.string.summary_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(setsSummary(exercise.sets, unit), style = MaterialTheme.typography.bodyLarge)
                    }
                    val records = state.records[exercise.id].orEmpty()
                    if (records.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            records.forEach { kind ->
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            stringResource(
                                                if (kind == RecordKind.WEIGHT) R.string.summary_record_weight else R.string.summary_record_1rm,
                                            ),
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.summary_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }
    }
}

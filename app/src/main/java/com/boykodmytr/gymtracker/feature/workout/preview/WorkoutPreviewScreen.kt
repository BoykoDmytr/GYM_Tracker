package com.boykodmytr.gymtracker.feature.workout.preview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.TemplateExercise
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.setsSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPreviewScreen(
    onBack: () -> Unit,
    onStarted: (sessionId: String) -> Unit,
    viewModel: WorkoutPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.started.collect(onStarted) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.template?.name ?: stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
        bottomBar = {
            val inProgress = state.inProgress
            val template = state.template
            if (!state.loading && (inProgress != null || !template?.exercises.isNullOrEmpty())) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        if (inProgress != null) {
                            BigButton(
                                text = stringResource(R.string.preview_continue_session),
                                onClick = { onStarted(inProgress.id) },
                                icon = Icons.Filled.PlayArrow,
                            )
                        } else {
                            BigButton(
                                text = stringResource(R.string.preview_start),
                                onClick = viewModel::start,
                                icon = Icons.Filled.PlayArrow,
                                enabled = !state.starting,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        val template = state.template
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.inProgress?.let { session ->
                item(key = "in-progress") {
                    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(stringResource(R.string.preview_session_in_progress, session.name))
                    }
                }
            }
            val workouts = state.program?.workouts.orEmpty()
            if (workouts.size > 1) {
                item(key = "chips") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        workouts.forEach { w ->
                            FilterChip(
                                selected = w.id == template?.id,
                                onClick = { viewModel.selectTemplate(w.id) },
                                label = { Text(w.name) },
                            )
                        }
                    }
                }
            }
            if (template == null || template.exercises.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.preview_empty)) }
            } else {
                itemsIndexed(template.exercises, key = { _, e -> e.id }) { index, exercise ->
                    PreviewExerciseRow(
                        index = index + 1,
                        exercise = exercise,
                        lastTime = setsSummary(state.lastPerformance[exercise.exercise.id].orEmpty(), state.settings.weightUnit),
                        restSeconds = exercise.restSeconds ?: state.settings.defaultRestSeconds,
                        weightUnit = state.settings.weightUnit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewExerciseRow(
    index: Int,
    exercise: TemplateExercise,
    lastTime: String,
    restSeconds: Int,
    weightUnit: WeightUnit,
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$index.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.exercise.name, style = MaterialTheme.typography.titleMedium)
                val plan = buildList {
                    add(Fmt.target(exercise.target))
                    exercise.target.weightKg?.let { add(Fmt.weightWithUnit(it, weightUnit)) }
                    add(stringResource(R.string.preview_rest, Fmt.restLabel(restSeconds)))
                }
                Text(plan.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (lastTime.isNotEmpty()) {
                    Text(
                        stringResource(R.string.preview_last_time, lastTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

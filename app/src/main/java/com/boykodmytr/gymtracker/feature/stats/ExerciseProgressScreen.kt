package com.boykodmytr.gymtracker.feature.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.logic.ExerciseSessionProgress
import com.boykodmytr.gymtracker.domain.logic.RecordValue
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.charts.ChartPoint
import com.boykodmytr.gymtracker.ui.charts.LineChart
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.StatRow
import com.boykodmytr.gymtracker.ui.components.StatTile
import com.boykodmytr.gymtracker.ui.format.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressScreen(
    onBack: () -> Unit,
    onEditExercise: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: ExerciseProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.weightUnit
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { onEditExercise(state.exerciseId) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.sessions.isEmpty()) {
                item { EmptyState(stringResource(R.string.progress_empty), icon = Icons.AutoMirrored.Filled.ShowChart) }
                return@LazyColumn
            }
            item(key = "metrics") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableMetrics.forEach { metric ->
                        FilterChip(
                            selected = state.metric == metric,
                            onClick = { viewModel.selectMetric(metric) },
                            label = { Text(stringResource(metric.label)) },
                        )
                    }
                }
            }
            item(key = "chart") {
                SectionCard {
                    LineChart(
                        points = state.sessions.map { s ->
                            ChartPoint(
                                x = s.startedAt.epochSecond / 86_400.0,
                                y = s.metricValue(state.metric, unit),
                                xLabel = Fmt.shortDayMonth(s.date),
                            )
                        },
                        valueFormatter = { formatMetric(it, state.metric, unit) },
                        includeZero = state.metric == ProgressMetric.REPS,
                        description = stringResource(state.metric.label),
                    )
                    if (state.metric == ProgressMetric.ONE_REP_MAX) {
                        Text(
                            stringResource(R.string.progress_1rm_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.progression.size > 1 && state.progression.any { it > 0 }) {
                item(key = "progression") {
                    SectionCard(title = stringResource(R.string.progress_weight_path)) {
                        Text(
                            state.progression.joinToString(" → ") { Fmt.weight(it, unit) } + " " + Fmt.weightUnitLabel(unit),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            state.records?.let { records ->
                item(key = "records") {
                    StatRow {
                        records.maxWeight?.let { RecordTile(stringResource(R.string.progress_record_weight), it, Modifier.weight(1f)) { v -> Fmt.weightWithUnit(v, unit) } }
                        records.bestOneRepMax?.let { RecordTile(stringResource(R.string.progress_record_1rm), it, Modifier.weight(1f)) { v -> Fmt.weightWithUnit(v, unit) } }
                        records.maxReps?.let { RecordTile(stringResource(R.string.progress_record_reps), it, Modifier.weight(1f)) { v -> v.toInt().toString() } }
                    }
                }
            }
            item(key = "history-title") {
                Text(stringResource(R.string.progress_history), style = MaterialTheme.typography.titleMedium)
            }
            items(state.sessions.reversed(), key = { it.sessionId }) { session ->
                val sets = state.setsBySession[session.sessionId].orEmpty()
                SectionCard(onClick = { onOpenSession(session.sessionId) }) {
                    Text(Fmt.dayMonthYear(session.date), style = MaterialTheme.typography.titleSmall)
                    Text(
                        sets.joinToString(", ") { if (it.weightKg > 0) "${Fmt.weight(it.weightKg, unit)}×${it.reps}" else "${it.reps}" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordTile(label: String, record: RecordValue, modifier: Modifier, format: (Double) -> String) {
    StatTile(label = "$label · ${Fmt.shortDayMonth(record.date)}", value = format(record.value), modifier = modifier)
}

private val ProgressMetric.label: Int
    get() = when (this) {
        ProgressMetric.MAX_WEIGHT -> R.string.progress_metric_weight
        ProgressMetric.ONE_REP_MAX -> R.string.progress_metric_1rm
        ProgressMetric.VOLUME -> R.string.progress_metric_volume
        ProgressMetric.REPS -> R.string.progress_metric_reps
    }

private fun ExerciseSessionProgress.metricValue(metric: ProgressMetric, unit: WeightUnit): Double = when (metric) {
    ProgressMetric.MAX_WEIGHT -> unit.fromKg(bestWeightKg)
    ProgressMetric.ONE_REP_MAX -> unit.fromKg(estimatedOneRepMaxKg)
    ProgressMetric.VOLUME -> unit.fromKg(volumeKg)
    ProgressMetric.REPS -> maxReps.toDouble()
}

private fun formatMetric(value: Double, metric: ProgressMetric, unit: WeightUnit): String = when (metric) {
    ProgressMetric.REPS -> "${value.toInt()} повт."
    ProgressMetric.VOLUME -> "${Fmt.number(Math.round(value).toDouble())} ${Fmt.weightUnitLabel(unit)}"
    else -> "${Fmt.oneDecimal(value)} ${Fmt.weightUnitLabel(unit)}"
}

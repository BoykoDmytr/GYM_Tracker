package com.boykodmytr.gymtracker.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.charts.BarChart
import com.boykodmytr.gymtracker.ui.charts.BarDatum
import com.boykodmytr.gymtracker.ui.charts.ChartPoint
import com.boykodmytr.gymtracker.ui.charts.LineChart
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.StatRow
import com.boykodmytr.gymtracker.ui.components.StatTile
import com.boykodmytr.gymtracker.ui.format.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenExercise: (String) -> Unit,
    onOpenBodyWeight: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_stats)) }) }) { padding ->
        val stats = state.stats ?: return@Scaffold
        val unit = state.weightUnit
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "kpi1") {
                StatRow {
                    StatTile(stringResource(R.string.home_this_week), stringResource(R.string.home_week_value, stats.thisWeek, state.weekTarget), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_this_month), stats.thisMonth.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_total), stats.total.toString(), Modifier.weight(1f))
                }
            }
            item(key = "kpi2") {
                StatRow {
                    StatTile(stringResource(R.string.stats_avg_duration), stats.averageDuration?.let(Fmt::minutes) ?: "–", Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_month_volume), Fmt.volume(stats.monthVolumeKg, unit), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_missed_30), stats.missedLast30Days.toString(), Modifier.weight(1f))
                }
            }
            item(key = "totals") {
                Text(
                    stringResource(
                        R.string.stats_totals_line,
                        pluralStringResource(R.plurals.weeks_count, stats.weeklyStreak, stats.weeklyStreak),
                        Fmt.volume(stats.totalVolumeKg, unit),
                        Fmt.duration(stats.totalDuration),
                        stats.missedTotal,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "weekly") {
                SectionCard(title = stringResource(R.string.stats_weekly_title)) {
                    val weekLabel = stringResource(R.string.stats_week_of)
                    val resources = LocalContext.current.resources
                    BarChart(
                        bars = stats.weeklyCounts.map {
                            BarDatum(
                                label = Fmt.shortDayMonth(it.weekStart),
                                value = it.count.toDouble(),
                                detail = weekLabel.format(Fmt.shortDayMonth(it.weekStart)),
                            )
                        },
                        valueFormatter = { v -> resources.getQuantityString(R.plurals.workouts_count, v.toInt(), v.toInt()) },
                        target = state.weekTarget.toDouble().takeIf { it > 0 },
                        targetLabel = stringResource(R.string.stats_target),
                        description = stringResource(R.string.stats_weekly_title),
                    )
                }
            }
            item(key = "bodyweight") { BodyWeightCard(state.bodyWeight, unit, onOpenBodyWeight) }
            item(key = "exercises-title") {
                Text(stringResource(R.string.stats_exercises_title), style = MaterialTheme.typography.titleMedium)
            }
            if (state.exercises.isEmpty()) {
                item(key = "exercises-empty") {
                    EmptyState(stringResource(R.string.stats_exercises_empty), icon = Icons.AutoMirrored.Filled.ShowChart)
                }
            } else {
                items(state.exercises, key = { it.exerciseId }) { exercise ->
                    SectionCard(onClick = { onOpenExercise(exercise.exerciseId) }) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (exercise.bodyweightOnly) {
                                stringResource(R.string.stats_max_reps, exercise.maxReps)
                            } else {
                                exercise.weightProgression.joinToString(" → ") { Fmt.weight(it, unit) } + " " + Fmt.weightUnitLabel(unit)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            pluralStringResource(R.plurals.workouts_count, exercise.sessionCount, exercise.sessionCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyWeightCard(measurements: List<Measurement>, unit: WeightUnit, onOpen: () -> Unit) {
    SectionCard(title = stringResource(R.string.stats_body_weight), onClick = onOpen) {
        if (measurements.isEmpty()) {
            Text(stringResource(R.string.stats_body_weight_empty), style = MaterialTheme.typography.bodyMedium)
            SecondaryButton(stringResource(R.string.stats_add_weight), onOpen, Modifier.fillMaxWidth())
        } else {
            LineChart(
                points = measurements.map { ChartPoint(it.date.toEpochDay().toDouble(), unit.fromKg(it.value), Fmt.shortDate(it.date)) },
                valueFormatter = { "${Fmt.oneDecimal(it)} ${Fmt.weightUnitLabel(unit)}" },
                description = stringResource(R.string.stats_body_weight),
                height = 160.dp,
            )
        }
    }
}

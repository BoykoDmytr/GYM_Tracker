package com.boykodmytr.gymtracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.StatRow
import com.boykodmytr.gymtracker.ui.components.StatTile
import com.boykodmytr.gymtracker.ui.components.rememberNow
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle
import java.time.Duration
import java.time.LocalDate

@Composable
fun HomeScreen(
    onStartWorkout: (templateId: String) -> Unit,
    onContinueSession: (sessionId: String) -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrograms: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStartWorkout = onStartWorkout,
        onContinueSession = onContinueSession,
        onFinishStale = viewModel::finishStaleSession,
        onOpenSession = onOpenSession,
        onOpenTimer = onOpenTimer,
        onOpenSettings = onOpenSettings,
        onOpenPrograms = onOpenPrograms,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onStartWorkout: (String) -> Unit,
    onContinueSession: (String) -> Unit,
    onFinishStale: (ActiveSessionInfo) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrograms: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Fmt.dayOfWeek(state.today).replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleLarge)
                        Text(
                            Fmt.dayMonthYear(state.today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTimer) { Icon(Icons.Outlined.Timer, stringResource(R.string.home_timer)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.home_settings)) }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val active = state.activeSession
            if (active != null) {
                item(key = "active") { ActiveSessionCard(active, onContinueSession, onFinishStale) }
            } else {
                item(key = "plan") { PlanCard(state.plan, state.today, onStartWorkout, onOpenPrograms) }
            }
            item(key = "stats") {
                StatRow {
                    StatTile(
                        label = stringResource(R.string.home_this_week),
                        value = stringResource(R.string.home_week_value, state.weekDone, state.weekTarget),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.home_streak),
                        value = state.streakWeeks.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.home_total),
                        value = state.totalWorkouts.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.lastWorkout?.let { last ->
                item(key = "last") { LastWorkoutCard(last, state.weightUnit, onOpenSession) }
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(
    info: ActiveSessionInfo,
    onContinue: (String) -> Unit,
    onFinishStale: (ActiveSessionInfo) -> Unit,
) {
    val now by rememberNow()
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            if (info.isStale) {
                stringResource(R.string.home_stale_session, Fmt.dayMonth(info.date))
            } else {
                stringResource(R.string.home_active_session)
            },
            style = MaterialTheme.typography.labelLarge,
        )
        Text(info.name, style = MaterialTheme.typography.headlineMedium)
        if (!info.isStale) {
            Text(
                Fmt.clock(Duration.between(info.startedAt, now)),
                style = MaterialTheme.typography.headlineSmall.merge(NumberTextStyle),
            )
        }
        Text(stringResource(R.string.home_sets_done, info.setsDone), style = MaterialTheme.typography.bodyMedium)
        BigButton(text = stringResource(R.string.home_continue), onClick = { onContinue(info.id) }, icon = Icons.Filled.PlayArrow)
        if (info.isStale) {
            SecondaryButton(
                text = stringResource(R.string.home_finish_stale),
                onClick = { onFinishStale(info) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: TodayPlan,
    today: LocalDate,
    onStartWorkout: (String) -> Unit,
    onOpenPrograms: () -> Unit,
) {
    when (plan) {
        TodayPlan.NoProgram -> SectionCard {
            EmptyState(
                text = stringResource(R.string.home_no_program) + "\n" + stringResource(R.string.home_no_program_hint),
                icon = Icons.Outlined.FitnessCenter,
                action = { SecondaryButton(stringResource(R.string.home_open_programs), onOpenPrograms) },
            )
        }
        TodayPlan.NoWorkouts -> SectionCard {
            EmptyState(
                text = stringResource(R.string.home_no_workouts),
                icon = Icons.Outlined.FitnessCenter,
                action = { SecondaryButton(stringResource(R.string.home_open_programs), onOpenPrograms) },
            )
        }
        is TodayPlan.Training -> SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.home_today_training), style = MaterialTheme.typography.labelLarge)
            TemplateHeadline(plan.template)
            BigButton(
                text = stringResource(R.string.home_start_workout),
                onClick = { onStartWorkout(plan.template.id) },
                icon = Icons.Filled.PlayArrow,
            )
        }
        is TodayPlan.Rest -> SectionCard {
            Text(stringResource(R.string.home_rest_day), style = MaterialTheme.typography.titleMedium)
            NextWorkoutLine(plan.next, plan.nextDate, today)
            SecondaryButton(
                text = stringResource(R.string.home_start_anyway),
                onClick = { onStartWorkout(plan.next.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is TodayPlan.Done -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_done_today), style = MaterialTheme.typography.titleMedium)
            }
            NextWorkoutLine(plan.next, plan.nextDate, today)
        }
    }
}

@Composable
private fun TemplateHeadline(template: WorkoutTemplate) {
    Text(template.name, style = MaterialTheme.typography.headlineMedium)
    Text(
        pluralStringResource(R.plurals.exercises_count, template.exercises.size, template.exercises.size),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NextWorkoutLine(next: WorkoutTemplate, date: LocalDate?, today: LocalDate) {
    Text(stringResource(R.string.home_next_workout), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (date == null) {
        Text(stringResource(R.string.home_no_schedule), style = MaterialTheme.typography.bodyMedium)
        Text(next.name, style = MaterialTheme.typography.titleLarge)
    } else {
        Text(
            stringResource(R.string.home_next_workout_date, Fmt.fullDate(date).replaceFirstChar { it.titlecase() }, next.name),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun LastWorkoutCard(last: SessionSummary, unit: WeightUnit, onOpen: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.home_last_workout), onClick = { onOpen(last.id) }) {
        Text(last.name, style = MaterialTheme.typography.titleLarge)
        val parts = buildList {
            add(Fmt.dayMonth(last.date))
            last.duration?.let { add(Fmt.duration(it)) }
            add(pluralStringResource(R.plurals.sets_count, last.setCount, last.setCount))
            if (last.volumeKg > 0) add(Fmt.volume(last.volumeKg, unit))
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

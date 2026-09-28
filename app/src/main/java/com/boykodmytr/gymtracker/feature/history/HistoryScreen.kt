package com.boykodmytr.gymtracker.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.SessionCard
import com.boykodmytr.gymtracker.ui.format.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.showMonth(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.history_previous_month))
                        }
                        Text(
                            Fmt.monthYear(state.month.atDay(1)),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { viewModel.showMonth(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.history_next_month))
                        }
                    }
                    MonthCalendar(
                        month = state.month,
                        today = state.today,
                        selected = state.selectedDate,
                        statuses = state.statuses,
                        onSelect = viewModel::select,
                    )
                    CalendarLegend()
                    Text(
                        pluralStringResource(R.plurals.workouts_count, state.monthSessions.size, state.monthSessions.size) +
                            " · " + stringResource(R.string.history_missed_count, state.missedCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val selected = state.selectedDate
            item(key = "list-title") {
                Text(
                    if (selected != null) Fmt.fullDate(selected).replaceFirstChar { it.titlecase() } else stringResource(R.string.history_month_workouts),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val sessions = state.visibleSessions
            if (sessions.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        text = if (selected != null && state.statuses[selected] == DayStatus.MISSED) {
                            stringResource(R.string.history_day_missed)
                        } else {
                            stringResource(R.string.history_empty)
                        },
                        icon = Icons.Outlined.CalendarMonth,
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(session, state.weightUnit, onClick = { onOpenSession(session.id) })
                }
            }
        }
    }
}

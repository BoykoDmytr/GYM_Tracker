package com.boykodmytr.gymtracker.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.logic.ScheduleCalculator
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class DayStatus { COMPLETED, MISSED, PLANNED, NONE }

data class HistoryUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate? = null,
    val statuses: Map<LocalDate, DayStatus> = emptyMap(),
    val monthSessions: List<SessionSummary> = emptyList(),
    val missedCount: Int = 0,
    val weightUnit: WeightUnit = WeightUnit.KG,
) {
    val visibleSessions: List<SessionSummary>
        get() = selectedDate?.let { date -> monthSessions.filter { it.date == date } } ?: monthSessions
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now(clock))
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val sessions = month.flatMapLatest { m ->
        workoutRepository.observeCompletedSummaries(m.atDay(1), m.atEndOfMonth())
    }

    val state: StateFlow<HistoryUiState> = combine(month, selectedDate, sessions, settingsRepository.settings) { m, selected, list, settings ->
        val today = LocalDate.now(clock)
        val statuses = dayStatuses(m, today, list, settings)
        HistoryUiState(
            loading = false,
            month = m,
            today = today,
            selectedDate = selected?.takeIf { YearMonth.from(it) == m },
            statuses = statuses,
            monthSessions = list,
            missedCount = statuses.values.count { it == DayStatus.MISSED },
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun showMonth(offset: Long) {
        month.update { it.plusMonths(offset) }
        selectedDate.value = null
    }

    fun select(date: LocalDate) {
        selectedDate.update { if (it == date) null else date }
    }

    companion object {
        internal fun dayStatuses(
            month: YearMonth,
            today: LocalDate,
            sessions: List<SessionSummary>,
            settings: AppSettings,
        ): Map<LocalDate, DayStatus> {
            val completed = sessions.map { it.date }.toSet()
            val monthStart = month.atDay(1)
            val monthEndExclusive = month.atEndOfMonth().plusDays(1)
            val trackingStart = maxOf(settings.trackingStartDate ?: today, monthStart)
            val missed = ScheduleCalculator.missedDates(trackingStart, minOf(today, monthEndExclusive), settings.trainingDays, completed).toSet()
            return generateSequence(monthStart) { it.plusDays(1) }
                .takeWhile { it.isBefore(monthEndExclusive) }
                .associateWith { date ->
                    when {
                        date in completed -> DayStatus.COMPLETED
                        date in missed -> DayStatus.MISSED
                        !date.isBefore(today) && ScheduleCalculator.isTrainingDay(date, settings.trainingDays) -> DayStatus.PLANNED
                        else -> DayStatus.NONE
                    }
                }
        }
    }
}

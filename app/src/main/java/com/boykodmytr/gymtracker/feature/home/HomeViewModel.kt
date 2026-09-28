package com.boykodmytr.gymtracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.logic.ScheduleCalculator
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

sealed interface TodayPlan {
    data object NoProgram : TodayPlan
    data object NoWorkouts : TodayPlan
    data class Training(val template: WorkoutTemplate) : TodayPlan
    data class Done(val next: WorkoutTemplate, val nextDate: LocalDate?) : TodayPlan
    data class Rest(val next: WorkoutTemplate, val nextDate: LocalDate?) : TodayPlan
}

data class ActiveSessionInfo(
    val id: String,
    val name: String,
    val date: LocalDate,
    val startedAt: Instant,
    val setsDone: Int,
    val lastActivityAt: Instant,
    /** Nothing logged for hours: most likely the user forgot to press "finish". */
    val isStale: Boolean,
)

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val activeSession: ActiveSessionInfo? = null,
    val plan: TodayPlan = TodayPlan.NoProgram,
    val weekDone: Int = 0,
    val weekTarget: Int = 0,
    val streakWeeks: Int = 0,
    val totalWorkouts: Int = 0,
    val lastWorkout: SessionSummary? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    programRepository: ProgramRepository,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val programWithLast = programRepository.observeActiveProgram().flatMapLatest { program ->
        if (program == null) {
            flowOf(null to null)
        } else {
            workoutRepository.observeLastCompletedTemplateId(program.id).map { program to it }
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        today,
        settingsRepository.settings,
        programWithLast,
        workoutRepository.observeInProgressSession(),
        workoutRepository.observeAllCompletedSummaries(),
    ) { today, settings, (program, lastTemplateId), inProgress, completed ->
        val now = clock.instant()
        val weekStart = ScheduleCalculator.weekStart(today)
        HomeUiState(
            loading = false,
            today = today,
            activeSession = inProgress?.let {
                ActiveSessionInfo(
                    id = it.id,
                    name = it.name,
                    date = it.date,
                    startedAt = it.startedAt,
                    setsDone = it.totalSets,
                    lastActivityAt = it.lastActivityAt,
                    isStale = Duration.between(it.lastActivityAt, now) > STALE_AFTER,
                )
            },
            plan = plan(program, lastTemplateId, today, settings.trainingDays, completed),
            weekDone = completed.count { !it.date.isBefore(weekStart) },
            weekTarget = settings.trainingDaysPerWeek,
            streakWeeks = ScheduleCalculator.weeklyStreak(completed.map { it.date }, today, settings.trainingDaysPerWeek),
            totalWorkouts = completed.size,
            lastWorkout = completed.firstOrNull(),
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun plan(
        program: Program?,
        lastTemplateId: String?,
        today: LocalDate,
        days: List<TrainingDay>,
        completed: List<SessionSummary>,
    ): TodayPlan {
        if (program == null) return TodayPlan.NoProgram
        val next = ScheduleCalculator.nextTemplate(program.workouts, lastTemplateId) ?: return TodayPlan.NoWorkouts
        return when {
            completed.any { it.date == today } -> TodayPlan.Done(next, ScheduleCalculator.nextTrainingDate(today.plusDays(1), days))
            ScheduleCalculator.isTrainingDay(today, days) -> TodayPlan.Training(next)
            else -> TodayPlan.Rest(next, ScheduleCalculator.nextTrainingDate(today, days))
        }
    }

    /** Closes a forgotten session at the time of its last logged set, so the duration stays honest. */
    fun finishStaleSession(info: ActiveSessionInfo) {
        viewModelScope.launch {
            if (info.setsDone == 0) {
                workoutRepository.deleteSession(info.id)
            } else {
                workoutRepository.finishSession(info.id, info.lastActivityAt)
            }
        }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(3)
    }
}

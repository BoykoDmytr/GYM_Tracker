package com.boykodmytr.gymtracker.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.logic.ProgressMath
import com.boykodmytr.gymtracker.domain.logic.StatsCalculator
import com.boykodmytr.gymtracker.domain.logic.TrainingStats
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ExerciseProgressSummary(
    val exerciseId: String,
    val name: String,
    val sessionCount: Int,
    val lastPerformedAt: Instant,
    val weightProgression: List<Double>,
    val bodyweightOnly: Boolean,
    val maxReps: Int,
)

data class StatsUiState(
    val loading: Boolean = true,
    val stats: TrainingStats? = null,
    val weekTarget: Int = 0,
    val bodyWeight: List<Measurement> = emptyList(),
    val exercises: List<ExerciseProgressSummary> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    bodyRepository: BodyRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(
        workoutRepository.observeAllCompletedSummaries(),
        workoutRepository.observeExercisesWithHistory(),
        workoutRepository.observeAllExerciseRecords(),
        bodyRepository.observeMeasurements(BuiltInMeasurementTypes.WEIGHT),
        settingsRepository.settings,
    ) { summaries, exercises, records, weight, settings ->
        val recordsByExercise = records.groupBy { it.exerciseId }
        StatsUiState(
            loading = false,
            stats = StatsCalculator.compute(summaries, LocalDate.now(clock), settings.trainingDays, settings.trackingStartDate),
            weekTarget = settings.trainingDaysPerWeek,
            bodyWeight = weight.sortedBy { it.date },
            exercises = exercises.map { entry ->
                val sessions = ProgressMath.perSession(recordsByExercise[entry.exerciseId].orEmpty())
                ExerciseProgressSummary(
                    exerciseId = entry.exerciseId,
                    name = entry.name,
                    sessionCount = entry.sessionCount,
                    lastPerformedAt = entry.lastPerformedAt,
                    weightProgression = ProgressMath.weightProgression(sessions, limit = 5),
                    bodyweightOnly = ProgressMath.isBodyweightOnly(sessions),
                    maxReps = sessions.maxOfOrNull { it.maxReps } ?: 0,
                )
            },
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

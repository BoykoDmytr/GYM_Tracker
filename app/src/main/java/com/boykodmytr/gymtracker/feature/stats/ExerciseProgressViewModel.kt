package com.boykodmytr.gymtracker.feature.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.logic.ExerciseSessionProgress
import com.boykodmytr.gymtracker.domain.logic.PersonalRecords
import com.boykodmytr.gymtracker.domain.logic.ProgressMath
import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.ui.navigation.ExerciseProgressRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ProgressMetric { MAX_WEIGHT, ONE_REP_MAX, VOLUME, REPS }

data class ExerciseProgressUiState(
    val loading: Boolean = true,
    val exerciseId: String = "",
    val name: String = "",
    val metric: ProgressMetric = ProgressMetric.MAX_WEIGHT,
    val availableMetrics: List<ProgressMetric> = ProgressMetric.entries,
    val sessions: List<ExerciseSessionProgress> = emptyList(),
    val setsBySession: Map<String, List<ExerciseSetRecord>> = emptyMap(),
    val records: PersonalRecords? = null,
    val progression: List<Double> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class ExerciseProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseProgressRoute>().exerciseId
    private val selectedMetric = MutableStateFlow<ProgressMetric?>(null)

    val state: StateFlow<ExerciseProgressUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        workoutRepository.observeExerciseRecords(exerciseId),
        settingsRepository.settings,
        selectedMetric,
    ) { details, records, settings, selected ->
        val sessions = ProgressMath.perSession(records)
        val bodyweight = ProgressMath.isBodyweightOnly(sessions)
        val available = if (bodyweight) listOf(ProgressMetric.REPS) else ProgressMetric.entries
        ExerciseProgressUiState(
            loading = false,
            exerciseId = exerciseId,
            name = details?.exercise?.name.orEmpty(),
            metric = selected?.takeIf { it in available } ?: available.first(),
            availableMetrics = available,
            sessions = sessions,
            setsBySession = records.groupBy { it.sessionId },
            records = ProgressMath.personalRecords(sessions),
            progression = ProgressMath.weightProgression(sessions),
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseProgressUiState())

    fun selectMetric(metric: ProgressMetric) {
        selectedMetric.value = metric
    }
}

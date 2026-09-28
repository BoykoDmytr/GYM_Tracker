package com.boykodmytr.gymtracker.feature.workout.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.logic.ProgressMath
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.ui.navigation.WorkoutSummaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordKind { WEIGHT, ONE_REP_MAX }

data class WorkoutSummaryUiState(
    val loading: Boolean = true,
    val session: WorkoutSession? = null,
    /** Personal records set in this workout, keyed by session-exercise id. */
    val records: Map<String, Set<RecordKind>> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<WorkoutSummaryRoute>().sessionId
    private val session = workoutRepository.observeSession(sessionId)

    private val records = session
        .map { it?.exercises.orEmpty().filter { e -> e.sets.isNotEmpty() } }
        .mapLatest { exercises ->
            exercises.associate { exercise ->
                val history = workoutRepository.observeExerciseRecords(exercise.exerciseId).first()
                    .filter { it.sessionId != sessionId }
                val kinds = mutableSetOf<RecordKind>()
                if (history.isNotEmpty()) {
                    val bestWeight = exercise.sets.maxOf { it.weightKg }
                    val best1Rm = exercise.sets.maxOf { ProgressMath.estimatedOneRepMax(it.weightKg, it.reps) }
                    if (bestWeight > 0 && bestWeight > history.maxOf { it.weightKg }) kinds += RecordKind.WEIGHT
                    if (best1Rm > 0 && best1Rm > history.maxOf { ProgressMath.estimatedOneRepMax(it.weightKg, it.reps) } + 1e-6) {
                        kinds += RecordKind.ONE_REP_MAX
                    }
                }
                exercise.id to kinds.toSet()
            }
        }
        .onStart { emit(emptyMap()) }

    val state: StateFlow<WorkoutSummaryUiState> = combine(session, records, settingsRepository.settings) { s, r, settings ->
        WorkoutSummaryUiState(loading = false, session = s, records = r, weightUnit = settings.weightUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    fun saveNotes(notes: String) {
        viewModelScope.launch { workoutRepository.updateSessionNotes(sessionId, notes) }
    }
}

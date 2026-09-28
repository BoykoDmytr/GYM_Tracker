package com.boykodmytr.gymtracker.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.ui.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: WorkoutSession? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SessionDetailRoute>().sessionId

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    val state: StateFlow<SessionDetailUiState> = combine(
        workoutRepository.observeSession(sessionId),
        settingsRepository.settings,
    ) { session, settings ->
        SessionDetailUiState(loading = false, session = session, weightUnit = settings.weightUnit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun updateSet(setId: String, input: SetInput) {
        viewModelScope.launch { workoutRepository.updateSet(setId, input) }
    }

    fun deleteSet(setId: String) {
        viewModelScope.launch { workoutRepository.deleteSet(setId) }
    }

    fun deleteSession() {
        viewModelScope.launch {
            workoutRepository.deleteSession(sessionId)
            _deleted.send(Unit)
        }
    }
}

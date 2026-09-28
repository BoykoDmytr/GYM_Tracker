package com.boykodmytr.gymtracker.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.ui.navigation.MeasurementDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MeasurementDetailUiState(
    val loading: Boolean = true,
    val type: MeasurementType? = null,
    val allTypes: List<MeasurementType> = emptyList(),
    /** Newest first. */
    val measurements: List<Measurement> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class MeasurementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bodyRepository: BodyRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val typeId = savedStateHandle.toRoute<MeasurementDetailRoute>().typeId

    val state: StateFlow<MeasurementDetailUiState> = combine(
        bodyRepository.observeMeasurementTypes(),
        bodyRepository.observeMeasurements(typeId),
        settingsRepository.settings,
    ) { types, measurements, settings ->
        MeasurementDetailUiState(
            loading = false,
            type = types.firstOrNull { it.id == typeId },
            allTypes = types,
            measurements = measurements,
            weightUnit = settings.weightUnit,
            today = LocalDate.now(clock),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUiState())

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val typeDeleted: Flow<Unit> = _deleted.receiveAsFlow()

    fun add(date: LocalDate, value: Double) {
        viewModelScope.launch { bodyRepository.addMeasurement(typeId, value, date) }
    }

    fun update(measurement: Measurement) {
        viewModelScope.launch { bodyRepository.updateMeasurement(measurement) }
    }

    fun delete(id: String) {
        viewModelScope.launch { bodyRepository.deleteMeasurement(id) }
    }

    fun deleteType() {
        viewModelScope.launch {
            bodyRepository.deleteMeasurementType(typeId)
            _deleted.send(Unit)
        }
    }
}

package com.boykodmytr.gymtracker.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.UserProfile
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

data class MeasurementRow(
    val type: MeasurementType,
    val latest: Measurement?,
    /** Change since the previous entry of the same type. */
    val delta: Double?,
)

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val age: Int? = null,
    val rows: List<MeasurementRow> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val today: LocalDate = LocalDate.now(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        bodyRepository.observeProfile(),
        bodyRepository.observeMeasurementTypes(),
        bodyRepository.observeAllMeasurements(),
        settingsRepository.settings,
    ) { profile, types, measurements, settings ->
        val today = LocalDate.now(clock)
        val byType = measurements.groupBy { it.typeId }
        ProfileUiState(
            loading = false,
            profile = profile,
            age = profile.birthDate?.let { Period.between(it, today).years }?.takeIf { it in 0..130 },
            rows = types.map { type ->
                val history = byType[type.id].orEmpty() // newest first
                val latest = history.getOrNull(0)
                val previous = history.getOrNull(1)
                MeasurementRow(type, latest, if (latest != null && previous != null) latest.value - previous.value else null)
            },
            weightUnit = settings.weightUnit,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { bodyRepository.saveProfile(profile) }
    }

    /** Values are already in canonical units (kg / cm / %). */
    fun addMeasurements(date: LocalDate, values: Map<String, Double>) {
        viewModelScope.launch { values.forEach { (typeId, value) -> bodyRepository.addMeasurement(typeId, value, date) } }
    }

    fun addType(name: String, unit: MeasurementUnit) {
        viewModelScope.launch { bodyRepository.addMeasurementType(name, unit) }
    }
}

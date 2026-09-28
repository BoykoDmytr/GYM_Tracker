package com.boykodmytr.gymtracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.ThemeMode
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleDay(day: DayOfWeek, enabled: Boolean) = updateDays { days ->
        if (enabled) {
            days + TrainingDay(day, days.firstOrNull()?.time ?: LocalTime.of(18, 0))
        } else {
            days.filterNot { it.dayOfWeek == day }
        }
    }

    fun setDayTime(day: DayOfWeek, time: LocalTime) = updateDays { days ->
        days.map { if (it.dayOfWeek == day) it.copy(time = time) else it }
    }

    fun setReminders(enabled: Boolean) = launch { repository.setRemindersEnabled(enabled) }
    fun setDefaultRest(seconds: Int) = launch { repository.setDefaultRestSeconds(seconds) }
    fun setAutoStartRest(enabled: Boolean) = launch { repository.setAutoStartRest(enabled) }
    fun setWeightUnit(unit: WeightUnit) = launch { repository.setWeightUnit(unit) }
    fun setTheme(mode: ThemeMode) = launch { repository.setThemeMode(mode) }
    fun setSound(enabled: Boolean) = launch { repository.setTimerSound(enabled) }
    fun setVibration(enabled: Boolean) = launch { repository.setTimerVibration(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = launch { repository.setKeepScreenOn(enabled) }

    private fun updateDays(transform: (List<TrainingDay>) -> List<TrainingDay>) = launch {
        val current = settings.value?.trainingDays ?: return@launch
        repository.setTrainingDays(transform(current).distinctBy { it.dayOfWeek }.sortedBy { it.dayOfWeek })
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

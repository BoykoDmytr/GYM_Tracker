package com.boykodmytr.gymtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boykodmytr.gymtracker.domain.model.ThemeMode
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Null until settings are read, so the first frame is drawn with the right theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingSessionId = MutableStateFlow<String?>(null)

    /** Session to open when the app is launched from the workout notification. */
    val pendingSessionId: StateFlow<String?> = _pendingSessionId.asStateFlow()

    fun openSession(sessionId: String?) {
        if (sessionId != null) _pendingSessionId.value = sessionId
    }

    fun consumePendingSession() {
        _pendingSessionId.value = null
    }

    suspend fun shouldAskNotificationPermission(): Boolean = !settingsRepository.wasNotificationPermissionRequested()

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { settingsRepository.markNotificationPermissionRequested() }
    }
}

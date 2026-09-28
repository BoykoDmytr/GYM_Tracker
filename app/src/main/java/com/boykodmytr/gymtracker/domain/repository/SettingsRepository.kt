package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.ThemeMode
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setTrainingDays(days: List<TrainingDay>)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setDefaultRestSeconds(seconds: Int)
    suspend fun setAutoStartRest(enabled: Boolean)
    suspend fun setWeightUnit(unit: WeightUnit)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setTimerSound(enabled: Boolean)
    suspend fun setTimerVibration(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun ensureTrackingStartDate(date: LocalDate)

    suspend fun seedVersion(): Int
    suspend fun setSeedVersion(version: Int)
    suspend fun wasNotificationPermissionRequested(): Boolean
    suspend fun markNotificationPermissionRequested()
}

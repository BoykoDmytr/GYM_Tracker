package com.boykodmytr.gymtracker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.ThemeMode
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Settings live in DataStore rather than Room: they are small, per-device and need no queries. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val trainingDays = stringPreferencesKey("training_days")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val defaultRest = intPreferencesKey("default_rest_seconds")
        val autoStartRest = booleanPreferencesKey("auto_start_rest")
        val weightUnit = stringPreferencesKey("weight_unit")
        val themeMode = stringPreferencesKey("theme_mode")
        val timerSound = booleanPreferencesKey("timer_sound")
        val timerVibration = booleanPreferencesKey("timer_vibration")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val trackingStart = longPreferencesKey("tracking_start_epoch_day")
        val seedVersion = intPreferencesKey("seed_version")
        val notificationPermissionRequested = booleanPreferencesKey("notification_permission_requested")
    }

    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            trainingDays = prefs[Keys.trainingDays]?.let(TrainingDaysCodec::decode) ?: defaults.trainingDays,
            remindersEnabled = prefs[Keys.remindersEnabled] ?: defaults.remindersEnabled,
            defaultRestSeconds = prefs[Keys.defaultRest] ?: defaults.defaultRestSeconds,
            autoStartRest = prefs[Keys.autoStartRest] ?: defaults.autoStartRest,
            weightUnit = prefs[Keys.weightUnit].toEnum(defaults.weightUnit),
            themeMode = prefs[Keys.themeMode].toEnum(defaults.themeMode),
            timerSound = prefs[Keys.timerSound] ?: defaults.timerSound,
            timerVibration = prefs[Keys.timerVibration] ?: defaults.timerVibration,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            trackingStartDate = prefs[Keys.trackingStart]?.let(LocalDate::ofEpochDay),
        )
    }.distinctUntilChanged()

    override suspend fun setTrainingDays(days: List<TrainingDay>) = set(Keys.trainingDays, TrainingDaysCodec.encode(days))
    override suspend fun setRemindersEnabled(enabled: Boolean) = set(Keys.remindersEnabled, enabled)
    override suspend fun setDefaultRestSeconds(seconds: Int) = set(Keys.defaultRest, seconds.coerceIn(5, 3600))
    override suspend fun setAutoStartRest(enabled: Boolean) = set(Keys.autoStartRest, enabled)
    override suspend fun setWeightUnit(unit: WeightUnit) = set(Keys.weightUnit, unit.name)
    override suspend fun setThemeMode(mode: ThemeMode) = set(Keys.themeMode, mode.name)
    override suspend fun setTimerSound(enabled: Boolean) = set(Keys.timerSound, enabled)
    override suspend fun setTimerVibration(enabled: Boolean) = set(Keys.timerVibration, enabled)
    override suspend fun setKeepScreenOn(enabled: Boolean) = set(Keys.keepScreenOn, enabled)

    override suspend fun ensureTrackingStartDate(date: LocalDate) {
        dataStore.edit { prefs -> if (prefs[Keys.trackingStart] == null) prefs[Keys.trackingStart] = date.toEpochDay() }
    }

    override suspend fun seedVersion(): Int = dataStore.data.first()[Keys.seedVersion] ?: 0
    override suspend fun setSeedVersion(version: Int) = set(Keys.seedVersion, version)

    override suspend fun wasNotificationPermissionRequested(): Boolean =
        dataStore.data.first()[Keys.notificationPermissionRequested] ?: false

    override suspend fun markNotificationPermissionRequested() = set(Keys.notificationPermissionRequested, true)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } } ?: default
}

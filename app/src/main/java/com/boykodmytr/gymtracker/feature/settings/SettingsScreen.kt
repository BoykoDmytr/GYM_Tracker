package com.boykodmytr.gymtracker.feature.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.core.notifications.NotificationChannels
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.ThemeMode
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.components.AppTimePickerDialog
import com.boykodmytr.gymtracker.ui.format.Fmt
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Permissions change in system settings; re-read them whenever the screen comes back.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeCount++ }
    val notificationsAllowed = remember(resumeCount) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAlarmsAllowed = remember(resumeCount) { context.canScheduleExactAlarms() }
    var editingTimeFor by rememberSaveable { mutableStateOf<DayOfWeek?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!notificationsAllowed || !exactAlarmsAllowed) {
                PermissionWarnings(context, notificationsAllowed, exactAlarmsAllowed)
            }

            SettingsSection(stringResource(R.string.settings_schedule)) {
                Text(stringResource(R.string.settings_schedule_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DayOfWeek.entries.forEach { day ->
                    val training = s.trainingDays.firstOrNull { it.dayOfWeek == day }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = training != null, onCheckedChange = { viewModel.toggleDay(day, it) })
                        Text(Fmt.dayOfWeekFull(day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (training != null) {
                            TextButton(onClick = { editingTimeFor = day }) { Text(Fmt.time(training.time)) }
                        }
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_reminders),
                    subtitle = stringResource(R.string.settings_reminders_hint),
                    checked = s.remindersEnabled,
                    onCheckedChange = viewModel::setReminders,
                )
            }

            SettingsSection(stringResource(R.string.settings_rest)) {
                Text(stringResource(R.string.settings_default_rest), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.REST_PRESETS_SECONDS.forEach { seconds ->
                        FilterChip(
                            selected = s.defaultRestSeconds == seconds,
                            onClick = { viewModel.setDefaultRest(seconds) },
                            label = { Text(Fmt.restLabel(seconds)) },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.settings_auto_rest), s.autoStartRest, viewModel::setAutoStartRest, stringResource(R.string.settings_auto_rest_hint))
                SwitchRow(stringResource(R.string.settings_sound), s.timerSound, viewModel::setSound)
                SwitchRow(stringResource(R.string.settings_vibration), s.timerVibration, viewModel::setVibration)
                ClickRow(
                    title = stringResource(R.string.settings_timer_channel),
                    subtitle = stringResource(R.string.settings_timer_channel_hint),
                    onClick = { context.openTimerChannelSettings() },
                )
            }

            SettingsSection(stringResource(R.string.settings_workout)) {
                SwitchRow(stringResource(R.string.settings_keep_screen_on), s.keepScreenOn, viewModel::setKeepScreenOn, stringResource(R.string.settings_keep_screen_on_hint))
            }

            SettingsSection(stringResource(R.string.settings_units)) {
                SegmentedChoice(
                    options = WeightUnit.entries,
                    selected = s.weightUnit,
                    label = { stringResource(if (it == WeightUnit.KG) R.string.settings_unit_kg else R.string.settings_unit_lb) },
                    onSelect = viewModel::setWeightUnit,
                )
            }

            SettingsSection(stringResource(R.string.settings_theme)) {
                SegmentedChoice(
                    options = ThemeMode.entries,
                    selected = s.themeMode,
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            },
                        )
                    },
                    onSelect = viewModel::setTheme,
                )
            }

            SettingsSection(stringResource(R.string.settings_data)) {
                ClickRow(stringResource(R.string.settings_export), onOpenExport, stringResource(R.string.settings_export_hint))
                ClickRow(stringResource(R.string.settings_import), onOpenImport, stringResource(R.string.settings_import_hint))
            }

            Text(
                stringResource(R.string.settings_version, context.appVersion()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingTimeFor?.let { day ->
        val current = settings?.trainingDays?.firstOrNull { it.dayOfWeek == day } ?: return@let
        AppTimePickerDialog(
            initial = current.time,
            onConfirm = {
                editingTimeFor = null
                viewModel.setDayTime(day, it)
            },
            onDismiss = { editingTimeFor = null },
        )
    }
}

@Composable
private fun PermissionWarnings(context: Context, notificationsAllowed: Boolean, exactAlarmsAllowed: Boolean) {
    SettingsSection(stringResource(R.string.settings_permissions)) {
        if (!notificationsAllowed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                ClickRow(
                    title = stringResource(R.string.settings_notifications_off),
                    subtitle = stringResource(R.string.settings_notifications_off_hint),
                    onClick = { context.openAppNotificationSettings() },
                    emphasized = true,
                )
            }
        }
        if (!exactAlarmsAllowed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                ClickRow(
                    title = stringResource(R.string.settings_exact_off),
                    subtitle = stringResource(R.string.settings_exact_off_hint),
                    onClick = { context.openExactAlarmSettings() },
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

private fun Context.canScheduleExactAlarms(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun Context.openAppNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}

private fun Context.openTimerChannelSettings() {
    startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.TIMER),
    )
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
    }
}

private fun Context.appVersion(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

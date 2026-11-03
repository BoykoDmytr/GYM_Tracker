package com.boykodmytr.gymtracker.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.core.notifications.AlertStatus
import com.boykodmytr.gymtracker.core.notifications.NotificationChannels

/**
 * Explains why the end of a rest may not reach the user with the screen off, with a one-tap fix for
 * each problem. Shown on the workout screen, where it matters.
 */
@Composable
fun AlertProblemsCard(status: AlertStatus, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enableNotifications = rememberEnableNotifications()
    SectionCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
            Text(stringResource(R.string.alert_problem_title), style = MaterialTheme.typography.titleSmall)
        }
        when {
            !status.notificationsAllowed -> Problem(stringResource(R.string.alert_problem_notifications), stringResource(R.string.alert_fix_enable), enableNotifications)
            !status.timerChannelAudible -> Problem(stringResource(R.string.alert_problem_channel), stringResource(R.string.alert_fix_settings)) {
                context.openTimerChannelSettings()
            }
        }
        if (!status.exactAlarmsAllowed) {
            Problem(stringResource(R.string.alert_problem_exact), stringResource(R.string.alert_fix_allow)) { context.openExactAlarmSettings() }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.alert_dismiss)) }
    }
}

@Composable
private fun Problem(text: String, action: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onClick) { Text(action) }
}

/**
 * Turns notifications on: the system dialog while Android still offers it, otherwise (denied twice,
 * or switched off for the whole app) the app's notification settings.
 */
@Composable
fun rememberEnableNotifications(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.openAppNotificationSettings()
    }
    return remember(context, launcher) {
        {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.openAppNotificationSettings()
        }
    }
}

fun Context.openAppNotificationSettings() = startSettings(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
)

fun Context.openTimerChannelSettings() = startSettings(
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.TIMER),
)

fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        startSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
    }
}

/** App info: where phone makers keep autostart and battery restrictions. */
fun Context.openAppDetailsSettings() = startSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))

/** Some phone makers drop individual settings screens; fall back to the app's info page. */
private fun Context.startSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
    }
}

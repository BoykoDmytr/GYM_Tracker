package com.boykodmytr.gymtracker.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the end of a rest can reach the user while the screen is off. */
data class AlertStatus(
    /** App notifications are allowed (POST_NOTIFICATIONS on Android 13+ and the app-level switch). */
    val notificationsAllowed: Boolean,
    /**
     * The "rest timer" channel still alerts: not blocked, not lowered to silent, and with a sound or
     * vibration (the user or the phone maker may have changed any of these).
     */
    val timerChannelAudible: Boolean,
    /** Exact alarms are allowed; without them Android may deliver the alert minutes late. */
    val exactAlarmsAllowed: Boolean,
) {
    val ok: Boolean get() = notificationsAllowed && timerChannelAudible && exactAlarmsAllowed
}

/** One place that answers "will the rest alert work?", for Settings and the workout screen. */
@Singleton
class AlertReadiness @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: WorkoutNotifier,
    private val scheduler: TimerAlarmScheduler,
) {
    fun check(): AlertStatus {
        val channel = NotificationManagerCompat.from(context).getNotificationChannelCompat(NotificationChannels.TIMER)
        return AlertStatus(
            notificationsAllowed = notifier.canPostNotifications(),
            // A missing channel is created on the next app start with the default (loud) settings.
            timerChannelAudible = channel == null ||
                (channel.importance >= NotificationManager.IMPORTANCE_DEFAULT && (channel.sound != null || channel.shouldVibrate())),
            exactAlarmsAllowed = scheduler.canScheduleExact(),
        )
    }
}

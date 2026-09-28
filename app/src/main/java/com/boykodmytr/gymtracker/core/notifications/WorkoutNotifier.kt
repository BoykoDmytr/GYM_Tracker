package com.boykodmytr.gymtracker.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.boykodmytr.gymtracker.MainActivity
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.logic.WorkoutStep
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds all notifications. The ongoing one uses the system chronometer (counting up for the workout,
 * down while resting), so the lock screen shows live time without a foreground service.
 */
@Singleton
class WorkoutNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun canPostNotifications(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    fun showOngoing(session: WorkoutSession, now: Instant) {
        val rest = session.rest?.takeIf { it.endsAt.isAfter(now) }
        val builder = NotificationCompat.Builder(context, NotificationChannels.WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(session.id))
            .setShowWhen(true)
            .setUsesChronometer(true)
        if (rest != null) {
            val next = when (val step = WorkoutFlow.step(session)) {
                is WorkoutStep.PerformSet -> context.getString(
                    R.string.notification_next_set,
                    step.exercise.exerciseName,
                    step.setNumber,
                    step.plannedSets,
                )
                is WorkoutStep.ExerciseDone -> step.next?.exerciseName.orEmpty()
                null -> ""
            }
            builder.setContentTitle(context.getString(R.string.notification_resting))
                .setContentText(next)
                .setWhen(rest.endsAt.toEpochMilli())
                .setChronometerCountDown(true)
        } else {
            builder.setContentTitle(session.name)
                .setContentText(context.getString(R.string.notification_workout_running, session.totalSets))
                .setWhen(session.startedAt.toEpochMilli())
                .setChronometerCountDown(false)
        }
        notify(NotificationIds.ONGOING_WORKOUT, builder.build())
    }

    fun cancelOngoing() = manager.cancel(NotificationIds.ONGOING_WORKOUT)

    fun showTimerFinished(kind: TimerKind, sessionId: String? = null) {
        val (title, text) = when (kind) {
            TimerKind.REST -> R.string.notification_rest_over_title to R.string.notification_rest_over_text
            TimerKind.STANDALONE -> R.string.notification_timer_over_title to R.string.notification_timer_over_text
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.TIMER)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMER_ALERT_TIMEOUT_MS)
            .setContentIntent(openApp(sessionId))
            .build()
        notify(kind.notificationId, notification)
    }

    fun cancelTimerFinished(kind: TimerKind) = manager.cancel(kind.notificationId)

    fun showReminder(title: String, text: String) {
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(null))
            .build()
        notify(NotificationIds.REMINDER, notification)
    }

    private fun openApp(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (sessionId != null) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId) }
        return PendingIntent.getActivity(
            context,
            if (sessionId != null) REQUEST_OPEN_SESSION else REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission") // checked in canPostNotifications()
    private fun notify(id: Int, notification: Notification) {
        if (canPostNotifications()) manager.notify(id, notification)
    }

    private companion object {
        const val REQUEST_OPEN_APP = 0
        const val REQUEST_OPEN_SESSION = 1
        const val TIMER_ALERT_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

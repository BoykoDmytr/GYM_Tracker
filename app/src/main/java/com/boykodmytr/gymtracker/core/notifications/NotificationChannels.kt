package com.boykodmytr.gymtracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.boykodmytr.gymtracker.R

object NotificationChannels {
    /** Silent, ongoing "workout in progress / resting" notification. */
    const val WORKOUT = "workout_ongoing"

    /** Loud alert when a rest or standalone timer ends while the app is in the background. */
    const val TIMER = "timer_alerts"

    const val REMINDERS = "workout_reminders"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(WORKOUT, context.getString(R.string.channel_workout), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_workout_description)
                    setShowBadge(false)
                },
                NotificationChannel(TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_timer_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                },
                NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_reminders_description)
                },
            ),
        )
    }
}

object NotificationIds {
    const val ONGOING_WORKOUT = 1
    const val REST_FINISHED = 2
    const val TIMER_FINISHED = 3
    const val REMINDER = 10
}

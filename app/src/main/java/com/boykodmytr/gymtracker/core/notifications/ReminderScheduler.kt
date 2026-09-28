package com.boykodmytr.gymtracker.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.TrainingDay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** One alarm per training weekday; each firing re-schedules the next week's occurrence. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(settings: AppSettings) {
        cancelAll()
        if (!settings.remindersEnabled) return
        val now = ZonedDateTime.now(clock)
        settings.trainingDays.forEach { day -> schedule(day, nextOccurrence(day, now)) }
    }

    private fun schedule(day: TrainingDay, at: ZonedDateTime) {
        val intent = pendingIntent(day.dayOfWeek)
        val trigger = at.toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, INEXACT_WINDOW_MS, intent)
            }
        } catch (_: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger, INEXACT_WINDOW_MS, intent)
        }
    }

    private fun cancelAll() = DayOfWeek.entries.forEach { alarmManager.cancel(pendingIntent(it)) }

    private fun pendingIntent(day: DayOfWeek): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE + day.value,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val REQUEST_BASE = 2000
        private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

        /** Next moment strictly after [now] that matches the weekday and time of [day]. */
        fun nextOccurrence(day: TrainingDay, now: ZonedDateTime): ZonedDateTime {
            val candidate = now.with(TemporalAdjusters.nextOrSame(day.dayOfWeek))
                .with(day.time)
                .truncatedTo(ChronoUnit.MINUTES)
            return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
        }
    }
}

package com.boykodmytr.gymtracker.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class TimerKind(val requestCode: Int, val notificationId: Int) {
    REST(1001, NotificationIds.REST_FINISHED),
    STANDALONE(1002, NotificationIds.TIMER_FINISHED),

    /** "Check the alert" in Settings: the same path as a rest end, without touching a workout. */
    TEST(1003, NotificationIds.TEST_FINISHED),
}

/** Wrapper around AlarmManager so timers still fire while the app is in the background or killed. */
@Singleton
class TimerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Android 12+ may deny exact alarms (denied by default on 14+). Without them the system is free to
     * deliver the alert late, so Settings explains this and links to the permission screen.
     */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** [sessionId] travels with the alarm so the alert opens the workout even after a process restart. */
    fun schedule(kind: TimerKind, at: Instant, sessionId: String? = null) {
        val intent = pendingIntent(kind, sessionId)
        val trigger = at.toEpochMilli()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
        }
    }

    fun cancel(kind: TimerKind) = alarmManager.cancel(pendingIntent(kind))

    // Extras are not part of PendingIntent identity, so cancel() matches whatever session was scheduled.
    private fun pendingIntent(kind: TimerKind, sessionId: String? = null): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.requestCode,
        Intent(context, TimerAlarmReceiver::class.java)
            .setAction(TimerAlarmReceiver.ACTION_TIMER_FINISHED)
            .putExtra(TimerAlarmReceiver.EXTRA_KIND, kind.name)
            .apply { if (sessionId != null) putExtra(TimerAlarmReceiver.EXTRA_SESSION_ID, sessionId) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

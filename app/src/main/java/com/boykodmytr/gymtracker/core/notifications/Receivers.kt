package com.boykodmytr.gymtracker.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.domain.logic.ScheduleCalculator
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Receivers are not injectable by constructor; they pull dependencies through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    fun timerAlertCoordinator(): TimerAlertCoordinator
    fun reminderScheduler(): ReminderScheduler
    fun workoutNotifier(): WorkoutNotifier
    fun settingsRepository(): SettingsRepository
    fun programRepository(): ProgramRepository
    fun workoutRepository(): WorkoutRepository
    fun clock(): Clock

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

private fun Context.entryPoint(): ReceiverEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.goAsync(context: Context, block: suspend ReceiverEntryPoint.() -> Unit) {
    val pending = goAsync()
    val entryPoint = context.entryPoint()
    entryPoint.applicationScope().launch {
        try {
            entryPoint.block()
        } finally {
            pending.finish()
        }
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_FINISHED) return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> TimerKind.entries.firstOrNull { it.name == name } } ?: return
        context.entryPoint().timerAlertCoordinator().onAlarm(kind)
    }

    companion object {
        const val ACTION_TIMER_FINISHED = "com.boykodmytr.gymtracker.action.TIMER_FINISHED"
        const val EXTRA_KIND = "kind"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        goAsync(context) {
            val settings = settingsRepository().settings.first()
            val today = LocalDate.now(clock())
            val trainedToday = workoutRepository().observeCompletedSummaries(today, today).first().isNotEmpty()
            val inProgress = workoutRepository().observeInProgressSession().first() != null
            if (settings.remindersEnabled && !trainedToday && !inProgress) {
                val program = programRepository().observeActiveProgram().first()
                val last = program?.let { workoutRepository().observeLastCompletedTemplateId(it.id).first() }
                val next = program?.let { ScheduleCalculator.nextTemplate(it.workouts, last) }
                val title = next?.let { context.getString(R.string.reminder_title, it.name) }
                    ?: context.getString(R.string.reminder_title_generic)
                val text = next?.let {
                    context.resources.getQuantityString(R.plurals.reminder_text, it.exercises.size, it.exercises.size)
                } ?: context.getString(R.string.reminder_text_generic)
                workoutNotifier().showReminder(title, text)
            }
            reminderScheduler().reschedule(settings)
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.boykodmytr.gymtracker.action.REMINDER"
    }
}

/**
 * Alarms are wiped on reboot and become wrong after a time or time-zone change, and exact alarms
 * need re-scheduling once the user grants the permission.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        goAsync(context) {
            reminderScheduler().reschedule(settingsRepository().settings.first())
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}

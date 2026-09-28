package com.boykodmytr.gymtracker.core.notifications

import com.boykodmytr.gymtracker.core.common.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure a timer end is announced exactly once.
 *
 * Two paths race: an in-process coroutine (precise while the app is visible, but paused when the CPU
 * sleeps) and an AlarmManager alarm (survives background and process death). Whichever fires first
 * wins; the other is cancelled or ignored. In the foreground the alert is a sound/vibration, in the
 * background a high-priority notification.
 */
@Singleton
class TimerAlertCoordinator @Inject constructor(
    private val scheduler: TimerAlarmScheduler,
    private val alertPlayer: AlertPlayer,
    private val foreground: AppForegroundTracker,
    private val notifier: WorkoutNotifier,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val armed = mutableMapOf<TimerKind, Instant>()
    private val jobs = mutableMapOf<TimerKind, Job>()
    private val lastFired = mutableMapOf<TimerKind, Instant>()

    /** End time that was already announced; re-arming it (e.g. a stale re-sync) must not alert twice. */
    private val announcedEnd = mutableMapOf<TimerKind, Instant>()

    private val _fired = MutableSharedFlow<TimerKind>(extraBufferCapacity = 8)
    val fired: SharedFlow<TimerKind> = _fired.asSharedFlow()

    @Synchronized
    fun arm(kind: TimerKind, endsAt: Instant) {
        if (armed[kind] == endsAt || announcedEnd[kind] == endsAt) return
        disarm(kind)
        val delayMillis = Duration.between(clock.instant(), endsAt).toMillis()
        if (delayMillis <= 0) return
        notifier.cancelTimerFinished(kind)
        armed[kind] = endsAt
        scheduler.schedule(kind, endsAt)
        jobs[kind] = scope.launch {
            delay(delayMillis)
            fire(kind, fromAlarm = false)
        }
    }

    @Synchronized
    fun disarm(kind: TimerKind) {
        armed.remove(kind)
        jobs.remove(kind)?.cancel()
        scheduler.cancel(kind)
    }

    fun onAlarm(kind: TimerKind) = fire(kind, fromAlarm = true)

    @Synchronized
    private fun fire(kind: TimerKind, fromAlarm: Boolean) {
        val now = clock.instant()
        val endsAt = armed.remove(kind)
        val wasArmed = endsAt != null
        if (!wasArmed) {
            // Already announced, or disarmed. An alarm arriving in a fresh process (app was killed)
            // is the exception: nobody announced it yet.
            val recentlyFired = lastFired[kind]?.let { Duration.between(it, now) < DEDUPE_WINDOW } == true
            if (!fromAlarm || recentlyFired) return
        }
        if (fromAlarm) jobs.remove(kind)?.cancel() else scheduler.cancel(kind).also { jobs.remove(kind) }
        lastFired[kind] = now
        if (endsAt != null) announcedEnd[kind] = endsAt
        if (foreground.isInForeground) alertPlayer.play() else notifier.showTimerFinished(kind)
        _fired.tryEmit(kind)
    }

    private companion object {
        val DEDUPE_WINDOW: Duration = Duration.ofSeconds(10)
    }
}

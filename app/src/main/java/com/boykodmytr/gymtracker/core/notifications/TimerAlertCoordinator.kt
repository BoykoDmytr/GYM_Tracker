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
    private val sessions = mutableMapOf<TimerKind, String>()
    private val jobs = mutableMapOf<TimerKind, Job>()
    private val lastFired = mutableMapOf<TimerKind, Instant>()

    /** End time that was already announced; re-arming it (e.g. a stale re-sync) must not alert twice. */
    private val announcedEnd = mutableMapOf<TimerKind, Instant>()

    private val _fired = MutableSharedFlow<TimerKind>(extraBufferCapacity = 8)
    val fired: SharedFlow<TimerKind> = _fired.asSharedFlow()

    /** [sessionId] is the workout the alert should open when tapped. */
    @Synchronized
    fun arm(kind: TimerKind, endsAt: Instant, sessionId: String? = null) {
        if (armed[kind] == endsAt || announcedEnd[kind] == endsAt) return
        disarm(kind)
        val delayMillis = Duration.between(clock.instant(), endsAt).toMillis()
        if (delayMillis <= 0) return
        notifier.cancelTimerFinished(kind)
        armed[kind] = endsAt
        if (sessionId != null) sessions[kind] = sessionId
        scheduler.schedule(kind, endsAt, sessionId)
        jobs[kind] = scope.launch {
            delay(delayMillis)
            fire(kind, fromAlarm = false)
        }
    }

    @Synchronized
    fun disarm(kind: TimerKind) {
        armed.remove(kind)
        sessions.remove(kind)
        jobs.remove(kind)?.cancel()
        scheduler.cancel(kind)
    }

    /**
     * The stored end is already in the past. If it is the end that is armed, its alert is being
     * delivered right now and stays. If a different end is armed, the user cut the rest short (e.g.
     * a shorter preset than the time already rested): the old alert would come late, so drop it.
     */
    @Synchronized
    fun dropStale(kind: TimerKind, endsAt: Instant) {
        val armedEnd = armed[kind] ?: return
        if (armedEnd != endsAt) disarm(kind)
    }

    fun onAlarm(kind: TimerKind, sessionId: String? = null) = fire(kind, fromAlarm = true, alarmSessionId = sessionId)

    @Synchronized
    private fun fire(kind: TimerKind, fromAlarm: Boolean, alarmSessionId: String? = null) {
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
        val sessionId = sessions.remove(kind) ?: alarmSessionId
        lastFired[kind] = now
        if (endsAt != null) announcedEnd[kind] = endsAt
        if (foreground.isInForeground) alertPlayer.play() else notifier.showTimerFinished(kind, sessionId)
        _fired.tryEmit(kind)
    }

    private companion object {
        val DEDUPE_WINDOW: Duration = Duration.ofSeconds(10)
    }
}

package com.boykodmytr.gymtracker.core.timer

import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.core.notifications.TimerAlertCoordinator
import com.boykodmytr.gymtracker.core.notifications.TimerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class TimerMode { COUNTDOWN, STOPWATCH }

/**
 * State of the general-purpose timer. Running timers are stored as absolute end/start instants, so
 * the displayed time is always "now minus start", never an accumulated tick count that drifts.
 */
data class StandaloneTimerState(
    val mode: TimerMode = TimerMode.COUNTDOWN,
    val countdownSeconds: Int = 60,
    val countdownEndsAt: Instant? = null,
    val countdownPausedMillis: Long? = null,
    val stopwatchStartedAt: Instant? = null,
    val stopwatchAccumulatedMillis: Long = 0,
) {
    val countdownRunning: Boolean get() = countdownEndsAt != null
    val countdownPaused: Boolean get() = countdownPausedMillis != null
    val stopwatchRunning: Boolean get() = stopwatchStartedAt != null

    fun countdownRemainingMillis(now: Instant): Long = when {
        countdownEndsAt != null -> Duration.between(now, countdownEndsAt).toMillis().coerceAtLeast(0)
        countdownPausedMillis != null -> countdownPausedMillis
        else -> countdownSeconds * 1000L
    }

    fun stopwatchElapsedMillis(now: Instant): Long =
        stopwatchAccumulatedMillis + (stopwatchStartedAt?.let { Duration.between(it, now).toMillis() } ?: 0)
}

/** App-scoped so the timer keeps running while the user navigates elsewhere. */
@Singleton
class StandaloneTimer @Inject constructor(
    private val coordinator: TimerAlertCoordinator,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StandaloneTimerState())
    val state: StateFlow<StandaloneTimerState> = _state.asStateFlow()

    init {
        scope.launch {
            coordinator.fired.filter { it == TimerKind.STANDALONE }.collect {
                _state.update { it.copy(countdownEndsAt = null, countdownPausedMillis = null) }
            }
        }
    }

    fun setMode(mode: TimerMode) = _state.update { it.copy(mode = mode) }

    fun setCountdownSeconds(seconds: Int) {
        if (_state.value.countdownRunning) return
        _state.update { it.copy(countdownSeconds = seconds.coerceIn(5, 99 * 60), countdownPausedMillis = null) }
    }

    fun startCountdown() {
        val current = _state.value
        if (current.countdownRunning) return
        val millis = current.countdownPausedMillis ?: (current.countdownSeconds * 1000L)
        val endsAt = clock.instant().plusMillis(millis)
        _state.update { it.copy(countdownEndsAt = endsAt, countdownPausedMillis = null) }
        coordinator.arm(TimerKind.STANDALONE, endsAt)
    }

    fun pauseCountdown() {
        val endsAt = _state.value.countdownEndsAt ?: return
        coordinator.disarm(TimerKind.STANDALONE)
        val remaining = Duration.between(clock.instant(), endsAt).toMillis().coerceAtLeast(0)
        _state.update { it.copy(countdownEndsAt = null, countdownPausedMillis = remaining) }
    }

    fun resetCountdown() {
        coordinator.disarm(TimerKind.STANDALONE)
        _state.update { it.copy(countdownEndsAt = null, countdownPausedMillis = null) }
    }

    fun startStopwatch() {
        if (_state.value.stopwatchRunning) return
        _state.update { it.copy(stopwatchStartedAt = clock.instant()) }
    }

    fun pauseStopwatch() {
        val now = clock.instant()
        _state.update { it.copy(stopwatchAccumulatedMillis = it.stopwatchElapsedMillis(now), stopwatchStartedAt = null) }
    }

    fun resetStopwatch() = _state.update { it.copy(stopwatchStartedAt = null, stopwatchAccumulatedMillis = 0) }
}

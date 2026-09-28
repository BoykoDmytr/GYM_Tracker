package com.boykodmytr.gymtracker.feature.timer

import androidx.lifecycle.ViewModel
import com.boykodmytr.gymtracker.core.timer.StandaloneTimer
import com.boykodmytr.gymtracker.core.timer.StandaloneTimerState
import com.boykodmytr.gymtracker.core.timer.TimerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timer: StandaloneTimer,
) : ViewModel() {
    val state: StateFlow<StandaloneTimerState> = timer.state

    fun setMode(mode: TimerMode) = timer.setMode(mode)
    fun setCountdownSeconds(seconds: Int) = timer.setCountdownSeconds(seconds)
    fun startCountdown() = timer.startCountdown()
    fun pauseCountdown() = timer.pauseCountdown()
    fun resetCountdown() = timer.resetCountdown()
    fun startStopwatch() = timer.startStopwatch()
    fun pauseStopwatch() = timer.pauseStopwatch()
    fun resetStopwatch() = timer.resetStopwatch()
}

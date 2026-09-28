package com.boykodmytr.gymtracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant

/** The app-wide clock, so timers on screen agree with timestamps stored by the data layer. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/** Current time, refreshed every [intervalMillis] while the composable is on screen. */
@Composable
fun rememberNow(intervalMillis: Long = 1000L): State<Instant> {
    val clock = LocalClock.current
    return produceState(clock.instant(), intervalMillis, clock) {
        while (true) {
            value = clock.instant()
            delay(intervalMillis)
        }
    }
}

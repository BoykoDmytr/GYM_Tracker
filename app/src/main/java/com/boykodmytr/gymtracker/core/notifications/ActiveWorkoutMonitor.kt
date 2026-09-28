package com.boykodmytr.gymtracker.core.notifications

import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the rest alarm and the ongoing notification in sync with the in-progress session stored in the
 * database. Screens only write state; this reacts to it, including after the process was restarted.
 */
@Singleton
class ActiveWorkoutMonitor @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val coordinator: TimerAlertCoordinator,
    private val notifier: WorkoutNotifier,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val restFired = coordinator.fired.filter { it == TimerKind.REST }.map { }.onStart { emit(Unit) }
            combine(workoutRepository.observeInProgressSession(), restFired) { session, _ -> session }
                .collect(::sync)
        }
    }

    private fun sync(session: WorkoutSession?) {
        if (session == null) {
            coordinator.disarm(TimerKind.REST)
            notifier.cancelOngoing()
            return
        }
        val rest = session.rest
        when {
            rest == null -> coordinator.disarm(TimerKind.REST)
            rest.endsAt.isAfter(clock.instant()) -> coordinator.arm(TimerKind.REST, rest.endsAt)
            // An expired rest is left alone: its alert has fired or is being delivered right now.
        }
        notifier.showOngoing(session, clock.instant())
    }
}

package com.boykodmytr.gymtracker.core.notifications

import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Re-schedules reminder alarms at start-up and whenever the schedule or the reminder switch changes. */
@Singleton
class ReminderSync @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            settingsRepository.settings
                .distinctUntilChangedBy { it.remindersEnabled to it.trainingDays }
                .collect(scheduler::reschedule)
        }
    }
}

package com.boykodmytr.gymtracker.core

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.core.notifications.ActiveWorkoutMonitor
import com.boykodmytr.gymtracker.core.notifications.NotificationChannels
import com.boykodmytr.gymtracker.core.notifications.NotificationIds
import com.boykodmytr.gymtracker.core.notifications.TimerAlertCoordinator
import com.boykodmytr.gymtracker.core.notifications.TimerKind
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Clock
import javax.inject.Inject

/** The rest timer is armed from database state, cancelled when rest is skipped, and alerts once. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RestTimerIntegrationTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var monitor: ActiveWorkoutMonitor
    @Inject lateinit var coordinator: TimerAlertCoordinator
    @Inject lateinit var clock: Clock

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        hiltRule.inject()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.createAll(context)
        runBlocking { seeder.seedIfNeeded() }
        monitor.start()
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out: $message" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
    }

    @Test
    fun restAlarmFollowsSessionState() = runBlocking {
        val template = programs.observeActiveProgram().first()!!.workouts.first()
        val sessionId = workouts.startSession(template.id)
        waitUntil("ongoing notification") {
            notifications.allNotifications.isNotEmpty()
        }

        val start = clock.instant()
        workouts.startRest(sessionId, start, start.plusSeconds(90))
        waitUntil("rest alarm scheduled") { alarms.scheduledAlarms.isNotEmpty() }
        assertEquals(start.plusSeconds(90).toEpochMilli(), alarms.scheduledAlarms.single().triggerAtTime)

        workouts.clearRest(sessionId)
        waitUntil("rest alarm cancelled") { alarms.scheduledAlarms.isEmpty() }

        workouts.startRest(sessionId, start, start.plusSeconds(60))
        waitUntil("rest alarm re-armed") { alarms.scheduledAlarms.isNotEmpty() }

        // The system delivers the alarm while the app is in the background.
        val fired = mutableListOf<TimerKind>()
        val collector = launch(Dispatchers.Default) { coordinator.fired.collect { fired += it } }
        yield()
        alarms.fireAlarm(alarms.scheduledAlarms.single())
        ShadowLooper.idleMainLooper()
        waitUntil("rest finished notification") {
            notifications.getNotification(NotificationIds.REST_FINISHED) != null
        }
        // A late duplicate (in-process timer or a re-sync of the same rest) must not alert again.
        coordinator.onAlarm(TimerKind.REST)
        Thread.sleep(200)
        ShadowLooper.idleMainLooper()
        assertEquals(listOf(TimerKind.REST), fired)
        assertTrue("monitor must not re-arm an announced rest", alarms.scheduledAlarms.isEmpty())
        collector.cancel()

        workouts.finishSession(sessionId, clock.instant())
        waitUntil("ongoing notification removed") {
            notifications.getNotification(NotificationIds.ONGOING_WORKOUT) == null
        }
    }
}

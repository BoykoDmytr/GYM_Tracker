package com.boykodmytr.gymtracker

import android.app.Application
import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.core.notifications.ActiveWorkoutMonitor
import com.boykodmytr.gymtracker.core.notifications.NotificationChannels
import com.boykodmytr.gymtracker.core.notifications.ReminderSync
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var workoutMonitor: ActiveWorkoutMonitor
    @Inject lateinit var reminderSync: ReminderSync

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        appScope.launch { seeder.seedIfNeeded() }
        workoutMonitor.start()
        reminderSync.start()
    }
}

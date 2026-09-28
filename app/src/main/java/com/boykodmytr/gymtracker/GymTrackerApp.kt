package com.boykodmytr.gymtracker

import android.app.Application
import com.boykodmytr.gymtracker.core.common.ApplicationScope
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymTrackerApp : Application() {

    @Inject lateinit var seeder: DataSeeder

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
    }
}

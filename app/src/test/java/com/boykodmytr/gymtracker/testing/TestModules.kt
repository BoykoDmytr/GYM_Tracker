package com.boykodmytr.gymtracker.testing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.dao.BodyDao
import com.boykodmytr.gymtracker.core.database.dao.ExerciseDao
import com.boykodmytr.gymtracker.core.database.dao.ProgramDao
import com.boykodmytr.gymtracker.core.database.dao.WorkoutDao
import com.boykodmytr.gymtracker.di.ClockModule
import com.boykodmytr.gymtracker.di.DataStoreModule
import com.boykodmytr.gymtracker.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Singleton

/** Monday 28 Sep 2026, 10:00 in Kyiv; the clock keeps ticking so timers behave normally. */
val TEST_ZONE: ZoneId = ZoneId.of("Europe/Kyiv")
val TEST_START: ZonedDateTime = ZonedDateTime.of(2026, 9, 28, 10, 0, 0, 0, TEST_ZONE)

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ClockModule::class])
object TestClockModule {
    @Provides
    @Singleton
    fun clock(): Clock {
        val system = Clock.system(TEST_ZONE)
        return Clock.offset(system, Duration.between(system.instant(), TEST_START.toInstant()))
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Provides fun exerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun programDao(db: AppDatabase): ProgramDao = db.programDao()
    @Provides fun workoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun bodyDao(db: AppDatabase): BodyDao = db.bodyDao()
}

/** A fresh DataStore file per test: DataStore forbids two live instances on one file. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb") }
}

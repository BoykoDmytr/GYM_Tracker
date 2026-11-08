package com.boykodmytr.gymtracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.di.SeedModule
import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import javax.inject.Inject

/** A fresh install starts with the real built-in program, ready to train: supersets, rest, weights. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StarterProgramSeedTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var workouts: WorkoutRepository
    @Inject lateinit var clock: Clock

    private lateinit var seeder: DataSeeder

    @Before
    fun setUp() {
        hiltRule.inject()
        // What real installs get (the Hilt binding the UI tests replace with a fixture).
        seeder = DataSeeder(db, settings, SeedModule.provideStarterProgram(), clock)
    }

    @Test
    fun freshInstallTrainsTheBuiltInProgram() = runBlocking {
        seeder.seedIfNeeded()
        val program = programs.observeActiveProgram().first()!!
        assertEquals("Тренування v11: фулбаді ×3", program.name)
        assertEquals(listOf("День А", "День Б", "День В"), program.workouts.map { it.name })

        val dayA = program.workouts[0].exercises
        assertEquals(60.0, dayA[0].target.weightKg!!, 0.0)
        assertEquals(180, dayA[0].restSeconds)
        assertTrue(dayA[0].notes.startsWith("У стійці з упорами"))
        assertNull(dayA[0].supersetId)
        assertTrue(dayA[2].supersetId != null && dayA[2].supersetId == dayA[3].supersetId)
        assertNull(dayA[4].supersetId)

        val dayB = program.workouts[1].exercises
        assertFalse(dayB[3].supersetId == dayB[5].supersetId) // two separate supersets
        assertEquals(dayB[5].supersetId, dayB[6].supersetId)

        // Laterals on days A and C are one exercise, so their history is shared.
        assertEquals(dayA[2].exercise.id, program.workouts[2].exercises[2].exercise.id)

        // The week the program is written for: Tue A · Thu Б · Sat В.
        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
            settings.settings.first().trainingDays.map { it.dayOfWeek },
        )

        // Seeding again changes nothing.
        seeder.seedIfNeeded()
        assertEquals(1, programs.observePrograms().first().size)
    }

    @Test
    fun aWorkoutFromItAlternatesTheSuperset() = runBlocking {
        seeder.seedIfNeeded()
        val dayA = programs.observeActiveProgram().first()!!.workouts[0]
        val session = workouts.observeSession(workouts.startSession(dayA.id)).first()!!
        val laterals = WorkoutFlow.ordered(session)[2]
        val next = WorkoutFlow.afterSet(session, laterals.id)
        assertEquals(WorkoutFlow.ordered(session)[3].id, next.switchTo)
        assertFalse(next.rest)
    }
}

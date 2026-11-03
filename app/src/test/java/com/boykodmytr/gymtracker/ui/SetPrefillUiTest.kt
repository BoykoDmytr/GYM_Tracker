package com.boykodmytr.gymtracker.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.MainActivity
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.data.seed.DataSeeder
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.model.TemplateExercise
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.testing.TEST_START
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The result sheet of a first set starts where the last workout started, not at the program's old
 * weight; a plan changed since then wins; a superset never borrows the other exercise's weight.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetPrefillUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var seeder: DataSeeder
    @Inject lateinit var programs: ProgramRepository
    @Inject lateinit var db: AppDatabase

    private lateinit var bench: TemplateExercise
    private lateinit var deadlift: TemplateExercise

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            seeder.seedIfNeeded()
            val workout = programs.observeActiveProgram().first()!!.workouts.first()
            bench = workout.exercises[0] // Жим штанги лежачи, 3×6–8
            deadlift = workout.exercises[1] // Румунська тяга зі штангою, 3×8–10
            programs.updateTemplateExercise(bench.id, bench.target.copy(weightKg = 28.0), bench.restSeconds, bench.notes)
            // Two days ago: bench planned at 28 kg but done with 32.5; Romanian deadlift with 40 kg.
            val startedAt = TEST_START.minusDays(2).toInstant()
            val dao = db.workoutDao()
            dao.insertSession(
                WorkoutSessionEntity(
                    "old", null, null, "Full Body A", TEST_START.toLocalDate().minusDays(2), startedAt, startedAt.plusSeconds(3600),
                    SessionStatus.COMPLETED, "", null, null, null, startedAt, startedAt,
                ),
            )
            listOf(Triple(bench, 28.0, listOf(32.5 to 8, 32.5 to 7)), Triple(deadlift, null, listOf(40.0 to 10, 40.0 to 9)))
                .forEachIndexed { order, (te, planned, sets) ->
                    val seId = "old-$order"
                    dao.insertSessionExercises(
                        listOf(
                            SessionExerciseEntity(
                                seId, "old", te.exercise.id, te.exercise.name, order, 3, 3, te.target.repsMin, te.target.repsMax,
                                planned, null, ExerciseStatus.COMPLETED, "",
                            ),
                        ),
                    )
                    sets.forEachIndexed { n, (kg, reps) ->
                        dao.insertSet(SetLogEntity("$seId-$n", seId, n + 1, kg, reps, null, false, null, startedAt, startedAt))
                    }
                }
        }
    }

    private fun startWorkout() = with(composeRule) {
        waitForText("Почати тренування")
        onNodeWithText("Почати тренування").performClick()
        waitForText("Почати")
        onNodeWithText("Почати").performClick()
        waitForText("Підхід 1 / 3")
    }

    private fun openSheetAndExpect(weight: String, reps: String) = with(composeRule) {
        onNodeWithText("Завершити підхід").performClick()
        waitForText("Зберегти")
        onAllNodes(hasSetTextAction())[0].assert(hasText(weight))
        onAllNodes(hasSetTextAction())[1].assert(hasText(reps))
    }

    private fun saveAsIs() = with(composeRule) {
        onNodeWithText("Зберегти").performClick()
        waitForNoText("Зберегти")
    }

    @Test
    fun firstSetStartsWhereLastWorkoutStarted() = with(composeRule) {
        runBlocking { programs.setSupersetWithNext(bench.id, true) }
        startWorkout()
        openSheetAndExpect("32,5", "8")
        screenshot("50_prefill_last_time")
        saveAsIs()

        // Superset switch: the Romanian deadlift must not inherit the bench weight.
        waitForText("Після підходу — відпочинок, потім нове коло")
        openSheetAndExpect("40", "10")
        saveAsIs()

        // Next round: repeat today's first bench set.
        waitForText("Підхід 2 / 3")
        openSheetAndExpect("32,5", "8")
    }

    @Test
    fun planChangedSinceLastWorkoutWins() = with(composeRule) {
        runBlocking { programs.updateTemplateExercise(bench.id, bench.target.copy(weightKg = 35.0), bench.restSeconds, bench.notes) }
        startWorkout()
        openSheetAndExpect("35", "6")
    }
}

package com.boykodmytr.gymtracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionStatus
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import com.boykodmytr.gymtracker.testing.TEST_START
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Which workout counts as "last time" for the pre-filled weight and the "last time" card. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LastPerformanceTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var workouts: WorkoutRepository

    private val now: Instant = TEST_START.toInstant()
    private var counter = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            db.transferDao().insertExercises(listOf(ExerciseEntity("bench", "Жим", "", now, now), ExerciseEntity("row", "Тяга", "", now, now)))
        }
    }

    /** A workout [daysAgo] before now; each entry of [exercises] is (exercise id, planned kg, first-set kg or null for no sets). */
    private fun workout(
        daysAgo: Long,
        vararg exercises: Triple<String, Double?, Double?>,
        status: SessionStatus = SessionStatus.COMPLETED,
        createdLater: Long = 0,
    ): String = runBlocking {
        val id = "s${++counter}"
        val startedAt = now.minus(Duration.ofDays(daysAgo))
        val dao = db.workoutDao()
        dao.insertSession(
            WorkoutSessionEntity(
                id, null, null, "Тренування", TEST_START.toLocalDate().minusDays(daysAgo), startedAt,
                startedAt.plusSeconds(3600).takeIf { status == SessionStatus.COMPLETED }, status, "", null, null, null,
                startedAt.plusSeconds(createdLater), startedAt,
            ),
        )
        exercises.forEachIndexed { order, (exerciseId, planned, firstSet) ->
            val seId = "$id-$order"
            dao.insertSessionExercises(
                listOf(SessionExerciseEntity(seId, id, exerciseId, exerciseId, order, 3, 3, 6, 8, planned, null, ExerciseStatus.COMPLETED, "")),
            )
            if (firstSet != null) {
                listOf(firstSet, firstSet - 2.5).forEachIndexed { n, kg ->
                    dao.insertSet(SetLogEntity("$seId-$n", seId, n + 1, kg, 8 - n, null, false, null, startedAt, startedAt))
                }
            }
        }
        id
    }

    private fun last(exerciseId: String = "bench", exclude: String? = null) = runBlocking { workouts.lastPerformance(exerciseId, exclude) }

    @Test
    fun newestPerformedWorkoutWithItsPlannedWeight() {
        workout(7, Triple("bench", 28.0, 30.0))
        workout(3, Triple("bench", 28.0, 32.5))
        val last = last()!!
        assertEquals(listOf(32.5, 30.0), last.sets.map { it.weightKg })
        assertEquals(28.0, last.plannedWeightKg!!, 0.0)
    }

    @Test
    fun skippedInProgressAndFutureWorkoutsDoNotCount() {
        workout(5, Triple("bench", 28.0, 30.0))
        workout(2, Triple("bench", 28.0, null)) // exercise skipped: no sets
        val current = workout(0, Triple("bench", 28.0, 40.0), status = SessionStatus.IN_PROGRESS)
        workout(-400, Triple("bench", null, 90.0)) // imported with a typo in the year
        assertEquals(30.0, last(exclude = current)!!.sets.first().weightKg, 0.0)
        assertNull(last("row"))
    }

    @Test
    fun tiesResolveToTheLaterImportAndTheFirstOccurrence() {
        // Two imported workouts on the same day without a time: both start at the same instant.
        workout(4, Triple("bench", null, 30.0))
        workout(4, Triple("bench", null, 31.0), createdLater = 60)
        assertEquals(31.0, last()!!.sets.first().weightKg, 0.0)

        // The same exercise twice in one workout: the main work, not the finisher.
        workout(1, Triple("bench", 28.0, 32.5), Triple("row", null, 20.0), Triple("bench", 20.0, 22.5))
        assertEquals(32.5, last()!!.sets.first().weightKg, 0.0)
    }
}

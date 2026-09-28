package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.WorkoutFlow
import com.boykodmytr.gymtracker.domain.logic.WorkoutStep
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFlowTest {

    @Test
    fun `fresh session starts at set 1 of the first exercise`() {
        val s = session(listOf(exercise("a", 0), exercise("b", 1)))
        val step = WorkoutFlow.step(s) as WorkoutStep.PerformSet
        assertEquals("a", step.exercise.id)
        assertEquals(1, step.setNumber)
        assertEquals(3, step.plannedSets)
        assertFalse(step.isOptional)
    }

    @Test
    fun `logged sets advance the set number`() {
        val s = session(listOf(exercise("a", 0, sets = 2), exercise("b", 1)), currentId = "a")
        val step = WorkoutFlow.step(s) as WorkoutStep.PerformSet
        assertEquals(3, step.setNumber)
    }

    @Test
    fun `all planned sets done shows exercise done with the next exercise`() {
        val s = session(listOf(exercise("a", 0, sets = 3), exercise("b", 1)), currentId = "a")
        val step = WorkoutFlow.step(s) as WorkoutStep.ExerciseDone
        assertEquals("b", step.next?.id)
    }

    @Test
    fun `sets above the lower bound of a range are optional`() {
        val abs = exercise("abs", 0, target = SetTarget(2, 3, 10, 15), sets = 2)
        val step = WorkoutFlow.step(session(listOf(abs), "abs")) as WorkoutStep.PerformSet
        assertEquals(3, step.setNumber)
        assertTrue(step.isOptional)
        assertTrue(WorkoutFlow.canCompleteEarly(abs))
    }

    @Test
    fun `extra set can be added after the plan`() {
        val s = session(listOf(exercise("a", 0, sets = 3)), "a")
        val step = WorkoutFlow.step(s, extraSetRequested = true) as WorkoutStep.PerformSet
        assertEquals(4, step.setNumber)
        assertTrue(step.isExtra)
        assertFalse(step.isOptional)
    }

    @Test
    fun `last exercise done means workout done`() {
        val s = session(
            listOf(exercise("a", 0, status = ExerciseStatus.COMPLETED, sets = 3), exercise("b", 1, sets = 3)),
            currentId = "b",
        )
        val step = WorkoutFlow.step(s) as WorkoutStep.ExerciseDone
        assertNull(step.next)
    }

    @Test
    fun `next pending wraps around to exercises that were jumped over`() {
        val s = session(
            listOf(exercise("a", 0), exercise("b", 1), exercise("c", 2, sets = 3)),
            currentId = "c",
        )
        assertEquals("a", WorkoutFlow.nextPending(s, "c")?.id)
    }

    @Test
    fun `skipped exercise is not offered as next`() {
        val s = session(
            listOf(exercise("a", 0, sets = 3), exercise("b", 1, status = ExerciseStatus.SKIPPED), exercise("c", 2)),
            currentId = "a",
        )
        assertEquals("c", WorkoutFlow.nextPending(s, "a")?.id)
    }

    @Test
    fun `current exercise falls back to the first pending one`() {
        val s = session(listOf(exercise("a", 0, status = ExerciseStatus.COMPLETED), exercise("b", 1)), currentId = null)
        assertEquals("b", WorkoutFlow.currentExercise(s)?.id)
    }

    @Test
    fun `closing status depends on whether sets were logged`() {
        assertEquals(ExerciseStatus.SKIPPED, WorkoutFlow.closingStatus(exercise("a", 0)))
        assertEquals(ExerciseStatus.COMPLETED, WorkoutFlow.closingStatus(exercise("a", 0, sets = 1)))
    }

    @Test
    fun `unfinished exercises are detected before finishing`() {
        val done = session(listOf(exercise("a", 0, sets = 3), exercise("b", 1, status = ExerciseStatus.SKIPPED)))
        assertFalse(WorkoutFlow.hasUnfinishedExercises(done))
        val notDone = session(listOf(exercise("a", 0, sets = 3), exercise("b", 1, sets = 1)))
        assertTrue(WorkoutFlow.hasUnfinishedExercises(notDone))
    }
}

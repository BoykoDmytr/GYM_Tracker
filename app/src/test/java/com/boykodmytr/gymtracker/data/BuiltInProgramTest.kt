package com.boykodmytr.gymtracker.data

import com.boykodmytr.gymtracker.data.seed.BuiltInPrograms
import com.boykodmytr.gymtracker.domain.logic.Supersets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The program that ships with the app, as written in "Тренування v11: фулбаді ×3". */
class BuiltInProgramTest {
    private val program = BuiltInPrograms.fullBodyV11
    private var counter = 0

    private fun supersetRanges(workout: Int) =
        Supersets.groups(Supersets.fromLabels(program.workouts[workout].exercises.map { it.superset }) { "s${++counter}" })

    @Test
    fun `three days in rotation order`() {
        assertEquals(listOf("День А", "День Б", "День В"), program.workouts.map { it.name })
        assertEquals(listOf(5, 7, 7), program.workouts.map { it.exercises.size })
        val dayA = program.workouts[0].exercises
        assertEquals("Жим штанги лежачи", dayA[0].name)
        assertEquals(listOf(3, 3, 4, 6), dayA[0].target.let { listOf(it.setsMin, it.setsMax, it.repsMin, it.repsMax) })
        assertEquals(60.0, dayA[0].target.weightKg!!, 0.0)
    }

    @Test
    fun `supersets are the pairs from the document`() {
        assertEquals(listOf(2..3), supersetRanges(0)) // махи + розгинання з-за голови
        assertEquals(listOf(3..4, 5..6), supersetRanges(1)) // задня дельта + молотки; носки + колесо
        assertEquals(listOf(2..3), supersetRanges(2))
    }

    @Test
    fun `rest is 2,5 or 3 minutes and 3 minutes after a superset round`() {
        val all = program.workouts.flatMap { it.exercises }
        assertTrue(all.all { it.restSeconds == 150 || it.restSeconds == 180 })
        assertTrue(all.filter { it.superset != null }.all { it.restSeconds == 180 })
        // The heavy lifts get the longer rest.
        val heavy = listOf("Жим штанги лежачи", "Підтягування з вагою", "Жим штанги стоячи над головою")
        assertTrue(all.filter { it.name in heavy }.all { it.restSeconds == 180 })
    }

    @Test
    fun `targets are valid and shared exercises use one name`() {
        val all = program.workouts.flatMap { it.exercises }
        assertTrue(all.all { it.target.isValid })
        // Laterals and overhead extensions are the same exercises on days A and C: one history each.
        assertEquals(17, all.map { it.name }.distinct().size)
        // Weighted and bodyweight pull-ups are kept apart, so one never pre-fills the other.
        assertTrue("Підтягування з вагою" in all.map { it.name } && "Підтягування" in all.map { it.name })
    }
}

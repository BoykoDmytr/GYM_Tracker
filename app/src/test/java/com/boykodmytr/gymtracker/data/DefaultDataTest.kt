package com.boykodmytr.gymtracker.data

import com.boykodmytr.gymtracker.data.seed.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDataTest {
    private val program = DefaultData.fullBody

    @Test
    fun `program matches the provided tables`() {
        assertEquals(listOf("Full Body A", "Full Body B", "Full Body C"), program.workouts.map { it.name })
        program.workouts.forEach { assertEquals(7, it.exercises.size) }
        val abs = program.workouts[0].exercises.last()
        assertEquals("Прес", abs.name)
        assertEquals(listOf(2, 3, 10, 15), listOf(abs.setsMin, abs.setsMax, abs.repsMin, abs.repsMax))
        val bench = program.workouts[0].exercises.first()
        assertEquals(listOf(3, 3, 6, 8), listOf(bench.setsMin, bench.setsMax, bench.repsMin, bench.repsMax))
    }

    @Test
    fun `identical names are shared between workouts and all ranges are valid`() {
        val all = program.workouts.flatMap { it.exercises }
        assertEquals(21, all.size)
        assertEquals(18, all.map { it.name }.distinct().size)
        assertTrue(all.all { it.setsMin in 1..it.setsMax && it.repsMin in 1..it.repsMax })
    }
}

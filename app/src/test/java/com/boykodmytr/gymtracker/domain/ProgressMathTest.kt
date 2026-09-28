package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.ProgressMath
import com.boykodmytr.gymtracker.domain.logic.ProgressionAdvisor
import com.boykodmytr.gymtracker.domain.logic.ProgressionHint
import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.SetTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressMathTest {

    private fun record(session: String, day: Int, weight: Double, reps: Int, n: Int = 1) = ExerciseSetRecord(
        sessionId = session,
        date = LocalDate.of(2026, 9, day),
        startedAt = T0.plusSeconds(day * 86_400L),
        setNumber = n,
        weightKg = weight,
        reps = reps,
        rpe = null,
        isFailure = false,
    )

    @Test
    fun `epley estimate`() {
        assertEquals(0.0, ProgressMath.estimatedOneRepMax(0.0, 10), 1e-9)
        assertEquals(100.0, ProgressMath.estimatedOneRepMax(100.0, 1), 1e-9)
        assertEquals(80.0, ProgressMath.estimatedOneRepMax(60.0, 10), 1e-9)
    }

    @Test
    fun `aggregates per session and sorts chronologically`() {
        val records = listOf(
            record("s2", 3, 65.0, 8, 1), record("s2", 3, 65.0, 7, 2),
            record("s1", 1, 60.0, 8, 1), record("s1", 1, 62.5, 6, 2),
        )
        val sessions = ProgressMath.perSession(records)
        assertEquals(listOf("s1", "s2"), sessions.map { it.sessionId })
        assertEquals(62.5, sessions[0].bestWeightKg, 1e-9)
        assertEquals(6, sessions[0].repsAtBestWeight)
        assertEquals(60.0 * 8 + 62.5 * 6, sessions[0].volumeKg, 1e-9)
        assertEquals(2, sessions[1].sets)
    }

    @Test
    fun `weight progression collapses repeats`() {
        val sessions = ProgressMath.perSession(
            listOf(record("a", 1, 60.0, 8), record("b", 2, 60.0, 8), record("c", 3, 65.0, 8), record("d", 4, 72.5, 6)),
        )
        assertEquals(listOf(60.0, 65.0, 72.5), ProgressMath.weightProgression(sessions))
    }

    @Test
    fun `records and bodyweight detection`() {
        val sessions = ProgressMath.perSession(listOf(record("a", 1, 0.0, 12), record("b", 2, 0.0, 15)))
        assertTrue(ProgressMath.isBodyweightOnly(sessions))
        val records = ProgressMath.personalRecords(sessions)
        assertNull(records.maxWeight)
        assertEquals(15.0, records.maxReps!!.value, 1e-9)
    }

    @Test
    fun `double progression suggests more weight only when every set hit the top of the range`() {
        val target = SetTarget(3, 3, 8, 12)
        val top = (1..3).map { set("e", it, weight = 60.0, reps = 12) }
        assertEquals(ProgressionHint.IncreaseWeight(60.0), ProgressionAdvisor.hint(top, target))
        val notYet = top.dropLast(1) + set("e", 3, weight = 60.0, reps = 10)
        assertEquals(ProgressionHint.AddReps(60.0), ProgressionAdvisor.hint(notYet, target))
        assertNull(ProgressionAdvisor.hint(emptyList(), target))
    }
}

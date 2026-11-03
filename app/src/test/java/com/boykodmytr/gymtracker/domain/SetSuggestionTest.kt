package com.boykodmytr.gymtracker.domain

import com.boykodmytr.gymtracker.domain.logic.SetSuggestion
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.LastPerformance
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SetSuggestionTest {

    private fun bench(planned: Double?, vararg done: Pair<Double, Int>, status: ExerciseStatus = ExerciseStatus.PENDING) =
        exercise("bench", 0, target = SetTarget(3, 3, 6, 8, planned), status = status).copy(
            sets = done.mapIndexed { i, (w, r) -> set("bench", i + 1, w, r) },
        )

    private fun last(planned: Double?, vararg sets: Pair<Double, Int>) =
        LastPerformance(sets.mapIndexed { i, (w, r) -> set("old", i + 1, w, r) }, planned)

    @Test
    fun `first set starts where last workout started when the plan did not change`() {
        // The reported case: the program says 28 kg, last time the first set was 32.5 kg.
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(28.0), last(28.0, 32.5 to 8, 32.5 to 7, 30.0 to 6)))
    }

    @Test
    fun `a plan changed since last workout wins, with the lower end of the rep range`() {
        assertEquals(SetInput(35.0, 6), SetSuggestion.suggest(bench(35.0), last(28.0, 32.5 to 8)))
        // Weight added to the plan after the exercise was last done (e.g. imported history without plan).
        assertEquals(SetInput(28.0, 6), SetSuggestion.suggest(bench(28.0), last(null, 32.5 to 8)))
    }

    @Test
    fun `no planned weight always repeats last time`() {
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(null), last(null, 32.5 to 8)))
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(null), last(28.0, 32.5 to 8)))
    }

    @Test
    fun `never performed uses the plan or nothing`() {
        assertEquals(SetInput(28.0, 6), SetSuggestion.suggest(bench(28.0), null))
        assertEquals(SetInput(0.0, 6), SetSuggestion.suggest(bench(null), null))
        assertEquals(SetInput(28.0, 6), SetSuggestion.suggest(bench(28.0), last(28.0)))
    }

    @Test
    fun `later sets repeat this workout's previous set`() {
        assertEquals(SetInput(30.0, 10), SetSuggestion.suggest(bench(28.0, 32.5 to 8, 30.0 to 10), last(28.0, 32.5 to 8)))
    }

    @Test
    fun `extra set after a skipped exercise uses the first-set rule`() {
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(28.0, status = ExerciseStatus.SKIPPED), last(28.0, 32.5 to 8)))
    }

    @Test
    fun `a target re-saved in pounds counts as unchanged`() {
        // 32.5 kg shown as 71,65 lb and saved back is a few grams off.
        val resaved = WeightUnit.LB.toKg(Math.round(WeightUnit.LB.fromKg(32.5) * 100) / 100.0)
        assertEquals(SetInput(40.0, 5), SetSuggestion.suggest(bench(resaved), last(32.5, 40.0 to 5)))
        // A real change of a kilogram is not rounding.
        assertEquals(SetInput(32.5, 6), SetSuggestion.suggest(bench(32.5), last(31.5, 40.0 to 5)))
    }

    @Test
    fun `rpe failure and notes are never carried over`() {
        val withExtras = LastPerformance(listOf(set("old", 1, 32.5, 8).copy(rpe = 9.0, isFailure = true, note = "болить лікоть")), 28.0)
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(28.0), withExtras))
    }

    @Test
    fun `first set is taken by set number, not by list order`() {
        val unordered = LastPerformance(listOf(set("old", 2, 30.0, 6), set("old", 1, 32.5, 8)), 28.0)
        assertEquals(SetInput(32.5, 8), SetSuggestion.suggest(bench(28.0), unordered))
    }
}

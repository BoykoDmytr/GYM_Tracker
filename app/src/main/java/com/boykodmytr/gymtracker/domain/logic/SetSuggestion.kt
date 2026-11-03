package com.boykodmytr.gymtracker.domain.logic

import com.boykodmytr.gymtracker.domain.model.LastPerformance
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.SetInput
import kotlin.math.abs

/**
 * Pre-fills the result of the next set. The guess must match what the user actually lifts, because a
 * wrong pre-filled weight is easy to save without noticing.
 *
 * - Set 2 and later (or an extra set): repeat this workout's previous set.
 * - First set: start where the user started last time (first set of the last workout). The plan's
 *   weight wins only when the plan was changed since then, i.e. this workout's planned weight differs
 *   from the one planned last time (raised, lowered, or newly set): that is the newer decision.
 * - Never performed: the plan's weight (or nothing) and the lower end of the rep range.
 *
 * RPE, failure and notes are never carried over.
 */
object SetSuggestion {

    /** Re-saving a target in pounds rounds it by a few grams; treat that as the same weight. */
    private const val SAME_WEIGHT_KG = 0.01

    fun suggest(exercise: SessionExercise, last: LastPerformance?): SetInput {
        exercise.sets.maxByOrNull { it.setNumber }?.let { return SetInput(weightKg = it.weightKg, reps = it.reps) }

        val planned = exercise.target.weightKg
        val first = last?.sets?.minByOrNull { it.setNumber }
        val planUnchanged = planned == null || last?.plannedWeightKg?.let { abs(it - planned) < SAME_WEIGHT_KG } == true
        return if (first != null && planUnchanged) {
            SetInput(weightKg = first.weightKg, reps = first.reps)
        } else {
            SetInput(weightKg = planned ?: 0.0, reps = exercise.target.repsMin)
        }
    }
}

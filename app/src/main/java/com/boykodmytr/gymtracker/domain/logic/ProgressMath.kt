package com.boykodmytr.gymtracker.domain.logic

import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.SetTarget
import java.time.Instant
import java.time.LocalDate

/** Best results of one exercise within one workout. */
data class ExerciseSessionProgress(
    val sessionId: String,
    val date: LocalDate,
    val startedAt: Instant,
    val sets: Int,
    val bestWeightKg: Double,
    val repsAtBestWeight: Int,
    val maxReps: Int,
    val volumeKg: Double,
    val estimatedOneRepMaxKg: Double,
)

data class RecordValue(val value: Double, val date: LocalDate)

data class PersonalRecords(
    val maxWeight: RecordValue?,
    val bestOneRepMax: RecordValue?,
    val maxVolume: RecordValue?,
    val maxReps: RecordValue?,
)

object ProgressMath {

    /**
     * Epley estimate. It is only an estimate (least accurate above ~10 reps), but it makes sets with
     * different rep counts comparable, which raw max weight does not when training in rep ranges.
     */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
        weightKg <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    fun perSession(records: List<ExerciseSetRecord>): List<ExerciseSessionProgress> =
        records.groupBy { it.sessionId }
            .map { (sessionId, sets) ->
                val best = sets.maxWith(compareBy<ExerciseSetRecord> { it.weightKg }.thenBy { it.reps })
                ExerciseSessionProgress(
                    sessionId = sessionId,
                    date = sets.first().date,
                    startedAt = sets.first().startedAt,
                    sets = sets.size,
                    bestWeightKg = best.weightKg,
                    repsAtBestWeight = best.reps,
                    maxReps = sets.maxOf { it.reps },
                    volumeKg = sets.sumOf { it.weightKg * it.reps },
                    estimatedOneRepMaxKg = sets.maxOf { estimatedOneRepMax(it.weightKg, it.reps) },
                )
            }
            .sortedBy { it.startedAt }

    fun personalRecords(sessions: List<ExerciseSessionProgress>): PersonalRecords {
        fun best(selector: (ExerciseSessionProgress) -> Double): RecordValue? =
            sessions.filter { selector(it) > 0.0 }.maxByOrNull(selector)?.let { RecordValue(selector(it), it.date) }
        return PersonalRecords(
            maxWeight = best { it.bestWeightKg },
            bestOneRepMax = best { it.estimatedOneRepMaxKg },
            maxVolume = best { it.volumeKg },
            maxReps = best { it.maxReps.toDouble() },
        )
    }

    /** "60 → 65 → 70 → 72.5": best working weight per session, repeated values collapsed. */
    fun weightProgression(sessions: List<ExerciseSessionProgress>, limit: Int = 6): List<Double> {
        val collapsed = mutableListOf<Double>()
        sessions.sortedBy { it.startedAt }.forEach { if (collapsed.lastOrNull() != it.bestWeightKg) collapsed += it.bestWeightKg }
        return collapsed.takeLast(limit)
    }

    /** True when the exercise is done without external weight (pull-ups, abs), so reps are the metric. */
    fun isBodyweightOnly(sessions: List<ExerciseSessionProgress>): Boolean =
        sessions.isNotEmpty() && sessions.all { it.bestWeightKg <= 0.0 }
}

sealed interface ProgressionHint {
    /** Every planned set reached the top of the rep range with the same weight: time to add weight. */
    data class IncreaseWeight(val currentWeightKg: Double) : ProgressionHint

    /** Keep the weight and try to add reps until the top of the range is reached on all sets. */
    data class AddReps(val currentWeightKg: Double) : ProgressionHint
}

/** Classic double progression, which is what rep ranges like 3×8–12 imply. */
object ProgressionAdvisor {
    fun hint(lastSets: List<SetLog>, target: SetTarget): ProgressionHint? {
        if (lastSets.isEmpty()) return null
        val workingWeight = lastSets.maxOf { it.weightKg }
        if (workingWeight <= 0.0) return null
        val workingSets = lastSets.filter { it.weightKg == workingWeight }
        val allAtTop = workingSets.size >= target.setsMin && workingSets.all { it.reps >= target.repsMax }
        return if (allAtTop) ProgressionHint.IncreaseWeight(workingWeight) else ProgressionHint.AddReps(workingWeight)
    }
}

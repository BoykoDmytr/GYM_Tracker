package com.boykodmytr.gymtracker.domain.transfer

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where the sets of one exercise name from the file go. */
sealed interface ExerciseTarget {
    val name: String

    data class Existing(val id: String, override val name: String) : ExerciseTarget
    data class New(override val name: String) : ExerciseTarget
}

enum class MatchReason {
    /** Same name as an exercise in the app (ignoring case, spaces and punctuation). */
    SAME_NAME,

    /** A known spelling of the same exercise (see [ExerciseAliases]). */
    ALIAS,

    /** Not in the app yet: a new exercise will be created. */
    NEW,

    /** Chosen by the user on the import screen. */
    MANUAL,
}

data class ExerciseMatch(val sourceName: String, val target: ExerciseTarget, val reason: MatchReason, val setCount: Int)

data class ExistingExercise(val id: String, val name: String)

data class ExistingSet(val exerciseId: String, val weightKg: Double, val reps: Int)

/** A completed workout already in the app, for duplicate detection. [startTime] is null when unknown. */
data class ExistingSession(val id: String, val date: LocalDate, val startTime: LocalTime?, val name: String, val sets: List<ExistingSet>)

data class ExistingMeasurement(val typeId: String, val date: LocalDate, val value: Double)

data class PlannedSession(val session: ParsedSession, val targets: Map<String, ExerciseTarget>)

data class ImportPlan(
    val sessions: List<PlannedSession>,
    val bodyWeights: List<Pair<LocalDate, Double>>,
    /** Names of exercises that will be created. */
    val newExercises: List<String>,
    val duplicateSessions: Int,
    val conflictingSessions: Int,
    val issues: List<ImportIssue>,
) {
    val setCount: Int get() = sessions.sumOf { it.session.setCount }
    val isEmpty: Boolean get() = sessions.isEmpty() && bodyWeights.isEmpty()
}

object ImportPlanner {

    /**
     * Proposes a target for every exercise name in the file: an app exercise with the same name, then a
     * known alias, otherwise a new exercise named like the canonical spelling (or as written). Similar
     * looking names are never matched automatically.
     */
    fun suggestMatches(file: ParsedWorkoutFile, existing: List<ExistingExercise>): List<ExerciseMatch> {
        val byKey = existing.associateBy { HeaderText.key(it.name) }
        val setCounts = LinkedHashMap<String, Int>()
        file.sessions.forEach { s -> s.exercises.forEach { e -> setCounts[e.name] = (setCounts[e.name] ?: 0) + e.sets.size } }
        return setCounts.map { (name, sets) ->
            val same = byKey[HeaderText.key(name)]
            if (same != null) return@map ExerciseMatch(name, ExerciseTarget.Existing(same.id, same.name), MatchReason.SAME_NAME, sets)
            val canonical = ExerciseAliases.canonical(name)
            if (canonical != null) {
                val existingSpelling = ExerciseAliases.spellings(canonical).firstNotNullOfOrNull { byKey[HeaderText.key(it)] }
                val target = existingSpelling?.let { ExerciseTarget.Existing(it.id, it.name) } ?: ExerciseTarget.New(canonical)
                return@map ExerciseMatch(name, target, MatchReason.ALIAS, sets)
            }
            ExerciseMatch(name, ExerciseTarget.New(name), MatchReason.NEW, sets)
        }
    }

    fun plan(
        file: ParsedWorkoutFile,
        matches: List<ExerciseMatch>,
        existingExercises: List<ExistingExercise>,
        existingSessions: List<ExistingSession>,
        existingBodyWeights: List<ExistingMeasurement>,
    ): ImportPlan {
        // Two file names mapped to "new: X", or "new: X" where X already exists, share one exercise.
        val existingByKey = existingExercises.associateBy { HeaderText.key(it.name) }
        val targets = matches.associate { match ->
            val target = when (val t = match.target) {
                is ExerciseTarget.Existing -> t
                is ExerciseTarget.New -> existingByKey[HeaderText.key(t.name)]?.let { ExerciseTarget.Existing(it.id, it.name) } ?: t
            }
            match.sourceName to target
        }
        val issues = file.issues.toMutableList()
        val planned = mutableListOf<PlannedSession>()
        var duplicates = 0
        var conflicts = 0
        val sessionsByDate = existingSessions.groupBy { it.date }

        for (session in file.sessions) {
            val sameWorkout = sessionsByDate[session.date].orEmpty().filter { existing ->
                HeaderText.key(existing.name) == HeaderText.key(session.name) &&
                    (session.startTime == null || existing.startTime == null || sameMinute(existing.startTime, session.startTime))
            }
            if (sameWorkout.isNotEmpty()) {
                val incoming = setSignature(session, targets)
                if (sameWorkout.any { containsAll(signature(it.sets), incoming) }) {
                    duplicates++
                    issues += ImportIssue(IssueKind.SESSION_DUPLICATE, session.firstRow, date = session.date, workout = session.name)
                } else {
                    // Never merge into or overwrite a workout the user already has: report and skip.
                    conflicts++
                    issues += ImportIssue(IssueKind.SESSION_CONFLICT, session.firstRow, date = session.date, workout = session.name)
                }
                continue
            }
            sessionsByDate[session.date]?.let { others ->
                issues += ImportIssue(IssueKind.SAME_DATE, session.firstRow, value = others.joinToString { it.name }, date = session.date, workout = session.name)
            }
            planned += PlannedSession(session, session.exercises.associate { it.name to targets.getValue(it.name) })
        }

        val bodyWeights = mutableListOf<Pair<LocalDate, Double>>()
        for (session in planned.map { it.session }) {
            val value = session.bodyWeightKg ?: continue
            val known = existingBodyWeights.any { it.date == session.date && abs(it.value - value) < 0.05 } ||
                bodyWeights.any { it.first == session.date && abs(it.second - value) < 0.05 }
            if (known) {
                issues += ImportIssue(IssueKind.MEASUREMENT_DUPLICATE, session.firstRow, value = value.toString(), date = session.date)
            } else {
                bodyWeights += session.date to value
            }
        }

        val newExercises = planned.flatMap { it.targets.values }.filterIsInstance<ExerciseTarget.New>()
            .distinctBy { HeaderText.key(it.name) }.map { it.name }
        return ImportPlan(planned, bodyWeights, newExercises, duplicates, conflicts, issues)
    }

    private fun sameMinute(a: LocalTime, b: LocalTime) = a.hour == b.hour && a.minute == b.minute

    private fun setSignature(session: ParsedSession, targets: Map<String, ExerciseTarget>): Map<Triple<String, Long, Int>, Int> =
        session.exercises.flatMap { e ->
            val key = when (val t = targets.getValue(e.name)) {
                is ExerciseTarget.Existing -> t.id
                is ExerciseTarget.New -> "new:" + HeaderText.key(t.name)
            }
            e.sets.map { Triple(key, (it.weightKg * 100).roundToLong(), it.reps) }
        }.groupingBy { it }.eachCount()

    private fun signature(sets: List<ExistingSet>): Map<Triple<String, Long, Int>, Int> =
        sets.map { Triple(it.exerciseId, (it.weightKg * 100).roundToLong(), it.reps) }.groupingBy { it }.eachCount()

    private fun <K> containsAll(existing: Map<K, Int>, incoming: Map<K, Int>): Boolean =
        incoming.all { (key, count) -> (existing[key] ?: 0) >= count }
}

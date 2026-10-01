package com.boykodmytr.gymtracker.domain.logic

import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import com.boykodmytr.gymtracker.domain.model.WorkoutSession

/** What the active workout screen should show right now. */
sealed interface WorkoutStep {
    val exercise: SessionExercise

    data class PerformSet(
        override val exercise: SessionExercise,
        val setNumber: Int,
        val plannedSets: Int,
        /** True for sets above the lower bound of a range like "2–3 sets". */
        val isOptional: Boolean,
        /** True for a set added on top of the plan. */
        val isExtra: Boolean,
    ) : WorkoutStep

    data class ExerciseDone(
        override val exercise: SessionExercise,
        /** Next exercise that still needs to be done; null means the whole workout is done. */
        val next: SessionExercise?,
    ) : WorkoutStep
}

/** Where the workout goes after a set has been logged. */
data class AfterSet(
    /** Exercise to switch to (the next one of a superset); null = stay on the current one. */
    val switchTo: String?,
    /** False between exercises of a superset round: the next exercise follows without rest. */
    val rest: Boolean,
    /** No exercise of the workout needs another set. */
    val workoutDone: Boolean,
)

/**
 * Pure state machine of an active workout. Everything is derived from what is stored in the database
 * (logged sets, exercise statuses, current exercise pointer), so the workout survives the app being
 * killed in the middle of a set.
 */
object WorkoutFlow {

    fun ordered(session: WorkoutSession): List<SessionExercise> = session.exercises.sortedBy { it.orderIndex }

    fun currentExercise(session: WorkoutSession): SessionExercise? {
        val exercises = ordered(session)
        return exercises.firstOrNull { it.id == session.currentExerciseId }
            ?: exercises.firstOrNull { it.status == ExerciseStatus.PENDING }
            ?: exercises.lastOrNull()
    }

    fun step(session: WorkoutSession, extraSetRequested: Boolean = false): WorkoutStep? {
        val exercise = currentExercise(session) ?: return null
        val logged = exercise.sets.size
        val planned = exercise.target.setsMax
        val closed = exercise.status != ExerciseStatus.PENDING || logged >= planned
        return if (closed && !extraSetRequested) {
            WorkoutStep.ExerciseDone(exercise, nextPending(session, exercise.id))
        } else {
            val setNumber = logged + 1
            WorkoutStep.PerformSet(
                exercise = exercise,
                setNumber = setNumber,
                plannedSets = planned,
                isOptional = setNumber > exercise.target.setsMin && setNumber <= planned,
                isExtra = setNumber > planned,
            )
        }
    }

    /** Next pending exercise after [currentId] in program order, wrapping around to skipped-over ones. */
    fun nextPending(session: WorkoutSession, currentId: String): SessionExercise? {
        val exercises = ordered(session)
        val index = exercises.indexOfFirst { it.id == currentId }
        val rotated = if (index == -1) exercises else exercises.drop(index + 1) + exercises.take(index)
        return rotated.firstOrNull { it.status == ExerciseStatus.PENDING && it.id != currentId }
    }

    /** The exercises of [exercise]'s superset in order, or just [exercise] when it is not in one. */
    fun supersetOf(session: WorkoutSession, exercise: SessionExercise): List<SessionExercise> {
        val id = exercise.supersetId ?: return listOf(exercise)
        val exercises = ordered(session)
        val index = exercises.indexOfFirst { it.id == exercise.id }
        if (index == -1) return listOf(exercise)
        var start = index
        while (start > 0 && exercises[start - 1].supersetId == id) start--
        var end = index
        while (end < exercises.lastIndex && exercises[end + 1].supersetId == id) end++
        return exercises.subList(start, end + 1)
    }

    /**
     * Decides what follows a set of [exerciseId]; [session] is the state before the set is saved.
     * In a superset one set of each exercise makes a round (A1 → B1 → rest → A2 → B2 → rest …);
     * an exercise with fewer planned sets simply drops out of the later rounds.
     */
    fun afterSet(session: WorkoutSession, exerciseId: String): AfterSet {
        fun setsAfter(e: SessionExercise) = e.sets.size + if (e.id == exerciseId) 1 else 0
        fun open(e: SessionExercise) = e.status == ExerciseStatus.PENDING && setsAfter(e) < e.target.setsMax
        val current = session.exercises.firstOrNull { it.id == exerciseId }
            ?: return AfterSet(null, rest = true, workoutDone = false)
        val workoutDone = session.exercises.none(::open)
        val group = supersetOf(session, current)
        if (group.size < 2) return AfterSet(null, rest = true, workoutDone = workoutDone)
        val index = group.indexOfFirst { it.id == exerciseId }
        group.drop(index + 1).firstOrNull(::open)?.let { return AfterSet(it.id, rest = false, workoutDone = false) }
        val nextRound = group.firstOrNull(::open)
        return AfterSet(nextRound?.id?.takeIf { it != exerciseId }, rest = true, workoutDone = workoutDone)
    }

    /** The user may close an exercise early once the lower bound of the set range is reached. */
    fun canCompleteEarly(exercise: SessionExercise): Boolean =
        exercise.sets.size >= exercise.target.setsMin && exercise.sets.size < exercise.target.setsMax

    fun hasUnfinishedExercises(session: WorkoutSession): Boolean =
        session.exercises.any { it.status == ExerciseStatus.PENDING && it.sets.size < it.target.setsMin }

    /** Status an exercise gets when the user leaves it (next exercise, skip, or finishing the workout). */
    fun closingStatus(exercise: SessionExercise): ExerciseStatus =
        if (exercise.sets.isEmpty()) ExerciseStatus.SKIPPED else ExerciseStatus.COMPLETED
}

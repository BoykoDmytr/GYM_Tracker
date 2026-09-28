package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.ExerciseHistoryEntry
import com.boykodmytr.gymtracker.domain.model.ExerciseSetRecord
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface WorkoutRepository {
    fun observeSession(id: String): Flow<WorkoutSession?>
    fun observeInProgressSession(): Flow<WorkoutSession?>

    /** Completed sessions with dates in [from, to] (inclusive), newest first. */
    fun observeCompletedSummaries(from: LocalDate, to: LocalDate): Flow<List<SessionSummary>>
    fun observeAllCompletedSummaries(): Flow<List<SessionSummary>>
    fun observeLastCompletedTemplateId(programId: String): Flow<String?>

    /** Starts a session from a template. Returns the existing session if one is already in progress. */
    suspend fun startSession(templateId: String): String
    suspend fun finishSession(sessionId: String, endedAt: Instant)
    suspend fun deleteSession(sessionId: String)
    suspend fun updateSessionNotes(sessionId: String, notes: String)

    suspend fun logSet(sessionExerciseId: String, input: SetInput): String
    suspend fun updateSet(setId: String, input: SetInput)
    suspend fun deleteSet(setId: String)

    suspend fun setCurrentExercise(sessionId: String, sessionExerciseId: String)
    suspend fun setExerciseStatus(sessionExerciseId: String, status: ExerciseStatus)
    suspend fun startRest(sessionId: String, startedAt: Instant, endsAt: Instant)
    suspend fun clearRest(sessionId: String)

    /** Sets from the most recent completed workout that contained this exercise. */
    suspend fun lastPerformance(exerciseId: String, excludeSessionId: String? = null): List<SetLog>
    fun observeExerciseRecords(exerciseId: String): Flow<List<ExerciseSetRecord>>
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistoryEntry>>
}

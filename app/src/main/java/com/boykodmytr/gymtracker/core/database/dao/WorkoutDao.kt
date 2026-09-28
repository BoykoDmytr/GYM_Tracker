package com.boykodmytr.gymtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.core.database.relation.ExerciseHistoryRow
import com.boykodmytr.gymtracker.core.database.relation.ExerciseSetRow
import com.boykodmytr.gymtracker.core.database.relation.SessionSummaryRow
import com.boykodmytr.gymtracker.core.database.relation.SessionWithExercises
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT id FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun getInProgressSessionId(): String?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeSession(id: String): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeInProgressSession(): Flow<SessionWithExercises?>

    @Query("UPDATE workout_session SET current_exercise_id = :exerciseId, updated_at = :now WHERE id = :sessionId")
    suspend fun setCurrentExercise(sessionId: String, exerciseId: String, now: Instant)

    @Query(
        "UPDATE workout_session SET rest_started_at = :startedAt, rest_ends_at = :endsAt, updated_at = :now " +
            "WHERE id = :sessionId",
    )
    suspend fun setRest(sessionId: String, startedAt: Instant?, endsAt: Instant?, now: Instant)

    @Query("UPDATE workout_session SET notes = :notes, updated_at = :now WHERE id = :sessionId")
    suspend fun setNotes(sessionId: String, notes: String, now: Instant)

    @Insert
    suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    @Query("SELECT * FROM session_exercise WHERE session_id = :sessionId ORDER BY order_index")
    suspend fun getSessionExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("UPDATE session_exercise SET status = :status WHERE id = :id")
    suspend fun setExerciseStatus(id: String, status: ExerciseStatus)

    @Insert
    suspend fun insertSet(set: SetLogEntity)

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Query("SELECT * FROM set_log WHERE id = :id")
    suspend fun getSet(id: String): SetLogEntity?

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("SELECT * FROM set_log WHERE session_exercise_id = :sessionExerciseId ORDER BY set_number")
    suspend fun getSets(sessionExerciseId: String): List<SetLogEntity>

    @Query("SELECT COUNT(*) FROM set_log WHERE session_exercise_id = :sessionExerciseId")
    suspend fun countSets(sessionExerciseId: String): Int

    @Query("UPDATE set_log SET set_number = :setNumber WHERE id = :id")
    suspend fun setSetNumber(id: String, setNumber: Int)

    @Query(
        """
        SELECT s.id, s.name, s.date, s.started_at, s.ended_at, s.status,
            (SELECT COUNT(DISTINCT se.id) FROM session_exercise se
                JOIN set_log l ON l.session_exercise_id = se.id WHERE se.session_id = s.id) AS exercise_count,
            (SELECT COUNT(*) FROM set_log l
                JOIN session_exercise se ON se.id = l.session_exercise_id WHERE se.session_id = s.id) AS set_count,
            (SELECT COALESCE(SUM(l.weight_kg * l.reps), 0) FROM set_log l
                JOIN session_exercise se ON se.id = l.session_exercise_id WHERE se.session_id = s.id) AS volume_kg
        FROM workout_session s
        WHERE s.status = 'COMPLETED' AND s.date BETWEEN :from AND :to
        ORDER BY s.started_at DESC
        """,
    )
    fun observeCompletedSummaries(from: LocalDate, to: LocalDate): Flow<List<SessionSummaryRow>>

    @Query(
        "SELECT template_id FROM workout_session WHERE status = 'COMPLETED' AND program_id = :programId " +
            "AND template_id IS NOT NULL ORDER BY started_at DESC LIMIT 1",
    )
    fun observeLastCompletedTemplateId(programId: String): Flow<String?>

    @Query(
        """
        SELECT se.id FROM session_exercise se
        JOIN workout_session s ON s.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND s.status = 'COMPLETED'
            AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
            AND EXISTS (SELECT 1 FROM set_log l WHERE l.session_exercise_id = se.id)
        ORDER BY s.started_at DESC LIMIT 1
        """,
    )
    suspend fun lastSessionExerciseId(exerciseId: String, excludeSessionId: String?): String?

    @Query(
        """
        SELECT se.exercise_id AS exercise_id, s.id AS session_id, s.date AS date, s.started_at AS started_at,
            l.set_number AS set_number, l.weight_kg AS weight_kg, l.reps AS reps, l.rpe AS rpe, l.is_failure AS is_failure
        FROM set_log l
        JOIN session_exercise se ON se.id = l.session_exercise_id
        JOIN workout_session s ON s.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND s.status = 'COMPLETED'
        ORDER BY s.started_at, l.set_number
        """,
    )
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetRow>>

    @Query(
        """
        SELECT se.exercise_id AS exercise_id, s.id AS session_id, s.date AS date, s.started_at AS started_at,
            l.set_number AS set_number, l.weight_kg AS weight_kg, l.reps AS reps, l.rpe AS rpe, l.is_failure AS is_failure
        FROM set_log l
        JOIN session_exercise se ON se.id = l.session_exercise_id
        JOIN workout_session s ON s.id = se.session_id
        WHERE s.status = 'COMPLETED'
        ORDER BY s.started_at, l.set_number
        """,
    )
    fun observeAllExerciseSets(): Flow<List<ExerciseSetRow>>

    @Query(
        """
        SELECT se.exercise_id AS exercise_id, e.name AS name, COUNT(DISTINCT s.id) AS session_count,
            MAX(s.started_at) AS last_performed_at
        FROM session_exercise se
        JOIN workout_session s ON s.id = se.session_id
        JOIN exercise e ON e.id = se.exercise_id
        WHERE s.status = 'COMPLETED' AND EXISTS (SELECT 1 FROM set_log l WHERE l.session_exercise_id = se.id)
        GROUP BY se.exercise_id, e.name
        ORDER BY last_performed_at DESC
        """,
    )
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistoryRow>>
}

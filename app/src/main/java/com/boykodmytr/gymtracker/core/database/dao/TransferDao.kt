package com.boykodmytr.gymtracker.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseImageEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.core.database.relation.SessionWithExercises
import java.time.Instant
import java.time.LocalDate

/** One logged set (or a workout without sets: exercise and set columns null), for duplicate checks. */
data class SessionSetRow(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val name: String,
    val date: LocalDate,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "exercise_id") val exerciseId: String?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
)

data class SessionExerciseNameRow(
    val id: String,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
)

/** Bulk reads and writes for CSV export/import and for merging duplicate exercises. */
@Dao
interface TransferDao {
    // --- Export and duplicate detection ---

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'COMPLETED' ORDER BY date, started_at")
    suspend fun getCompletedSessions(): List<SessionWithExercises>

    @Query(
        """
        SELECT s.id AS session_id, s.name AS name, s.date AS date, s.started_at AS started_at,
            se.exercise_id AS exercise_id, l.weight_kg AS weight_kg, l.reps AS reps
        FROM workout_session s
        LEFT JOIN session_exercise se ON se.session_id = s.id
        LEFT JOIN set_log l ON l.session_exercise_id = se.id
        WHERE s.status = 'COMPLETED'
        """,
    )
    suspend fun getCompletedSetRows(): List<SessionSetRow>

    @Query("SELECT * FROM exercise")
    suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM measurement_type ORDER BY order_index, name")
    suspend fun getMeasurementTypes(): List<MeasurementTypeEntity>

    @Query("SELECT * FROM body_measurement ORDER BY date, created_at")
    suspend fun getMeasurements(): List<BodyMeasurementEntity>

    // --- Import ---

    @Insert
    suspend fun insertExercises(items: List<ExerciseEntity>)

    @Insert
    suspend fun insertSessions(items: List<WorkoutSessionEntity>)

    @Insert
    suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    @Insert
    suspend fun insertSets(items: List<SetLogEntity>)

    @Insert
    suspend fun insertMeasurements(items: List<BodyMeasurementEntity>)

    @Insert
    suspend fun insertMeasurementTypes(items: List<MeasurementTypeEntity>)

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM measurement_type")
    suspend fun maxMeasurementTypeOrder(): Int

    // --- Undo an import (ids come in chunks below SQLite's variable limit) ---

    @Query("DELETE FROM workout_session WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<String>): Int

    @Query("DELETE FROM body_measurement WHERE id IN (:ids)")
    suspend fun deleteMeasurements(ids: List<String>): Int

    @Query(
        "DELETE FROM exercise WHERE id = :id " +
            "AND NOT EXISTS (SELECT 1 FROM session_exercise WHERE exercise_id = :id) " +
            "AND NOT EXISTS (SELECT 1 FROM template_exercise WHERE exercise_id = :id)",
    )
    suspend fun deleteExerciseIfUnused(id: String): Int

    @Query(
        "DELETE FROM measurement_type WHERE id = :id AND is_built_in = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM body_measurement WHERE type_id = :id)",
    )
    suspend fun deleteMeasurementTypeIfUnused(id: String): Int

    // --- Merge two exercises ---

    @Query("SELECT id, exercise_name FROM session_exercise WHERE exercise_id = :exerciseId")
    suspend fun sessionExerciseNames(exerciseId: String): List<SessionExerciseNameRow>

    @Query("SELECT COUNT(DISTINCT session_id) FROM session_exercise WHERE exercise_id = :exerciseId")
    suspend fun countSessions(exerciseId: String): Int

    /** Workouts that contain both exercises; after a merge they list the exercise twice. */
    @Query(
        "SELECT COUNT(*) FROM (SELECT session_id FROM session_exercise WHERE exercise_id IN (:first, :second) " +
            "GROUP BY session_id HAVING COUNT(DISTINCT exercise_id) = 2)",
    )
    suspend fun countSessionsWithBoth(first: String, second: String): Int

    @Query("UPDATE session_exercise SET exercise_id = :to, exercise_name = :name WHERE exercise_id = :from")
    suspend fun moveSessionExercises(from: String, to: String, name: String)

    @Query("UPDATE session_exercise SET exercise_id = :exerciseId, exercise_name = :name WHERE id = :id AND exercise_id = :currentExerciseId")
    suspend fun restoreSessionExercise(id: String, currentExerciseId: String, exerciseId: String, name: String)

    @Query("SELECT id FROM template_exercise WHERE exercise_id = :exerciseId")
    suspend fun templateExerciseIds(exerciseId: String): List<String>

    @Query("UPDATE template_exercise SET exercise_id = :to WHERE exercise_id = :from")
    suspend fun moveTemplateExercises(from: String, to: String)

    @Query("UPDATE template_exercise SET exercise_id = :exerciseId WHERE id = :id AND exercise_id = :currentExerciseId")
    suspend fun restoreTemplateExercise(id: String, currentExerciseId: String, exerciseId: String)

    @Query("SELECT * FROM exercise_image WHERE exercise_id = :exerciseId ORDER BY position")
    suspend fun images(exerciseId: String): List<ExerciseImageEntity>

    @Query("UPDATE exercise_image SET exercise_id = :exerciseId, position = :position WHERE id = :id")
    suspend fun moveImage(id: String, exerciseId: String, position: Int)

    @Query("UPDATE exercise SET notes = :notes, updated_at = :now WHERE id = :id")
    suspend fun setExerciseNotes(id: String, notes: String, now: Instant)
}

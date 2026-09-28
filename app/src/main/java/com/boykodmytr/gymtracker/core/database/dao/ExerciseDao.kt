package com.boykodmytr.gymtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseImageEntity
import com.boykodmytr.gymtracker.core.database.relation.ExerciseWithImages
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observeWithImages(id: String): Flow<ExerciseWithImages?>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Query("UPDATE exercise SET name = :name, notes = :notes, updated_at = :now WHERE id = :id")
    suspend fun update(id: String, name: String, notes: String, now: Instant)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM template_exercise WHERE exercise_id = :id")
    suspend fun countTemplateUsages(id: String): Int

    @Query("SELECT COUNT(DISTINCT session_id) FROM session_exercise WHERE exercise_id = :id")
    suspend fun countSessionUsages(id: String): Int

    @Insert
    suspend fun insertImage(image: ExerciseImageEntity)

    @Query("SELECT * FROM exercise_image WHERE id = :id")
    suspend fun getImage(id: String): ExerciseImageEntity?

    @Query("SELECT * FROM exercise_image WHERE exercise_id = :exerciseId ORDER BY position")
    suspend fun getImages(exerciseId: String): List<ExerciseImageEntity>

    @Query("DELETE FROM exercise_image WHERE id = :id")
    suspend fun deleteImage(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercise_image WHERE exercise_id = :exerciseId")
    suspend fun maxImagePosition(exerciseId: String): Int
}

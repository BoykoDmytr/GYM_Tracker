package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import kotlinx.coroutines.flow.Flow

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** The item is referenced by programs or history and was kept. */
    data class InUse(val templateUsages: Int, val historyUsages: Int) : DeleteResult
}

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercise(id: String): Flow<ExerciseDetails?>
    suspend fun createExercise(name: String, notes: String): String
    suspend fun updateExercise(id: String, name: String, notes: String)
    suspend fun deleteExercise(id: String): DeleteResult

    /** Copies the picked image into app storage, so it survives the source being deleted. */
    suspend fun addImage(exerciseId: String, sourceUri: String)
    suspend fun deleteImage(imageId: String)
}

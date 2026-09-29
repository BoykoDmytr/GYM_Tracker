package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import kotlinx.coroutines.flow.Flow

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** The item is referenced by programs or history and was kept. */
    data class InUse(val templateUsages: Int, val historyUsages: Int) : DeleteResult
}

/** Why a picked picture could not be added; each reason gets its own message for the user. */
class ImageImportException(
    val reason: Reason,
    cause: Throwable? = null,
    /** MIME type reported by the source, when known – shown for unsupported formats. */
    val mimeType: String? = null,
) : Exception("Image import failed: $reason${mimeType?.let { " ($it)" }.orEmpty()}", cause) {
    enum class Reason {
        /** The file is gone, or the app lost the permission to read it. */
        UNREADABLE,

        /** Not an image Android can decode (e.g. SVG), or a damaged file. */
        UNSUPPORTED_FORMAT,

        /** Did not fit into memory even downscaled. */
        TOO_LARGE,

        /** The copy could not be written, usually because the phone storage is full. */
        STORAGE,
    }
}

interface ExerciseRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercise(id: String): Flow<ExerciseDetails?>
    suspend fun createExercise(name: String, notes: String): String
    suspend fun updateExercise(id: String, name: String, notes: String)
    suspend fun deleteExercise(id: String): DeleteResult

    /**
     * Copies the picked image into app storage, so it survives the source being deleted.
     * @throws ImageImportException when the picture cannot be read, decoded or stored.
     */
    suspend fun addImage(exerciseId: String, sourceUri: String)
    suspend fun deleteImage(imageId: String)
}

package com.boykodmytr.gymtracker.data.repository

import androidx.core.net.toUri
import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.dao.ExerciseDao
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseImageEntity
import com.boykodmytr.gymtracker.data.image.ExerciseImageStorage
import com.boykodmytr.gymtracker.domain.model.Exercise
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import com.boykodmytr.gymtracker.domain.model.ExerciseImage
import com.boykodmytr.gymtracker.domain.repository.DeleteResult
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.time.Clock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val exerciseDao: ExerciseDao,
    private val imageStorage: ExerciseImageStorage,
    private val clock: Clock,
) : ExerciseRepository {

    // SQLite's NOCASE only folds ASCII; sort Cyrillic names with a locale-aware collator instead.
    private val collator = Collator.getInstance(Locale.forLanguageTag("uk"))

    override fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { list -> list.map { it.toDomain() }.sortedWith(compareBy(collator) { it.name }) }

    override fun observeExercise(id: String): Flow<ExerciseDetails?> =
        exerciseDao.observeWithImages(id).map { row ->
            row?.let {
                ExerciseDetails(
                    exercise = it.exercise.toDomain(),
                    images = it.images.sortedBy { img -> img.position }.map { img ->
                        ExerciseImage(
                            id = img.id,
                            exerciseId = img.exerciseId,
                            path = imageStorage.file(img.fileName).absolutePath,
                            position = img.position,
                        )
                    },
                )
            }
        }

    override suspend fun createExercise(name: String, notes: String): String {
        val now = clock.instant()
        val id = newId()
        exerciseDao.insert(ExerciseEntity(id, name.trim(), notes.trim(), now, now))
        return id
    }

    override suspend fun updateExercise(id: String, name: String, notes: String) =
        exerciseDao.update(id, name.trim(), notes.trim(), clock.instant())

    override suspend fun deleteExercise(id: String): DeleteResult {
        var removedFiles = emptyList<String>()
        val result = db.withTransaction {
            val templates = exerciseDao.countTemplateUsages(id)
            val history = exerciseDao.countSessionUsages(id)
            if (templates > 0 || history > 0) {
                DeleteResult.InUse(templates, history)
            } else {
                removedFiles = exerciseDao.getImages(id).map { it.fileName }
                exerciseDao.delete(id)
                DeleteResult.Deleted
            }
        }
        removedFiles.forEach { imageStorage.delete(it) }
        return result
    }

    override suspend fun addImage(exerciseId: String, sourceUri: String) {
        val fileName = imageStorage.import(sourceUri.toUri())
        exerciseDao.insertImage(
            ExerciseImageEntity(
                id = newId(),
                exerciseId = exerciseId,
                fileName = fileName,
                position = exerciseDao.maxImagePosition(exerciseId) + 1,
                createdAt = clock.instant(),
            ),
        )
    }

    override suspend fun deleteImage(imageId: String) {
        val image = exerciseDao.getImage(imageId) ?: return
        exerciseDao.deleteImage(imageId)
        imageStorage.delete(image.fileName)
    }
}

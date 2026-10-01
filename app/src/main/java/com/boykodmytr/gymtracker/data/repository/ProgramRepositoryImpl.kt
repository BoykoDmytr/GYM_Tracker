package com.boykodmytr.gymtracker.data.repository

import androidx.room.withTransaction
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.dao.ProgramDao
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity
import com.boykodmytr.gymtracker.domain.logic.Supersets
import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgramRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val programDao: ProgramDao,
    private val clock: Clock,
) : ProgramRepository {

    override fun observePrograms(): Flow<List<Program>> =
        programDao.observePrograms().map { list -> list.map { it.toDomain() } }

    override fun observeProgram(id: String): Flow<Program?> = programDao.observeProgram(id).map { it?.toDomain() }

    override fun observeActiveProgram(): Flow<Program?> = programDao.observeActiveProgram().map { it?.toDomain() }

    override fun observeTemplate(id: String): Flow<WorkoutTemplate?> =
        programDao.observeTemplate(id).map { it?.toDomain() }

    override suspend fun createProgram(name: String, description: String): String = db.withTransaction {
        val now = clock.instant()
        val isFirst = programDao.countPrograms() == 0
        val id = newId()
        programDao.insertProgram(
            ProgramEntity(
                id = id,
                name = name.trim(),
                description = description.trim(),
                isActive = isFirst,
                startedOn = if (isFirst) today() else null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    override suspend fun updateProgram(id: String, name: String, description: String) =
        programDao.updateProgram(id, name.trim(), description.trim(), clock.instant())

    override suspend fun deleteProgram(id: String) = db.withTransaction {
        programDao.deleteProgram(id)
        if (programDao.getActiveProgram() == null) {
            programDao.getFirstProgram()?.let { programDao.activate(it.id, today(), clock.instant()) }
        }
    }

    override suspend fun setActiveProgram(id: String) = db.withTransaction {
        val now = clock.instant()
        programDao.clearActive(now)
        programDao.activate(id, today(), now)
    }

    override suspend fun addTemplate(programId: String, name: String): String = db.withTransaction {
        val now = clock.instant()
        val id = newId()
        programDao.insertTemplate(
            WorkoutTemplateEntity(
                id = id,
                programId = programId,
                name = name.trim(),
                orderIndex = programDao.getTemplates(programId).size,
                createdAt = now,
                updatedAt = now,
            ),
        )
        id
    }

    override suspend fun renameTemplate(id: String, name: String) =
        programDao.renameTemplate(id, name.trim(), clock.instant())

    override suspend fun deleteTemplate(id: String) = db.withTransaction {
        val template = programDao.getTemplate(id) ?: return@withTransaction
        programDao.deleteTemplate(id)
        programDao.getTemplates(template.programId).forEachIndexed { index, t ->
            if (t.orderIndex != index) programDao.setTemplateOrder(t.id, index)
        }
    }

    override suspend fun moveTemplate(id: String, offset: Int) = db.withTransaction {
        val template = programDao.getTemplate(id) ?: return@withTransaction
        val reordered = programDao.getTemplates(template.programId).moved(id, offset) { it.id }
        reordered.forEachIndexed { index, t -> programDao.setTemplateOrder(t.id, index) }
    }

    override suspend fun addTemplateExercise(templateId: String, exerciseId: String, target: SetTarget, restSeconds: Int?) =
        db.withTransaction {
            val now = clock.instant()
            programDao.insertTemplateExercises(
                listOf(
                    TemplateExerciseEntity(
                        id = newId(),
                        templateId = templateId,
                        exerciseId = exerciseId,
                        orderIndex = programDao.getTemplateExercises(templateId).size,
                        setsMin = target.setsMin,
                        setsMax = target.setsMax,
                        repsMin = target.repsMin,
                        repsMax = target.repsMax,
                        targetWeightKg = target.weightKg,
                        restSeconds = restSeconds,
                        notes = "",
                        createdAt = now,
                        updatedAt = now,
                    ),
                ),
            )
        }

    override suspend fun updateTemplateExercise(id: String, target: SetTarget, restSeconds: Int?, notes: String) {
        val existing = programDao.getTemplateExercise(id) ?: return
        programDao.updateTemplateExercise(
            existing.copy(
                setsMin = target.setsMin,
                setsMax = target.setsMax,
                repsMin = target.repsMin,
                repsMax = target.repsMax,
                targetWeightKg = target.weightKg,
                restSeconds = restSeconds,
                notes = notes.trim(),
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun removeTemplateExercise(id: String) = db.withTransaction {
        val existing = programDao.getTemplateExercise(id) ?: return@withTransaction
        programDao.deleteTemplateExercise(id)
        val rest = programDao.getTemplateExercises(existing.templateId)
        rest.forEachIndexed { index, te ->
            if (te.orderIndex != index) programDao.setTemplateExerciseOrder(te.id, index)
        }
        saveSupersets(rest, Supersets.normalize(rest.map { it.supersetId }, ::newId))
    }

    override suspend fun moveTemplateExercise(id: String, offset: Int) = db.withTransaction {
        val existing = programDao.getTemplateExercise(id) ?: return@withTransaction
        val reordered = programDao.getTemplateExercises(existing.templateId).moved(id, offset) { it.id }
        reordered.forEachIndexed { index, te -> programDao.setTemplateExerciseOrder(te.id, index) }
        // Moving an exercise into or out of a superset breaks it rather than silently growing it.
        saveSupersets(reordered, Supersets.normalize(reordered.map { it.supersetId }, ::newId))
    }

    override suspend fun setSupersetWithNext(id: String, linked: Boolean) = db.withTransaction {
        val existing = programDao.getTemplateExercise(id) ?: return@withTransaction
        val items = programDao.getTemplateExercises(existing.templateId)
        val index = items.indexOfFirst { it.id == id }
        val ids = items.map { it.supersetId }
        val updated = if (linked) Supersets.link(ids, index, ::newId) else Supersets.unlink(ids, index, ::newId)
        saveSupersets(items, updated)
    }

    private suspend fun saveSupersets(items: List<TemplateExerciseEntity>, ids: List<String?>) {
        items.zip(ids).forEach { (te, supersetId) ->
            if (te.supersetId != supersetId) programDao.setTemplateExerciseSuperset(te.id, supersetId)
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}

/** Returns a copy with the element identified by [id] moved by [offset] positions, clamped to bounds. */
internal fun <T> List<T>.moved(id: String, offset: Int, idOf: (T) -> String): List<T> {
    val from = indexOfFirst { idOf(it) == id }
    if (from == -1) return this
    val to = (from + offset).coerceIn(0, lastIndex)
    if (from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

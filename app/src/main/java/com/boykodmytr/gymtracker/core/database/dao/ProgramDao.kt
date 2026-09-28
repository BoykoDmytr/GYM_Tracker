package com.boykodmytr.gymtracker.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity
import com.boykodmytr.gymtracker.core.database.relation.ProgramWithTemplates
import com.boykodmytr.gymtracker.core.database.relation.TemplateWithExercises
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface ProgramDao {
    @Transaction
    @Query("SELECT * FROM program ORDER BY is_active DESC, created_at")
    fun observePrograms(): Flow<List<ProgramWithTemplates>>

    @Transaction
    @Query("SELECT * FROM program WHERE id = :id")
    fun observeProgram(id: String): Flow<ProgramWithTemplates?>

    @Transaction
    @Query("SELECT * FROM program WHERE is_active = 1 LIMIT 1")
    fun observeActiveProgram(): Flow<ProgramWithTemplates?>

    @Query("SELECT COUNT(*) FROM program")
    suspend fun countPrograms(): Int

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun getProgram(id: String): ProgramEntity?

    @Query("SELECT * FROM program WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProgram(): ProgramEntity?

    @Query("SELECT * FROM program ORDER BY created_at LIMIT 1")
    suspend fun getFirstProgram(): ProgramEntity?

    @Insert
    suspend fun insertProgram(program: ProgramEntity)

    @Query("UPDATE program SET name = :name, description = :description, updated_at = :now WHERE id = :id")
    suspend fun updateProgram(id: String, name: String, description: String, now: Instant)

    @Query("DELETE FROM program WHERE id = :id")
    suspend fun deleteProgram(id: String)

    @Query("UPDATE program SET is_active = 0, updated_at = :now WHERE is_active = 1")
    suspend fun clearActive(now: Instant)

    @Query("UPDATE program SET is_active = 1, started_on = :startedOn, updated_at = :now WHERE id = :id")
    suspend fun activate(id: String, startedOn: LocalDate, now: Instant)

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity)

    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getTemplate(id: String): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template WHERE program_id = :programId ORDER BY order_index")
    suspend fun getTemplates(programId: String): List<WorkoutTemplateEntity>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    fun observeTemplate(id: String): Flow<TemplateWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getTemplateWithExercises(id: String): TemplateWithExercises?

    @Query("UPDATE workout_template SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun renameTemplate(id: String, name: String, now: Instant)

    @Query("UPDATE workout_template SET order_index = :orderIndex WHERE id = :id")
    suspend fun setTemplateOrder(id: String, orderIndex: Int)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Insert
    suspend fun insertTemplateExercises(items: List<TemplateExerciseEntity>)

    @Query("SELECT * FROM template_exercise WHERE id = :id")
    suspend fun getTemplateExercise(id: String): TemplateExerciseEntity?

    @Query("SELECT * FROM template_exercise WHERE template_id = :templateId ORDER BY order_index")
    suspend fun getTemplateExercises(templateId: String): List<TemplateExerciseEntity>

    @Update
    suspend fun updateTemplateExercise(item: TemplateExerciseEntity)

    @Query("UPDATE template_exercise SET order_index = :orderIndex WHERE id = :id")
    suspend fun setTemplateExerciseOrder(id: String, orderIndex: Int)

    @Query("DELETE FROM template_exercise WHERE id = :id")
    suspend fun deleteTemplateExercise(id: String)
}

package com.boykodmytr.gymtracker.domain.repository

import com.boykodmytr.gymtracker.domain.model.Program
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface ProgramRepository {
    fun observePrograms(): Flow<List<Program>>
    fun observeProgram(id: String): Flow<Program?>
    fun observeActiveProgram(): Flow<Program?>
    fun observeTemplate(id: String): Flow<WorkoutTemplate?>

    suspend fun createProgram(name: String, description: String): String
    suspend fun updateProgram(id: String, name: String, description: String)
    suspend fun deleteProgram(id: String)
    suspend fun setActiveProgram(id: String)

    suspend fun addTemplate(programId: String, name: String): String
    suspend fun renameTemplate(id: String, name: String)
    suspend fun deleteTemplate(id: String)
    suspend fun moveTemplate(id: String, offset: Int)

    suspend fun addTemplateExercise(templateId: String, exerciseId: String, target: SetTarget, restSeconds: Int?)
    suspend fun updateTemplateExercise(id: String, target: SetTarget, restSeconds: Int?, notes: String)
    suspend fun removeTemplateExercise(id: String)
    suspend fun moveTemplateExercise(id: String, offset: Int)

    /** Joins the exercise with the next one in its workout into a superset, or splits them. */
    suspend fun setSupersetWithNext(id: String, linked: Boolean)
}

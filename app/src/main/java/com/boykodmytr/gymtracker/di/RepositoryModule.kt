package com.boykodmytr.gymtracker.di

import com.boykodmytr.gymtracker.data.repository.BodyRepositoryImpl
import com.boykodmytr.gymtracker.data.repository.ExerciseRepositoryImpl
import com.boykodmytr.gymtracker.data.repository.ProgramRepositoryImpl
import com.boykodmytr.gymtracker.data.repository.WorkoutRepositoryImpl
import com.boykodmytr.gymtracker.data.settings.SettingsRepositoryImpl
import com.boykodmytr.gymtracker.domain.repository.BodyRepository
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.ProgramRepository
import com.boykodmytr.gymtracker.domain.repository.SettingsRepository
import com.boykodmytr.gymtracker.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The only place that knows which implementation backs each repository – swap here for sync later. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository
    @Binds abstract fun bindProgramRepository(impl: ProgramRepositoryImpl): ProgramRepository
    @Binds abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository
    @Binds abstract fun bindBodyRepository(impl: BodyRepositoryImpl): BodyRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

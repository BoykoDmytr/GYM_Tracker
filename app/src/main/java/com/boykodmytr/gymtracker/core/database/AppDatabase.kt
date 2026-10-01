package com.boykodmytr.gymtracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.boykodmytr.gymtracker.core.database.dao.BodyDao
import com.boykodmytr.gymtracker.core.database.dao.ExerciseDao
import com.boykodmytr.gymtracker.core.database.dao.ProgramDao
import com.boykodmytr.gymtracker.core.database.dao.TransferDao
import com.boykodmytr.gymtracker.core.database.dao.WorkoutDao
import com.boykodmytr.gymtracker.core.database.entity.BodyMeasurementEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.ExerciseImageEntity
import com.boykodmytr.gymtracker.core.database.entity.MeasurementTypeEntity
import com.boykodmytr.gymtracker.core.database.entity.ProgramEntity
import com.boykodmytr.gymtracker.core.database.entity.SessionExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.SetLogEntity
import com.boykodmytr.gymtracker.core.database.entity.TemplateExerciseEntity
import com.boykodmytr.gymtracker.core.database.entity.UserProfileEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutSessionEntity
import com.boykodmytr.gymtracker.core.database.entity.WorkoutTemplateEntity

/**
 * Every schema change must come with a Migration in Migrations.kt (schemas are exported to
 * app/schemas so migrations can be tested) – never fallbackToDestructiveMigration on user data.
 * v2: superset_id on template_exercise and session_exercise.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        MeasurementTypeEntity::class,
        BodyMeasurementEntity::class,
        ExerciseEntity::class,
        ExerciseImageEntity::class,
        ProgramEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun bodyDao(): BodyDao
    abstract fun transferDao(): TransferDao

    companion object {
        const val NAME = "gym_tracker.db"
        const val VERSION = 2
    }
}

package com.boykodmytr.gymtracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: supersets. Only adds nullable columns; existing rows keep every value. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `template_exercise` ADD COLUMN `superset_id` TEXT")
        db.execSQL("ALTER TABLE `session_exercise` ADD COLUMN `superset_id` TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

package com.boykodmytr.gymtracker.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Copies the database files aside before Room upgrades them, so a failed or wrong migration can be
 * recovered by hand (files/db-backups/). Never blocks opening the database: a failed backup is logged.
 */
object DatabaseBackup {
    private const val TAG = "DatabaseBackup"
    private const val KEEP = 3

    fun beforeUpgrade(context: Context, name: String, targetVersion: Int) {
        val db = context.getDatabasePath(name)
        if (!db.exists()) return
        try {
            val version = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            if (version <= 0 || version >= targetVersion) return
            val dir = File(context.filesDir, "db-backups").apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val base = "${name.removeSuffix(".db")}-v$version-$stamp.db"
            for (suffix in listOf("", "-wal", "-shm")) {
                val source = File(db.path + suffix)
                if (source.exists()) source.copyTo(File(dir, base + suffix), overwrite = true)
            }
            dir.listFiles { f -> f.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP)
                ?.forEach { old -> listOf("", "-wal", "-shm").forEach { File(old.path + it).delete() } }
        } catch (e: Exception) {
            Log.w(TAG, "Backup before upgrade failed", e)
        }
    }
}

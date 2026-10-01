package com.boykodmytr.gymtracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.core.database.ALL_MIGRATIONS
import com.boykodmytr.gymtracker.core.database.AppDatabase
import com.boykodmytr.gymtracker.core.database.DatabaseBackup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a database exactly as schema v1 (from the exported app/schemas/…/1.json) with data in it,
 * then opens it with the current Room setup. Room checks the migrated schema against v2 on open, and
 * the test checks that every row survived and a backup of the v1 file was taken first.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() = cleanUp()

    @After
    fun tearDown() = cleanUp()

    private fun cleanUp() {
        context.deleteDatabase(name)
        File(context.filesDir, "db-backups").deleteRecursively()
    }

    @Test
    fun v1DatabaseKeepsAllDataAfterUpgrade() = runBlocking {
        createV1Database()

        DatabaseBackup.beforeUpgrade(context, name, AppDatabase.VERSION)
        val backups = File(context.filesDir, "db-backups").listFiles { f -> f.name.endsWith(".db") }.orEmpty()
        assertEquals(1, backups.size)
        assertTrue(backups[0].name.startsWith("migration-test-v1-"))

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val template = db.programDao().getTemplateWithExercises("t1")!!
            assertEquals("Низ", template.template.name)
            val te = template.exercises.single().templateExercise
            assertEquals(24.0, te.targetWeightKg!!, 0.0)
            assertEquals("пауза 1 с", te.notes)
            assertNull(te.supersetId)

            val session = db.workoutDao().getSessionExercises("s1").single()
            assertEquals("Підйоми на носки стоячи", session.exerciseName)
            assertNull(session.supersetId)
            assertEquals(2, db.workoutDao().countSets("se1"))
        } finally {
            db.close()
        }
        // Already v2: no second backup.
        DatabaseBackup.beforeUpgrade(context, name, AppDatabase.VERSION)
        assertEquals(1, File(context.filesDir, "db-backups").listFiles { f -> f.name.endsWith(".db") }!!.size)
    }

    private fun createV1Database() {
        val schema = listOf(File("schemas"), File("app/schemas"))
            .map { File(it, "com.boykodmytr.gymtracker.core.database.AppDatabase/1.json") }
            .first { it.exists() }
        val database = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            listOf(
                "INSERT INTO exercise VALUES ('e1', 'Підйоми на носки стоячи', '', 0, 0)",
                "INSERT INTO program VALUES ('p1', 'v11.2', '', 1, 20000, 0, 0)",
                "INSERT INTO workout_template VALUES ('t1', 'p1', 'Низ', 0, 0, 0)",
                "INSERT INTO template_exercise VALUES ('te1', 't1', 'e1', 0, 3, 3, 12, 15, 24.0, 90, 'пауза 1 с', 0, 0)",
                "INSERT INTO workout_session VALUES ('s1', 'p1', 't1', 'Низ', 20000, 1000, 2000, 'COMPLETED', '', NULL, NULL, NULL, 0, 0)",
                "INSERT INTO session_exercise VALUES ('se1', 's1', 'e1', 'Підйоми на носки стоячи', 0, 3, 3, 12, 15, 24.0, 90, 'COMPLETED', '')",
                "INSERT INTO set_log VALUES ('l1', 'se1', 1, 24.0, 12, NULL, 0, NULL, 1100, 1100)",
                "INSERT INTO set_log VALUES ('l2', 'se1', 2, 24.0, 12, NULL, 0, NULL, 1200, 1200)",
            ).forEach(db::execSQL)
            db.version = 1
        }
    }
}

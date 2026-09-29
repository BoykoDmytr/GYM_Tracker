package com.boykodmytr.gymtracker.data.transfer

import android.content.Context
import android.util.Log
import com.boykodmytr.gymtracker.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StoredExercise(val id: String, val name: String, val notes: String, val createdAt: Long, val updatedAt: Long)

@Serializable
data class MovedSessionExercise(val id: String, val oldName: String)

@Serializable
data class MovedImage(val id: String, val oldPosition: Int)

/** Everything needed to put a merged exercise back exactly as it was. */
@Serializable
data class MergeUndo(
    val source: StoredExercise,
    val targetId: String,
    val targetNotesBefore: String,
    val targetNotesAfter: String,
    val sessionExercises: List<MovedSessionExercise>,
    val templateExerciseIds: List<String>,
    val images: List<MovedImage>,
)

/**
 * The ids an import created (or what a merge moved), so it can be undone precisely later. Kept as
 * a small JSON file instead of a database table: no schema migration is needed, and the log never
 * touches the user's workout data itself.
 */
@Serializable
data class TransferRecord(
    val id: String,
    val kind: String,
    val createdAt: Long,
    val title: String,
    val sessionIds: List<String> = emptyList(),
    val measurementIds: List<String> = emptyList(),
    val exerciseIds: List<String> = emptyList(),
    val measurementTypeIds: List<String> = emptyList(),
    val sets: Int = 0,
    /** Workouts whose history a merge moved. */
    val sessionCount: Int = 0,
    val merge: MergeUndo? = null,
    val undone: Boolean = false,
)

@Serializable
private data class TransferLog(@SerialName("records") val records: List<TransferRecord> = emptyList())

@Singleton
class TransferHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val records = MutableStateFlow<List<TransferRecord>?>(null)

    suspend fun observe(): StateFlow<List<TransferRecord>?> {
        ensureLoaded()
        return records.asStateFlow()
    }

    suspend fun all(): List<TransferRecord> = ensureLoaded()

    suspend fun add(record: TransferRecord) = update { listOf(record) + it.take(MAX_RECORDS - 1) }

    suspend fun markUndone(id: String) = update { list -> list.map { if (it.id == id) it.copy(undone = true) else it } }

    private suspend fun ensureLoaded(): List<TransferRecord> = mutex.withLock {
        records.value ?: withContext(io) { read() }.also { records.value = it }
    }

    private suspend fun update(transform: (List<TransferRecord>) -> List<TransferRecord>) {
        ensureLoaded()
        mutex.withLock {
            val updated = transform(records.value.orEmpty())
            withContext(io) {
                // Write to a temporary file first so a crash never leaves half a log behind.
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(json.encodeToString(TransferLog.serializer(), TransferLog(updated)))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            records.value = updated
        }
    }

    private fun read(): List<TransferRecord> = try {
        if (file.exists()) json.decodeFromString(TransferLog.serializer(), file.readText()).records else emptyList()
    } catch (e: Exception) {
        Log.w("TransferHistory", "Unreadable transfer log, starting a new one", e)
        emptyList()
    }

    private companion object {
        const val FILE_NAME = "transfer_history.json"
        const val MAX_RECORDS = 30
    }
}

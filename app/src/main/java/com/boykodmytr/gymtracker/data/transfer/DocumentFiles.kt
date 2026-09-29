package com.boykodmytr.gymtracker.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.boykodmytr.gymtracker.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

data class TextDocument(val name: String, val text: String, val charset: String)

class DocumentTooLargeException(val limitBytes: Long) : IOException("File is larger than $limitBytes bytes")

/** Reads and writes the files the user picks through the system file picker (SAF). */
@Singleton
class DocumentFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun readText(uri: Uri): TextDocument = withContext(io) {
        val resolver = context.contentResolver
        val bytes = (resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_BYTES) throw DocumentTooLargeException(MAX_BYTES)
            }
            out.toByteArray()
        }
        val (text, charset) = decode(bytes)
        TextDocument(displayName(uri), text, charset)
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(io) {
        // "wt" truncates: overwriting a longer file must not leave its tail behind.
        (context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write $uri")).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.csv"

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024

        /**
         * UTF-8 (with or without BOM) and UTF-16 with BOM are recognised directly. Anything that is not
         * valid UTF-8 is read as Windows-1251: that is what Excel writes for plain "CSV" on a Windows
         * PC with Ukrainian or Russian regional settings.
         */
        fun decode(bytes: ByteArray): Pair<String, String> {
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16BE"
            }
            val strict = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return try {
                strict.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") to "UTF-8"
            } catch (e: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1251")) to "Windows-1251"
            }
        }
    }
}

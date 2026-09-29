package com.boykodmytr.gymtracker.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.boykodmytr.gymtracker.core.common.IoDispatcher
import com.boykodmytr.gymtracker.core.common.newId
import com.boykodmytr.gymtracker.domain.repository.ImageImportException
import com.boykodmytr.gymtracker.domain.repository.ImageImportException.Reason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Stores exercise pictures in app-private storage.
 *
 * Picker and document URIs are temporary grants, so every picked image is copied. It is downscaled
 * to [MAX_DIMENSION] and re-encoded as JPEG: a 12 MP phone photo shrinks from ~5 MB to ~300 KB, which
 * matters for storage and for backups.
 *
 * Decoding goes through [ImageDecoder] on Android 9+: it reads everything [BitmapFactory] does plus
 * HEIF (and AVIF on Android 12+), applies the EXIF orientation itself and can decode straight to the
 * target size. Android 8 uses [BitmapFactory] with manual EXIF rotation.
 */
@Singleton
class ExerciseImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    /** Copies the image behind [uri] into app storage and returns the new file name. */
    suspend fun import(uri: Uri): String = withContext(io) {
        val resolver = context.contentResolver
        // Read the source once: cloud and download providers may serve a stream that cannot be reopened cheaply.
        val bytes = readBytes(resolver, uri)
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(bytes, mimeType) else decodeWithBitmapFactory(bytes, mimeType)
        } catch (e: OutOfMemoryError) {
            throw ImageImportException(Reason.TOO_LARGE, e)
        }
        val opaque = bitmap.onWhiteBackground()
        if (opaque !== bitmap) bitmap.recycle()

        val fileName = "${newId()}.jpg"
        val target = file(fileName)
        try {
            val written = FileOutputStream(target).use { out -> opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            if (!written) throw IOException("JPEG encoder failed")
        } catch (e: IOException) {
            target.delete()
            throw ImageImportException(Reason.STORAGE, e)
        } finally {
            opaque.recycle()
        }
        fileName
    }

    suspend fun delete(fileName: String) {
        withContext(io) { file(fileName).delete() }
    }

    private fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray {
        val input = try {
            resolver.openInputStream(uri) ?: throw ImageImportException(Reason.UNREADABLE)
        } catch (e: FileNotFoundException) {
            throw ImageImportException(Reason.UNREADABLE, e)
        } catch (e: SecurityException) {
            throw ImageImportException(Reason.UNREADABLE, e)
        }
        return try {
            input.use {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_SOURCE_BYTES) throw ImageImportException(Reason.TOO_LARGE)
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            throw ImageImportException(Reason.UNREADABLE, e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(bytes: ByteArray, mimeType: String?): Bitmap = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            // Software pixels: hardware bitmaps cannot be drawn into a Canvas or compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = info.size.width to info.size.height
            val scale = MAX_DIMENSION.toFloat() / max(width, height)
            if (scale < 1f) {
                decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
            }
        }
    } catch (e: IOException) {
        // DecodeException and friends: not an image Android understands, or a damaged file.
        throw ImageImportException(Reason.UNSUPPORTED_FORMAT, e, mimeType)
    }

    private fun decodeWithBitmapFactory(bytes: ByteArray, mimeType: String?): Bitmap {
        // With inJustDecodeBounds the decoder always returns null and only fills outWidth/outHeight,
        // so success is judged by the dimensions, never by the return value.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageImportException(Reason.UNSUPPORTED_FORMAT, mimeType = bounds.outMimeType ?: mimeType)
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw ImageImportException(Reason.UNSUPPORTED_FORMAT, mimeType = bounds.outMimeType ?: mimeType)
        val rotation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees }.getOrDefault(0)
        val result = decoded.scaledAndRotated(rotation)
        if (result !== decoded) decoded.recycle()
        return result
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        return sample
    }

    private fun Bitmap.scaledAndRotated(rotation: Int): Bitmap {
        val scale = minOf(1f, MAX_DIMENSION.toFloat() / max(width, height))
        if (scale == 1f && rotation == 0) return this
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    /**
     * JPEG has no transparency: a transparent PNG/WebP (typical for exercise illustrations from the
     * web) would turn black, hiding black line art. Flatten such images onto white, and convert
     * unusual pixel formats (e.g. 16-bit HDR) to plain 8-bit colour the JPEG encoder handles everywhere.
     */
    private fun Bitmap.onWhiteBackground(): Bitmap {
        if (!hasAlpha() && config == Bitmap.Config.ARGB_8888) return this
        val result = createBitmap(width, height)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@onWhiteBackground, 0f, 0f, null)
        }
        return result
    }

    private companion object {
        const val DIRECTORY = "exercise_images"
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85

        /** Far above any phone photo (a 200 MP JPEG is ~40 MB); protects memory from absurd files. */
        const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
    }
}

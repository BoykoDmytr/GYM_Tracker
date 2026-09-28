package com.boykodmytr.gymtracker.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.boykodmytr.gymtracker.core.common.IoDispatcher
import com.boykodmytr.gymtracker.core.common.newId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Stores exercise pictures in app-private storage.
 *
 * Photo-picker URIs are temporary, so every picked image is copied. It is downscaled to
 * [MAX_DIMENSION] and re-encoded as JPEG: a 12 MP phone photo shrinks from ~5 MB to ~300 KB, which
 * matters for storage and for future backups.
 */
@Singleton
class ExerciseImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun file(fileName: String): File = File(directory, fileName)

    suspend fun import(uri: Uri): String = withContext(io) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open $uri")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $uri")
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)

        val result = decoded.scaledAndRotated(rotation)
        val fileName = "${newId()}.jpg"
        FileOutputStream(file(fileName)).use { out -> result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        if (result !== decoded) decoded.recycle()
        result.recycle()
        fileName
    }

    suspend fun delete(fileName: String) {
        withContext(io) { file(fileName).delete() }
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

    private companion object {
        const val DIRECTORY = "exercise_images"
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
    }
}

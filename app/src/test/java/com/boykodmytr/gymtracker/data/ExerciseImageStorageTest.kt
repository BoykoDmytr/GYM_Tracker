package com.boykodmytr.gymtracker.data

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boykodmytr.gymtracker.data.image.ExerciseImageStorage
import com.boykodmytr.gymtracker.domain.repository.ImageImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Picked pictures reach the app as content:// URIs from the photo picker, the Downloads provider or
 * a file manager. [DocumentsLikeProvider] stands in for them, serving files the way those providers
 * do (file descriptor + MIME type), so the whole decode → downscale → JPEG path runs for real.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
open class ExerciseImageStorageTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var storage: ExerciseImageStorage
    private lateinit var sourceDir: File

    @Before
    fun setUp() {
        storage = ExerciseImageStorage(app, Dispatchers.Unconfined)
        sourceDir = File(app.cacheDir, "picked").apply { mkdirs() }
        DocumentsLikeProvider.root = sourceDir
        Robolectric.setupContentProvider(DocumentsLikeProvider::class.java, AUTHORITY)
    }

    @Test
    fun jpegFromGalleryIsCopied() {
        val stored = import(contentUri(write("IMG_2041.jpg", photo(1200, 900), Bitmap.CompressFormat.JPEG)))
        assertEquals(1200, stored.width)
        assertEquals(900, stored.height)
    }

    @Test
    fun pngWithTransparencyIsFlattenedOnWhite() {
        val picture = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(100f, 50f, 200f, 150f, Paint().apply { color = Color.BLACK })
        }
        val stored = import(contentUri(write("bench-press.png", picture, Bitmap.CompressFormat.PNG)))
        val corner = stored.getPixel(5, 5)
        val centre = stored.getPixel(150, 100)
        // JPEG is lossy: compare channels with a tolerance.
        assertTrue("transparent area must become white, was ${Integer.toHexString(corner)}", Color.red(corner) > 240 && Color.blue(corner) > 240)
        assertTrue("drawing must stay dark, was ${Integer.toHexString(centre)}", Color.red(centre) < 30)
    }

    @Test
    fun webpDownloadedFromBrowserIsCopied() {
        @Suppress("DEPRECATION") // WEBP_LOSSY needs API 30; the deprecated constant works everywhere
        val format = Bitmap.CompressFormat.WEBP
        val stored = import(contentUri(write("squat-technique.webp", photo(800, 600), format)))
        assertEquals(800, stored.width)
    }

    @Test
    fun fileUriIsCopied() {
        val stored = import(Uri.fromFile(write("local.jpg", photo(640, 480), Bitmap.CompressFormat.JPEG)))
        assertEquals(640, stored.width)
    }

    @Test
    fun largeImageIsDownscaled() {
        val stored = import(contentUri(write("huge.jpg", photo(6000, 4000), Bitmap.CompressFormat.JPEG)))
        assertEquals(1600, stored.width)
        assertTrue("height ${stored.height}", stored.height in 1066..1067)
    }

    @Test
    fun exifOrientationIsApplied() {
        val file = write("portrait.jpg", photo(400, 200), Bitmap.CompressFormat.JPEG)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val stored = import(contentUri(file))
        assertEquals(200, stored.width)
        assertEquals(400, stored.height)
    }

    @Test
    fun svgIsReportedAsUnsupportedFormat() {
        val file = File(sourceDir, "icon.svg").apply { writeText("""<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"/>""") }
        val error = importFailure(contentUri(file))
        assertEquals(ImageImportException.Reason.UNSUPPORTED_FORMAT, error.reason)
        assertEquals("image/svg+xml", error.mimeType)
    }

    @Test
    fun damagedFileIsReportedAsUnsupportedFormat() {
        val file = File(sourceDir, "broken.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)) }
        assertEquals(ImageImportException.Reason.UNSUPPORTED_FORMAT, importFailure(contentUri(file)).reason)
    }

    @Test
    fun missingFileIsReportedAsUnreadable() {
        assertEquals(ImageImportException.Reason.UNREADABLE, importFailure(contentUri(File(sourceDir, "deleted.jpg"))).reason)
    }

    @Test
    fun failedImportLeavesNoFiles() {
        importFailure(contentUri(File(sourceDir, "deleted.jpg")))
        val dir = File(app.filesDir, "exercise_images")
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    private fun import(uri: Uri): Bitmap {
        val name = runBlocking { storage.import(uri) }
        val file = storage.file(name)
        assertTrue(file.exists())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        return BitmapFactory.decodeFile(file.path)
    }

    private fun importFailure(uri: Uri): ImageImportException {
        try {
            runBlocking { storage.import(uri) }
        } catch (e: ImageImportException) {
            return e
        }
        fail("import of $uri should have failed")
        error("unreachable")
    }

    private fun write(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat): File =
        File(sourceDir, name).also { file -> FileOutputStream(file).use { bitmap.compress(format, 90, it) } }

    /** A picture with some detail rather than a flat colour, like a real photo. */
    private fun photo(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(90, 140, 200))
        Canvas(this).drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, Paint().apply { color = Color.rgb(230, 120, 40) })
    }

    private fun contentUri(file: File): Uri = Uri.parse("content://$AUTHORITY/document/${file.name}")

    companion object {
        const val AUTHORITY = "com.boykodmytr.gymtracker.test.documents"
    }
}

/** Serves files by name with a MIME type from the extension, like MediaStore / DownloadsProvider. */
class DocumentsLikeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String? = when (uri.lastPathSegment?.substringAfterLast('.')) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(root, uri.lastPathSegment!!), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = File(root, uri.lastPathSegment!!)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf(file.name, file.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        lateinit var root: File
    }
}

/** The same cases on Android 8, where decoding goes through BitmapFactory and manual EXIF rotation. */
@Config(application = Application::class, sdk = [27])
class ExerciseImageStorageApi27Test : ExerciseImageStorageTest()

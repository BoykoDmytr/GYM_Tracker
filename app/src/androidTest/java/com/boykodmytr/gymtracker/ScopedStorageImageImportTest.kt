package com.boykodmytr.gymtracker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boykodmytr.gymtracker.data.image.ExerciseImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Runs on a real Android (the CI emulator): pictures are put into MediaStore the way the camera or
 * gallery stores photos (Pictures/) and the way a browser stores downloads (Download/), then copied
 * through their real content:// URIs — the same URIs the photo picker and file picker hand out.
 */
@RunWith(AndroidJUnit4::class)
class ScopedStorageImageImportTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private lateinit var storage: ExerciseImageStorage
    private val inserted = mutableListOf<Uri>()
    private val stored = mutableListOf<String>()

    @Before
    fun setUp() {
        // MediaStore.Downloads and RELATIVE_PATH exist since Android 10.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        storage = ExerciseImageStorage(context, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        inserted.forEach { runCatching { resolver.delete(it, null, null) } }
        stored.forEach { runCatching { storage.file(it).delete() } }
    }

    @Test
    fun galleryPhotoJpeg() {
        val uri = insert(galleryCollection(), "gym_tracker_test_photo.jpg", "image/jpeg", "Pictures/GymTrackerTest", encode(photo(4000, 3000), Bitmap.CompressFormat.JPEG))
        val result = import(uri)
        assertEquals(1600, result.width)
        assertEquals(1200, result.height)
    }

    @Test
    fun browserDownloadPngWithTransparency() {
        val picture = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(200f, 100f, 400f, 300f, Paint().apply { color = Color.BLACK })
        }
        val uri = insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "gym_tracker_test_illustration.png", "image/png", "Download/GymTrackerTest", encode(picture, Bitmap.CompressFormat.PNG))
        val result = import(uri)
        assertTrue(Color.red(result.getPixel(10, 10)) > 240)
        assertTrue(Color.red(result.getPixel(300, 200)) < 30)
    }

    @Test
    fun browserDownloadWebp() {
        @Suppress("DEPRECATION")
        val bytes = encode(photo(1200, 800), Bitmap.CompressFormat.WEBP)
        val uri = insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "gym_tracker_test_technique.webp", "image/webp", "Download/GymTrackerTest", bytes)
        assertEquals(1200, import(uri).width)
    }

    @Test
    fun browserDownloadRotatedJpeg() {
        val file = File(context.cacheDir, "rotated.jpg")
        file.writeBytes(encode(photo(800, 400), Bitmap.CompressFormat.JPEG))
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val uri = insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "gym_tracker_test_rotated.jpg", "image/jpeg", "Download/GymTrackerTest", file.readBytes())
        val result = import(uri)
        assertEquals(400, result.width)
        assertEquals(800, result.height)
    }

    private fun import(uri: Uri): Bitmap {
        val name = runBlocking { storage.import(uri) }
        stored += name
        val bitmap = BitmapFactory.decodeFile(storage.file(name).path)
        assertTrue("stored copy must be a readable JPEG", bitmap != null)
        return bitmap
    }

    private fun galleryCollection(): Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun insert(collection: Uri, name: String, mime: String, relativePath: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values)) { "MediaStore refused $name" }
        inserted += uri
        requireNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()

    private fun photo(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(90, 140, 200))
        Canvas(this).drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, Paint().apply { color = Color.rgb(230, 120, 40) })
    }
}

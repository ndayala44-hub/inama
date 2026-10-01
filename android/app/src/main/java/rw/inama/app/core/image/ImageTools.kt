package rw.inama.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rw.inama.app.domain.ai.ImageFeatures
import rw.inama.app.domain.image.PhotoAnalyzer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class PreparedPhoto(val file: File, val features: ImageFeatures)

/**
 * Turns a camera capture or gallery pick into a small, upright JPEG kept in app storage, and
 * measures it on-device (PhotoAnalyzer) before any upload. Low-data mode uses a smaller size and
 * stronger compression — a typical result is 150–300 KB.
 */
class ImageTools(private val context: Context) {

    val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** A fresh file the camera can write into. */
    fun newCaptureFile(): File = File(File(context.cacheDir, "capture").apply { mkdirs() }, "cap_${UUID.randomUUID()}.jpg")

    suspend fun prepare(source: Uri, maxSide: Int, quality: Int): PreparedPhoto = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        fun open(): InputStream = resolver.openInputStream(source) ?: error("Cannot open photo")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode photo")

        val rotation = open().use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }

        val scaled = scaleToMax(decoded, maxSide)
        val upright = if (rotation != 0f) {
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(rotation) }, true)
        } else scaled

        val out = File(photosDir, "photo_${UUID.randomUUID()}.jpg")
        FileOutputStream(out).use { upright.compress(Bitmap.CompressFormat.JPEG, quality, it) }

        val features = analyze(upright)
        if (upright !== decoded) decoded.recycle()
        if (scaled !== decoded && scaled !== upright) scaled.recycle()
        upright.recycle()
        PreparedPhoto(out, features)
    }

    private fun scaleToMax(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun analyze(bitmap: Bitmap): ImageFeatures {
        val small = scaleToMax(bitmap, 256)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val features = PhotoAnalyzer.analyze(pixels, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return features
    }

    /**
     * Copies a bundled sample photo (assets/samples) to the cache and returns a file Uri, so the
     * demo samples go through exactly the same [prepare] path as camera and gallery photos.
     */
    suspend fun copySampleToCache(assetName: String): Uri = withContext(Dispatchers.IO) {
        val out = File(File(context.cacheDir, "capture").apply { mkdirs() }, "sample_${UUID.randomUUID()}.jpg")
        context.assets.open("samples/$assetName").use { input -> FileOutputStream(out).use { input.copyTo(it) } }
        Uri.fromFile(out)
    }

    fun deletePhotos() {
        photosDir.listFiles()?.forEach { it.delete() }
        File(context.cacheDir, "capture").listFiles()?.forEach { it.delete() }
    }
}

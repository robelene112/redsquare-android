package nl.davidengelen.redsquare.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** OS-prunable public rasters, isolated by node, signer, signed version and source. */
internal object AvatarImageCache {
    private const val MAX_FILE = 384 * 1024
    private val lock = Any()

    suspend fun load(context: Context, backend: SaitoBackend, author: String, proof: String?, source: String): Bitmap =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${backend.id}\n$author\n$proof\n$source".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val directory = File(context.cacheDir, "profile-avatar-images")
            val file = File(directory, "$digest.png")
            val cached = synchronized(lock) {
                try {
                    if (file.length() !in 1..MAX_FILE.toLong()) null
                    else {
                        val bytes = file.readBytes()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth !in 1..256 || bounds.outHeight !in 1..256) null
                        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { file.setLastModified(System.currentTimeMillis()) }
                    }
                } catch (_: Exception) { null }
            }
            if (cached != null) return@withContext cached
            val image = PostImageLoader.load(source, backend, "avatar:$author:$proof")
            currentCoroutineContext().ensureActive()
            val ratio = minOf(1f, 256f / maxOf(image.width, image.height))
            val scaled = Bitmap.createScaledBitmap(image, maxOf(1, (image.width * ratio).toInt()),
                maxOf(1, (image.height * ratio).toInt()), true)
            val raster = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else requireNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false))
            // Failed/OS-pruned disk storage must not hide an otherwise available public picture.
            try {
                val bytes = ByteArrayOutputStream().also { check(raster.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
                if (bytes.size <= MAX_FILE) synchronized(lock) {
                    directory.mkdirs()
                    val temporary = File(directory, "$digest.tmp")
                    try {
                        temporary.writeBytes(bytes)
                        check(temporary.renameTo(file))
                    } finally { temporary.delete() }
                    val files = directory.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }.orEmpty()
                    var total = 0L
                    files.forEachIndexed { index, entry ->
                        total += entry.length()
                        if (index >= 64 || total > 8 * 1024 * 1024) entry.delete()
                    }
                }
            } catch (_: Exception) { /* Public image caching is best effort. */ }
            raster
        }
}

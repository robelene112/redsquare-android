package nl.davidengelen.redsquare.spaces

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.feed.PostImageLoader
import nl.davidengelen.redsquare.saito.SaitoBackend

/** Account-owned memory only. Lazy rows can reuse a raster on their first frame
 * without decoding, hashing, disk access or revealing private images on disk. */
internal class SpaceAvatarImages(private val owner: String, private val backend: SaitoBackend) {
    private data class Source(val space: String, val image: String)
    private val lock = Any()
    private var closed = false
    private val cache = object : LruCache<Source, Bitmap>(16 * 1024 * 1024) {
        // At most64 entries even when the source is a tiny community icon.
        override fun sizeOf(key: Source, value: Bitmap) = maxOf(256 * 1024, value.allocationByteCount)
    }

    fun loaded(space: String, source: String): ImageBitmap? = synchronized(lock) {
        if (closed) null else cache.get(Source(space, source))?.asImageBitmap()
    }

    suspend fun load(space: String, source: String): ImageBitmap = withContext(Dispatchers.IO) {
        val key = Source(space, source)
        val cached = synchronized(lock) {
            if (closed) throw CancellationException("Community images have been retired")
            cache.get(key)
        }
        val raster = cached ?: PostImageLoader.load(source, backend, "$owner:space:$space")
        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            if (closed) throw CancellationException("Community images have been retired")
            cache.put(key, raster)
        }
        raster.asImageBitmap()
    }

    fun close() = synchronized(lock) { closed = true; cache.evictAll() }
}

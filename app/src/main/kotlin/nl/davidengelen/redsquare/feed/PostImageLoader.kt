package nl.davidengelen.redsquare.feed

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.RedSquareImages
import nl.davidengelen.redsquare.saito.SaitoBackend
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Public post media only. No wallet, draft or authenticated HTTP state. */
internal object PostImageLoader {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val slots = Semaphore(2)
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        // Charge at least256KiB so tiny rasters cannot accumulate more than64 entries.
        override fun sizeOf(key: String, value: Bitmap) = maxOf(256 * 1024, value.allocationByteCount)
    }

    suspend fun load(source: String, backend: SaitoBackend): Bitmap = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val key = "${backend.id}:$digest"
        cache.get(key) ?: slots.withPermit {
            cache.get(key) ?: decode(bytes(source, backend)).also { cache.put(key, it) }
        }
    }

    private fun bytes(source: String, backend: SaitoBackend): ByteArray {
        require(RedSquareImages.safeSource(source) == source)
        if (source.startsWith("data:", ignoreCase = true)) {
            val payload = source.substringAfter(',')
            require(payload.length <= 4 * ((MAX_BYTES + 2) / 3))
            return Base64.decode(payload, Base64.NO_WRAP).also { require(it.size <= MAX_BYTES) }
        }
        val url = if (source.startsWith('/')) backend.webUrl + source else source
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful)
            val body = requireNotNull(response.body)
            require(body.contentLength() <= MAX_BYTES)
            body.byteStream().use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        require(width in 1..16_384 && height in 1..16_384 && width.toLong() * height <= 100_000_000)
        val scale = minOf(1.0, 1024.0 / maxOf(width, height))
        decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

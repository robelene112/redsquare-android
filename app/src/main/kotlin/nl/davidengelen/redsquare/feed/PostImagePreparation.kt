package nl.davidengelen.redsquare.feed

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** URI grants are consumed here; recoverable drafts contain normalized bytes, never URIs. */
internal object PostImagePreparation {
    private const val MAX_SOURCE_BYTES = 32 * 1024 * 1024
    private val readers = pool("post-image-read", 2)
    private val cleanup = pool("post-image-close", 4)

    suspend fun normalize(resolver: ContentResolver, uri: Uri, maxChars: Int, cropRatio: Int? = null): String {
        require(maxChars >= 1024)
        require(cropRatio == null || cropRatio in listOf(1, 3))
        val bytes = read(resolver, uri)
        return withContext(Dispatchers.IO) {
            var bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                require(info.mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/gif"))
                val width = info.size.width
                val height = info.size.height
                require(width in 1..16_384 && height in 1..16_384 && width.toLong() * height <= 100_000_000)
                val scale = minOf(1.0, 1920.0 / width, 1024.0 / height)
                val targetWidth = maxOf(cropRatio ?: 1, (width * scale).toInt())
                val targetHeight = maxOf(1, (height * scale).toInt())
                decoder.setTargetSize(targetWidth, targetHeight)
                cropRatio?.let { ratio ->
                    val cropHeight = minOf(targetHeight, targetWidth / ratio)
                    val cropWidth = cropHeight * ratio
                    val left = (targetWidth - cropWidth) / 2
                    val top = (targetHeight - cropHeight) / 2
                    decoder.setCrop(Rect(left, top, left + cropWidth, top + cropHeight))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                currentCoroutineContext().ensureActive()
                // New RGB pixels flatten alpha to white and omit source EXIF/GPS metadata.
                val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                bitmap.recycle()
                bitmap = flattened
                repeat(6) {
                    for (quality in listOf(85, 65, 45, 25)) {
                        currentCoroutineContext().ensureActive()
                        val output = ByteArrayOutputStream()
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                        val jpeg = output.toByteArray()
                        if (jpeg.size <= 380 * 1024) {
                            val result = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
                            if (result.length <= maxChars) return@withContext result
                        }
                    }
                    if (bitmap.width == 1 && bitmap.height == 1) throw IOException("Image cannot fit the post")
                    val smallerHeight = maxOf(1, bitmap.height / 2)
                    val smallerWidth = cropRatio?.let { it * smallerHeight } ?: maxOf(1, bitmap.width / 2)
                    val smaller = Bitmap.createScaledBitmap(bitmap, smallerWidth, smallerHeight, true)
                    if (smaller !== bitmap) bitmap.recycle()
                    bitmap = smaller
                }
                throw IOException("Image cannot fit the post")
            } finally { bitmap.recycle() }
        }
    }

    private suspend fun read(resolver: ContentResolver, uri: Uri): ByteArray {
        require(uri.scheme == "content")
        try {
            return withTimeout(15_000) {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    val descriptor = AtomicReference<ParcelFileDescriptor?>()
                    continuation.invokeOnCancellation {
                        // Binder cancellation and descriptor closing may block; keep both off the UI thread.
                        runCatching { cleanup.execute { runCatching { signal.cancel() } } }
                        runCatching { cleanup.execute { runCatching { descriptor.getAndSet(null)?.close() } } }
                    }
                    try {
                        readers.execute {
                            if (!continuation.isActive) return@execute
                            try {
                                val opened = requireNotNull(resolver.openFileDescriptor(uri, "r", signal))
                                descriptor.set(opened)
                                val bytes = ParcelFileDescriptor.AutoCloseInputStream(opened).use { input ->
                                    check(continuation.isActive)
                                    require(opened.statSize <= MAX_SOURCE_BYTES)
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        check(continuation.isActive)
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(count > 0 && output.size() + count <= MAX_SOURCE_BYTES)
                                        output.write(buffer, 0, count)
                                    }
                                    opened.checkError()
                                    output.toByteArray()
                                }
                                if (continuation.isActive) continuation.resume(bytes)
                            } catch (failure: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(failure)
                            } finally { runCatching { descriptor.getAndSet(null)?.close() } }
                        }
                    } catch (failure: Exception) { continuation.resumeWithException(failure) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("Image provider timed out")
        }
    }

    private fun pool(name: String, queue: Int) = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queue), { runnable -> Thread(runnable, name).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())
}

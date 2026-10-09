package nl.davidengelen.redsquare.feed

import android.content.Context
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareFeed
import java.io.File
import java.io.IOException

/** Public post cache. It contains no wallet or private Chat state. */
class AndroidFeedCache(context: Context, backend: SaitoBackend, private val threadId: String? = null) {
    init { require(threadId == null || threadId.matches(Regex("[0-9a-f]{128}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, if (threadId == null)
        "feed.${backend.id}.v1" else "conversation.${backend.id}.$threadId.v1"))
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()

    data class SavedPosts(val posts: List<FeedPost>, val unreadable: Boolean)

    fun load(): SavedPosts = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return@synchronized SavedPosts(emptyList(), false)
        }
        try {
            val bytes = file.openRead().use { input ->
                val buffer = ByteArray(8192)
                val collected = java.io.ByteArrayOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(collected.size() + count <= MAX_BYTES)
                    collected.write(buffer, 0, count)
                }
                collected.toByteArray()
            }
            val envelopes = gson.fromJson(bytes.toString(Charsets.UTF_8), JsonArray::class.java)
            require(envelopes.size() <= MAX_POSTS)
            var unreadable = false
            val posts = envelopes.mapNotNull {
                try { RedSquareFeed.decode(it.asString).takeIf { post ->
                    if (threadId == null) post.parentId.isEmpty() else post.threadId == threadId
                } }
                catch (_: Exception) { unreadable = true; null }
            }
            SavedPosts(posts.distinctBy { it.id }.sortedByDescending { it.createdAt }, unreadable)
        } catch (_: Exception) { SavedPosts(emptyList(), true) }
    }

    fun save(posts: List<FeedPost>) = synchronized(lock) {
        require(!file.baseFile.exists() || file.baseFile.isFile)
        var remaining = MAX_BYTES - 2
        val encoded = mutableListOf<ByteArray>()
        for (post in posts.distinctBy { it.id }.sortedByDescending { it.createdAt }.take(MAX_POSTS)) {
            val item = gson.toJson(post.envelope).toByteArray(Charsets.UTF_8)
            if (item.size + 1 > remaining) break
            encoded.add(item)
            remaining -= item.size + 1
        }
        if (posts.isNotEmpty() && encoded.isEmpty()) throw IOException("Post cache is too large")
        val output = file.startWrite()
        try {
            output.write('['.code)
            encoded.forEachIndexed { index, bytes ->
                if (index > 0) output.write(','.code)
                output.write(bytes)
            }
            output.write(']'.code)
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        // AtomicFile logs some commit failures instead of throwing. Check the actual file.
        file.openRead().buffered().use { input ->
            if (input.read() != '['.code) throw IOException("Post cache commit failed")
            encoded.forEachIndexed { index, bytes ->
                if (index > 0 && input.read() != ','.code) throw IOException("Post cache commit failed")
                for (byte in bytes) {
                    if (input.read() != (byte.toInt() and 255)) throw IOException("Post cache commit failed")
                }
            }
            if (input.read() != ']'.code || input.read() != -1) throw IOException("Post cache commit failed")
        }
    }

    companion object {
        const val MAX_POSTS = 100
        private const val MAX_BYTES = 16 * 1024 * 1024
        private val lock = Any()
    }
}

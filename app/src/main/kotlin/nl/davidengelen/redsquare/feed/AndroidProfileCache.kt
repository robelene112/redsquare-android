package nl.davidengelen.redsquare.feed

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.*
import java.io.File
import java.io.IOException

/** Bounded public posts and signed like records. Never stores wallet or private Chat data. */
internal class AndroidProfileCache(context: Context, backend: SaitoBackend) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "profile.${backend.id}.v1"))
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()
    data class SavedProfile(val posts: List<FeedPost>, val likes: List<ProfileLike>, val unreadable: Boolean)

    fun load(): SavedProfile = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return@synchronized SavedProfile(emptyList(), emptyList(), false)
        }
        try {
            val bytes = file.openRead().use { input ->
                val collected = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(collected.size() + count <= MAX_BYTES)
                    collected.write(buffer, 0, count)
                }
                collected.toByteArray()
            }
            val envelopes = gson.fromJson(bytes.toString(Charsets.UTF_8), JsonArray::class.java)
            require(envelopes.size() <= MAX_ENTRIES)
            val posts = mutableListOf<FeedPost>()
            val likes = mutableListOf<ProfileLike>()
            var unreadable = false
            for (entry in envelopes) {
                val envelope = entry.asString
                try { posts.add(RedSquareFeed.decode(envelope)) }
                catch (_: Exception) {
                    try { likes.add(RedSquareLikes.decode(envelope)) }
                    catch (_: Exception) { unreadable = true }
                }
            }
            SavedProfile(posts.distinctBy { it.id }, likes.distinctBy { it.id }, unreadable)
        } catch (_: Exception) { SavedProfile(emptyList(), emptyList(), true) }
    }

    fun save(posts: List<FeedPost>, likes: List<ProfileLike>) = synchronized(lock) {
        require(!file.baseFile.exists() || file.baseFile.isFile)
        val entries = (posts.distinctBy { it.id }.map { it.createdAt to it.envelope } +
            likes.distinctBy { it.id }.map { it.createdAt to it.envelope })
            .sortedByDescending { it.first }.take(MAX_ENTRIES)
        var remaining = MAX_BYTES - 2
        val encoded = mutableListOf<ByteArray>()
        for ((_, envelope) in entries) {
            val item = gson.toJson(envelope).toByteArray(Charsets.UTF_8)
            if (item.size + 1 > remaining) break
            encoded.add(item)
            remaining -= item.size + 1
        }
        if (entries.isNotEmpty() && encoded.isEmpty()) throw IOException("Profile cache is too large")
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
        } catch (failure: Exception) { file.failWrite(output); throw failure }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        file.openRead().buffered().use { input ->
            if (input.read() != '['.code) throw IOException("Profile cache commit failed")
            encoded.forEachIndexed { index, bytes ->
                if (index > 0 && input.read() != ','.code) throw IOException("Profile cache commit failed")
                for (byte in bytes) {
                    if (input.read() != (byte.toInt() and 255)) throw IOException("Profile cache commit failed")
                }
            }
            if (input.read() != ']'.code || input.read() != -1) throw IOException("Profile cache commit failed")
        }
    }

    companion object {
        private const val MAX_ENTRIES = 200
        private const val MAX_BYTES = 16 * 1024 * 1024
        private val lock = Any()
    }
}

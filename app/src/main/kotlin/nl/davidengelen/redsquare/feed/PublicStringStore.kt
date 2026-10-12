package nl.davidengelen.redsquare.feed

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness
import java.io.File
import java.io.IOException

/** Atomic bounded public strings. Feature readers validate their own values and signed envelopes. */
internal class PublicStringStore(context: Context, name: String, private val maxEntries: Int) {
    init { require(name.matches(Regex("[a-zA-Z0-9.]+")) && name.length <= 240 && maxEntries in 1..1000) }
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()
    data class Loaded(val values: List<String>, val unreadable: Boolean)

    fun load(): Loaded = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return@synchronized Loaded(emptyList(), false)
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
            val entries = gson.fromJson(bytes.toString(Charsets.UTF_8), JsonArray::class.java)
            require(entries.size() <= maxEntries)
            var unreadable = false
            val values = entries.mapNotNull { entry ->
                if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) entry.asString
                else { unreadable = true; null }
            }
            Loaded(values, unreadable)
        } catch (_: Exception) { Loaded(emptyList(), true) }
    }

    fun save(values: List<String>) = synchronized(lock) {
        require(!file.baseFile.exists() || file.baseFile.isFile)
        val encoded = encode(values.asSequence())
        if (values.isNotEmpty() && encoded.isEmpty()) throw IOException("Public cache is too large")
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
        // AtomicFile logs some failures instead of throwing. Verify committed bytes.
        file.openRead().buffered().use { input ->
            if (input.read() != '['.code) throw IOException("Public cache commit failed")
            encoded.forEachIndexed { index, bytes ->
                if (index > 0 && input.read() != ','.code) throw IOException("Public cache commit failed")
                for (byte in bytes) {
                    if (input.read() != (byte.toInt() and 255)) throw IOException("Public cache commit failed")
                }
            }
            if (input.read() != ']'.code || input.read() != -1) throw IOException("Public cache commit failed")
        }
    }

    /** The retained prefix fits the exact encoding and entry budget used by save. */
    fun fittingEntryCount(values: Sequence<String>): Int = encode(values).size

    private fun encode(values: Sequence<String>): List<ByteArray> {
        var remaining = MAX_BYTES - 2
        val encoded = mutableListOf<ByteArray>()
        for (value in values.take(maxEntries)) {
            val item = gson.toJson(value).toByteArray(Charsets.UTF_8)
            if (item.size + 1 > remaining) break
            encoded.add(item)
            remaining -= item.size + 1
        }
        return encoded
    }

    /** Serialize read, feature validation/merge and commit across all store instances. */
    fun update(merge: (Loaded) -> List<String>) = synchronized(lock) { save(merge(load())) }

    companion object {
        private const val MAX_BYTES = 16 * 1024 * 1024
        // Shared by bounded filesystem fixture setup as well as all store operations.
        internal val lock = Any()
    }
}

package nl.davidengelen.redsquare.feed

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException

/** Atomic bounded public strings. Feature readers validate their own values and signed envelopes. */
internal class PublicStringStore(context: Context, name: String, private val maxEntries: Int) {
    init { require(name.matches(Regex("[a-zA-Z0-9.]+")) && name.length <= 240 && maxEntries in 1..2000) }
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()
    data class Loaded(val values: List<String>, val unreadable: Boolean)

    fun load(): Loaded = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return@synchronized Loaded(emptyList(), false)
        }
        try {
            file.openRead().use { input ->
                // AtomicFile publishes by rename. Bound the opened file before
                // decoding it without allocating a second copy of the archive.
                require(input.channel.size() <= MAX_BYTES)
                gson.newJsonReader(input.bufferedReader(Charsets.UTF_8)).use { reader ->
                    val values = mutableListOf<String>()
                    var read = 0
                    var unreadable = false
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(read++ < maxEntries)
                        if (reader.peek() == JsonToken.STRING) values.add(reader.nextString())
                        else {
                            unreadable = true
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                    require(reader.peek() == JsonToken.END_DOCUMENT)
                    Loaded(values, unreadable)
                }
            }
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
            val buffer = ByteArray(8192)
            encoded.forEachIndexed { index, bytes ->
                if (index > 0 && input.read() != ','.code) throw IOException("Public cache commit failed")
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(buffer, 0, minOf(buffer.size, bytes.size - offset))
                    if (count <= 0 || (0 until count).any { buffer[it] != bytes[offset + it] })
                        throw IOException("Public cache commit failed")
                    offset += count
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

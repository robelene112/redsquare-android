package nl.davidengelen.redsquare.gateway

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions

/** One runtime owns the state file; acknowledgement follows file and directory sync. */
internal class GatewayStateFile(private val directory: Path, private val origin: String) : Closeable {
    private val channel: FileChannel
    private val lock: java.nio.channels.FileLock
    private val file = directory.resolve("subscriptions.v1.json")
    init {
        Files.createDirectories(directory)
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        channel = FileChannel.open(directory.resolve("gateway.lock"), CREATE, WRITE)
        try { lock = checkNotNull(channel.tryLock()) { "Gateway state already in use" } }
        catch (failure: Exception) { channel.close(); throw failure }
        try { load() } catch (failure: Exception) { close(); throw failure }
    }

    fun load(): JsonObject = try { readState() }
        catch (failure: Exception) { throw IOException("Gateway state unavailable", failure) }

    private fun readState(): JsonObject {
        if (!Files.exists(file)) return JsonObject().apply {
            addProperty("version", 1); addProperty("origin", origin)
            add("subscriptions", JsonObject()); add("retired", JsonObject()); add("events", JsonObject()); add("gaps", JsonObject()); add("chains", JsonObject())
        }
        require(Files.size(file) in 1..MAX_BYTES)
        val state = Files.newBufferedReader(file, Charsets.UTF_8).use {
            requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(it, JsonObject::class.java))
        }
        require(state.keySet().containsAll(setOf("version", "origin", "subscriptions", "retired")) &&
            state.keySet().all { it in setOf("version", "origin", "subscriptions", "retired", "events", "gaps", "chains") } &&
            state.get("version").asBigDecimal.intValueExact() == 1 && state.get("origin").asString == origin)
        for (name in listOf("subscriptions", "retired")) {
            val records = state.getAsJsonObject(name)
            require(records.size() <= 2048 && records.entrySet().all {
                it.key.matches(Regex("[0-9a-f]{64}")) && it.value.isJsonObject
            })
        }
        if (!state.has("events")) state.add("events", JsonObject())
        val events = state.getAsJsonObject("events")
        require(events.size() <= 4096 && events.entrySet().all {
            it.key.matches(Regex("[0-9a-f]{64}")) && it.value.isJsonObject
        })
        if (!state.has("gaps")) state.add("gaps", JsonObject())
        require(state.getAsJsonObject("gaps").entrySet().all {
            it.key in setOf("owner", "official") && it.value.isJsonObject
        })
        if (!state.has("chains")) state.add("chains", JsonObject())
        require(state.getAsJsonObject("chains").entrySet().all {
            it.key in setOf("owner", "official") && it.value.isJsonObject
        })
        // Also resolves the uncertain directory-sync outcome of a previous attempted commit.
        syncDirectory()
        return state
    }

    fun save(state: JsonObject) {
        val bytes = state.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size.toLong() <= MAX_BYTES)
        val pending = directory.resolve("subscriptions.v1.pending")
        FileChannel.open(pending, CREATE, TRUNCATE_EXISTING, WRITE).use { output ->
            Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("rw-------"))
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) check(output.write(buffer) > 0)
            output.force(true)
        }
        Files.move(pending, file, ATOMIC_MOVE, REPLACE_EXISTING)
        syncDirectory()
        check(load() == state)
    }

    private fun syncDirectory() = FileChannel.open(directory, READ).use { it.force(true) }
    override fun close() { try { lock.release() } finally { channel.close() } }
    private companion object { const val MAX_BYTES = 4 * 1024 * 1024L }
}

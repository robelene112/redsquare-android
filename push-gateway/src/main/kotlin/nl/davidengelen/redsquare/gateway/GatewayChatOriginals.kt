package nl.davidengelen.redsquare.gateway

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions

/** Ciphertext originals live outside the small subscription journal, once per backend/signature. */
internal class GatewayChatOriginals(private val directory: Path) {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private data class Original(val envelope: String, val expiresAt: Long)

    fun save(backend: SaitoBackend, signature: String, envelope: String, expiresAt: Long, now: Long): Long {
        require(envelope.toByteArray(Charsets.UTF_8).size <= MAX_ORIGINAL && expiresAt > now)
        Files.createDirectories(directory)
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        cleanup(now)
        val target = path(backend, signature)
        if (Files.exists(target)) { val retained = read(target); sync(); return retained.expiresAt }
        val contents = JsonObject().apply {
            addProperty("version", 1); addProperty("expiresAt", expiresAt); addProperty("envelope", envelope)
        }.toString().toByteArray(Charsets.UTF_8)
        val entries = entries()
        if (entries.size >= MAX_FILES || entries.sumOf { Files.size(it) } + contents.size > MAX_TOTAL) {
            throw IOException("Chat original capacity reached")
        }
        val pending = directory.resolve("${backend.id}-$signature.pending")
        FileChannel.open(pending, CREATE, TRUNCATE_EXISTING, WRITE).use { output ->
            Files.setPosixFilePermissions(pending, PosixFilePermissions.fromString("rw-------"))
            val buffer = ByteBuffer.wrap(contents)
            while (buffer.hasRemaining()) check(output.write(buffer) > 0)
            output.force(true)
        }
        Files.move(pending, target, ATOMIC_MOVE, REPLACE_EXISTING)
        sync()
        check(read(target) == Original(envelope, expiresAt))
        return expiresAt
    }

    fun load(backend: SaitoBackend, signature: String, now: Long): String? {
        val target = path(backend, signature)
        if (!Files.exists(target)) return null
        return read(target).takeIf { it.expiresAt > now }?.envelope
    }

    fun cleanup(now: Long) {
        if (!Files.exists(directory)) return
        var changed = false
        for (entry in entries()) {
            val name = entry.fileName.toString()
            val expired = if (name.matches(Regex("(owner|official)-[0-9a-f]{128}\\.json"))) read(entry).expiresAt <= now
                else if (name.matches(Regex("(owner|official)-[0-9a-f]{128}\\.pending")))
                    now >= Files.getLastModifiedTime(entry).toMillis() && now - Files.getLastModifiedTime(entry).toMillis() >= RETENTION
                else false
            if (expired) { Files.delete(entry); changed = true }
        }
        if (changed) sync()
    }

    private fun path(backend: SaitoBackend, signature: String): Path {
        require(signature.matches(Regex("[0-9a-f]{128}")))
        return directory.resolve("${backend.id}-$signature.json")
    }
    private fun entries(): List<Path> = Files.list(directory).use { stream ->
        stream.limit(MAX_FILES + 1L).toList().also { if (it.size > MAX_FILES) throw IOException("Chat original capacity reached") }
    }
    private fun read(path: Path): Original = try {
        require(Files.size(path) in 1..(MAX_ORIGINAL + 1024).toLong())
        val body = Files.newBufferedReader(path, Charsets.UTF_8).use { gson.fromJson(it, JsonObject::class.java) }
        require(body.keySet() == setOf("version", "expiresAt", "envelope") && body.get("version").asBigDecimal.intValueExact() == 1)
        require(body.get("envelope").isJsonPrimitive && body.get("envelope").asJsonPrimitive.isString)
        Original(body.get("envelope").asString, body.get("expiresAt").asBigDecimal.longValueExact().also { require(it >= 0) })
    } catch (failure: Exception) { throw IOException("Chat original unavailable", failure) }
    private fun sync() = FileChannel.open(directory, READ).use { it.force(true) }

    companion object {
        const val RETENTION = 24 * 60 * 60 * 1000L
        private const val MAX_ORIGINAL = 8 * 1024 * 1024
        private const val MAX_TOTAL = 128 * 1024 * 1024L
        private const val MAX_FILES = 4096
    }
}

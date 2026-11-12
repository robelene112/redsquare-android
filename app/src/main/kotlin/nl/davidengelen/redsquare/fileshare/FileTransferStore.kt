package nl.davidengelen.redsquare.fileshare

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

internal enum class FilePhase {
    CONNECTING, WAITING, OFFERED, SENDING, RECEIVING, COMPLETE, SAVED, DECLINED, CANCELLED, INTERRUPTED, FAILED;
    val active get() = this in setOf(CONNECTING, WAITING, SENDING, RECEIVING)
    val delivered get() = this == COMPLETE || this == SAVED
}

internal enum class PreparationPhase { PREPARING, CANCELLED, INTERRUPTED, FAILED }
internal data class FilePreparation(val token: String, val owner: String, val backend: SaitoBackend,
    val contact: String, val endpoint: String, val source: String, val phase: PreparationPhase = PreparationPhase.PREPARING,
    val name: String? = null, val bytes: Long = 0)

internal data class FileTransfer(val token: String, val owner: String, val backend: SaitoBackend,
    val contact: String, val endpoint: String, val offer: Fileshare.Offer, val incoming: Boolean,
    val phase: FilePhase, val bytes: Long = 0, val createdAt: Long = System.currentTimeMillis(), val source: String? = null) {
    init { require(token.matches(Regex("[0-9a-f-]{36}"))) }
}

/** Streaming encrypted spool, independent of the Chat history lock. Names never become file paths. */
internal class FileTransferStore(private val context: Context, private val owner: String,
    private val backend: SaitoBackend, private val contact: String) {
    private val prefix = "fileshare.${digest("$owner:${backend.id}:$contact")}."
    private fun file(token: String, suffix: String) = File(context.noBackupFilesDir, "$prefix$token.$suffix")
    private fun metadata(token: String) = EncryptedLocalFile(context, file(token, "info").name,
        "fileshare.metadata.v1", "fileshare.")
    private fun aad(token: String) = "${context.packageName}:$prefix$token".toByteArray(Charsets.UTF_8)
    private val preparation = EncryptedLocalFile(context, "${prefix}preparation.info", "fileshare.metadata.v1", "fileshare.")

    fun savePreparation(value: FilePreparation?) {
        val body = JsonObject()
        value?.let {
            require(it.owner == owner && it.backend == backend && it.contact == contact && it.token.matches(Regex("[0-9a-f-]{36}")))
            body.addProperty("token", it.token); body.addProperty("endpoint", it.endpoint)
            body.addProperty("source", it.source); body.addProperty("phase", it.phase.name)
            it.name?.let { name -> body.addProperty("name", name) }; body.addProperty("bytes", it.bytes)
        }
        preparation.save(EncryptedLocalFile.Contents(1, body.toString()))
    }
    fun loadPreparation(): FilePreparation? {
        val saved = preparation.load() ?: return null
        require(saved.version == 1.toByte())
        val body = JsonParser.parseString(saved.text).asJsonObject
        if (body.size() == 0) return null
        val token = body.get("token").asString
        require(token.matches(Regex("[0-9a-f-]{36}")))
        return FilePreparation(token, owner, backend, contact, body.get("endpoint").asString,
            body.get("source").asString, PreparationPhase.valueOf(body.get("phase").asString),
            body.get("name")?.asString, Fileshare.number(body, "bytes"))
    }

    fun save(value: FileTransfer) {
        require(value.owner == owner && value.backend == backend && value.contact == contact)
        val body = JsonObject().apply {
            addProperty("token", value.token); addProperty("endpoint", value.endpoint)
            add("offer", value.offer.data()); addProperty("incoming", value.incoming)
            addProperty("phase", value.phase.name); addProperty("bytes", value.bytes)
            addProperty("createdAt", value.createdAt); value.source?.let { addProperty("source", it) }
        }
        metadata(value.token).save(EncryptedLocalFile.Contents(1, body.toString()))
    }

    fun load(): List<FileTransfer> = context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.name.matches(Regex("${Regex.escape(prefix)}[0-9a-f-]{36}\\.info(?:\\.bak)?")) }
        .map { it.name.removePrefix(prefix).removeSuffix(".bak").removeSuffix(".info") }.distinct().map { token ->
            val record = requireNotNull(metadata(token).load())
            require(record.version == 1.toByte())
            val body = JsonParser.parseString(record.text).asJsonObject
            require(body.get("token").asString == token)
            val value = FileTransfer(token, owner, backend, contact, body.get("endpoint").asString,
                Fileshare.readOffer(body.getAsJsonObject("offer")), body.get("incoming").asBoolean,
                FilePhase.valueOf(body.get("phase").asString), Fileshare.number(body, "bytes"),
                Fileshare.number(body, "createdAt"), body.get("source")?.asString)
            require(value.bytes <= value.offer.size)
            value
        }.sortedByDescending { it.createdAt }

    fun begin(value: FileTransfer): Spool {
        require(value.incoming)
        val overhead = (value.offer.size + Fileshare.CHUNK_BYTES - 1) / Fileshare.CHUNK_BYTES * 32
        require(context.noBackupFilesDir.usableSpace > value.offer.size + overhead + 1024 * 1024)
        return Spool(value.token, value.offer.size)
    }

    /** Unknown-length providers are read once into the same bounded encrypted format. */
    fun prepare(token: String, input: InputStream, progress: (Long) -> Unit): Long {
        Spool(token, 9_007_199_254_740_991L).use { spool ->
            val buffer = ByteArray(Fileshare.CHUNK_BYTES)
            while (true) {
                progress(spool.count)
                val size = input.read(buffer)
                if (size < 0) break
                check(size > 0 && context.noBackupFilesDir.usableSpace > size + 1024 * 1024)
                spool.append(buffer.copyOf(size))
            }
            check(spool.count > 0)
            spool.finish(spool.count)
            return spool.count
        }
    }
    fun discard(token: String) {
        for (suffix in listOf("part", "data", "inbox")) {
            val target = file(token, suffix)
            check(!target.exists() || target.delete())
        }
    }

    fun discardInbox(token: String) {
        val target = file(token, "inbox")
        check(!target.exists() || target.delete())
    }

    fun inbox(value: FileTransfer): FrameInbox {
        require(value.incoming && value.phase == FilePhase.RECEIVING)
        return FrameInbox(value.token, value.offer.size * 3 + Fileshare.MAX_FRAME_BYTES * 2L)
    }

    /**
     * Upstream browsers send without waiting for receipts. Keep their ordered frames on
     * encrypted temporary storage instead of retaining one controller event per chunk.
     * The RTC callback never waits for the controller or holds the Chat/wallet lock.
     */
    inner class FrameInbox(private val token: String, private val maximum: Long) : Closeable {
        private val target = file(token, "inbox")
        private val secret = key(create = true, context)
        private val storage = RandomAccessFile(target, "rw").apply { setLength(0) }
        private var readAt = 0L
        private var written = 0L
        private var sequence = 0L
        private var nextRead = 0L
        private var scheduled = false
        private var closed = false
        private var failure: Exception? = null

        /** Returns true only when the controller needs a new drain event. */
        @Synchronized fun append(text: String): Boolean {
            if (closed) return false
            if (failure == null) try {
                val bytes = text.toByteArray(Charsets.UTF_8)
                require(bytes.size in 1..Fileshare.MAX_FRAME_BYTES)
                val recordSize = bytes.size + 32L
                check(written <= maximum - recordSize && target.parentFile!!.usableSpace > recordSize + 1024 * 1024)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, secret)
                    updateAAD(frameAad(sequence, bytes.size))
                }
                storage.seek(written)
                storage.writeInt(bytes.size); storage.write(cipher.iv); storage.write(cipher.doFinal(bytes))
                written += recordSize; sequence++
            } catch (problem: Exception) { failure = problem }
            if (scheduled) return false
            scheduled = true
            return true
        }

        /** Releases the queue lock before the caller validates or sends an RTC receipt. */
        @Synchronized fun next(): String? {
            if (closed) return null
            failure?.let { throw it }
            if (readAt == written) {
                storage.setLength(0); readAt = 0; written = 0; scheduled = false
                return null
            }
            storage.seek(readAt)
            val length = storage.readInt()
            require(length in 1..Fileshare.MAX_FRAME_BYTES && length + 32L <= written - readAt)
            val iv = ByteArray(12).also(storage::readFully)
            val encrypted = ByteArray(length + 16).also(storage::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
                updateAAD(frameAad(nextRead, length))
            }
            val bytes = cipher.doFinal(encrypted)
            readAt += length + 32L; nextRead++
            return bytes.toString(Charsets.UTF_8)
        }

        private fun frameAad(index: Long, length: Int) = aad(token) + "inbox".toByteArray(Charsets.UTF_8) +
            ByteBuffer.allocate(12).putLong(index).putInt(length).array()

        @Synchronized override fun close() {
            if (!closed) {
                closed = true
                try { storage.close() } finally { check(!target.exists() || target.delete()) }
            }
        }
    }

    fun export(value: FileTransfer, destination: Uri) {
        require(value.incoming && value.phase in setOf(FilePhase.COMPLETE, FilePhase.SAVED))
        openPrepared(value).use { input ->
            requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { output -> input.copyTo(output, Fileshare.CHUNK_BYTES) }
        }
    }

    fun openPrepared(value: FileTransfer): InputStream = object : InputStream() {
        private val secret = key(create = false, context)
        private val input = DataInputStream(FileInputStream(file(value.token, "data")))
        private var authenticated = 0L
        private var buffer = ByteArray(0)
        private var offset = 0
        private var ended = false
        init { try { check(input.readInt() == MAGIC) } catch (failure: Exception) { input.close(); throw failure } }
        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 255
        }
        override fun read(destination: ByteArray, start: Int, size: Int): Int {
            require(start >= 0 && size >= 0 && start <= destination.size - size)
            if (size == 0) return 0
            if (offset == buffer.size) {
                buffer.fill(0)
                if (authenticated == value.offer.size) {
                    if (!ended) { check(input.read() == -1); ended = true }
                    return -1
                }
                val length = input.readInt()
                require(length in 1..Fileshare.CHUNK_BYTES && length <= value.offer.size - authenticated)
                val iv = ByteArray(12).also(input::readFully)
                val encrypted = ByteArray(length + 16).also(input::readFully)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
                    updateAAD(aad(value.token) + ByteBuffer.allocate(12).putLong(authenticated).putInt(length).array())
                }
                buffer = cipher.doFinal(encrypted); check(buffer.size == length)
                authenticated += length; offset = 0
            }
            val amount = minOf(size, buffer.size - offset)
            buffer.copyInto(destination, start, offset, offset + amount); offset += amount
            return amount
        }
        override fun close() { buffer.fill(0); input.close() }
    }

    inner class Spool(private val token: String, private val maximum: Long) : Closeable {
        private val target = file(token, "part")
        private val secret = key(create = true, context)
        private val output = FileOutputStream(target)
        private var closed = false
        var count = 0L; private set
        init { DataOutputStream(output).writeInt(MAGIC) }
        fun append(bytes: ByteArray) {
            check(!closed && bytes.size <= maximum - count)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, secret)
                updateAAD(aad(token) + ByteBuffer.allocate(12).putLong(count).putInt(bytes.size).array())
            }
            DataOutputStream(output).writeInt(bytes.size)
            output.write(cipher.iv); output.write(cipher.doFinal(bytes)); count += bytes.size
        }
        fun finish(expected: Long = maximum) {
            check(!closed && count == expected && count > 0)
            try {
                output.fd.sync(); output.close(); closed = true
                Files.move(target.toPath(), file(token, "data").toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { if (!closed) close() }
        }
        override fun close() {
            if (!closed) { closed = true; output.close(); target.delete() }
        }
    }

    companion object {
        private const val MAGIC = 0x46534831
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        @Synchronized private fun key(create: Boolean, context: Context): SecretKey {
            val alias = "fileshare.spool.v1"
            val existing = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as? SecretKey
            if (existing != null) return existing
            check(create)
            check(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.startsWith("fileshare.") && it.name.endsWith(".data") })
            return KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
    }
}

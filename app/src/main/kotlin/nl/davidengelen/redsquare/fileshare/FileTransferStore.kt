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
}

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
        return Spool(value)
    }
    fun discard(token: String) {
        for (suffix in listOf("part", "data")) {
            val target = file(token, suffix)
            check(!target.exists() || target.delete())
        }
    }

    fun export(value: FileTransfer, destination: Uri) {
        require(value.incoming && value.phase in setOf(FilePhase.COMPLETE, FilePhase.SAVED))
        FileInputStream(file(value.token, "data")).use { input ->
            val stream = DataInputStream(input)
            check(stream.readInt() == MAGIC)
            val secret = key(create = false, context)
            requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { output ->
                var written = 0L
                while (written < value.offer.size) {
                    val size = stream.readInt()
                    require(size in 1..Fileshare.CHUNK_BYTES && size <= value.offer.size - written)
                    val iv = ByteArray(12).also(stream::readFully)
                    val encrypted = ByteArray(size + 16).also(stream::readFully)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
                        updateAAD(aad(value.token) + ByteBuffer.allocate(12).putLong(written).putInt(size).array())
                    }
                    val bytes = cipher.doFinal(encrypted)
                    check(bytes.size == size)
                    output.write(bytes); bytes.fill(0); written += size
                }
                check(stream.read() == -1)
                output.flush()
            }
        }
    }

    inner class Spool(private val value: FileTransfer) : Closeable {
        private val target = file(value.token, "part")
        private val output = FileOutputStream(target)
        private val secret = key(create = true, context)
        private var closed = false
        private var count = 0L
        init { DataOutputStream(output).writeInt(MAGIC) }
        fun append(bytes: ByteArray) {
            check(!closed && bytes.size <= value.offer.size - count)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, secret)
                updateAAD(aad(value.token) + ByteBuffer.allocate(12).putLong(count).putInt(bytes.size).array())
            }
            DataOutputStream(output).writeInt(bytes.size)
            output.write(cipher.iv); output.write(cipher.doFinal(bytes)); count += bytes.size
        }
        fun finish() {
            check(!closed && count == value.offer.size)
            try {
                output.fd.sync(); output.close(); closed = true
                Files.move(target.toPath(), file(value.token, "data").toPath(), StandardCopyOption.ATOMIC_MOVE)
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

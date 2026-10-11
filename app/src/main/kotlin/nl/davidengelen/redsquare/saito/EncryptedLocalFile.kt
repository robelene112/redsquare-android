package nl.davidengelen.redsquare.saito

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only draft bytes. Feature adapters own their version and payload validation. */
internal class EncryptedLocalFile(private val context: Context, filename: String,
    private val keyAlias: String, private val familyPrefix: String,
    private val directorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))
    private val associatedData = "${context.packageName}:$filename".toByteArray(Charsets.UTF_8)

    init {
        require(filename.matches(Regex("[a-zA-Z0-9._-]{1,240}")) && filename.startsWith(familyPrefix))
    }

    data class Contents(val version: Byte, val text: String)

    fun load(): Contents? = synchronized(SaitoLocalState.lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        val key = requireNotNull(existingKey()) { "Draft storage key unavailable" }
        val encrypted = file.openRead().use { input ->
            val buffer = ByteArray(MAX_BYTES + 30)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                check(count > 0)
                size += count
            }
            require(size in 29 until buffer.size && buffer[0] in 1.toByte()..2.toByte())
            buffer.copyOf(size)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
            updateAAD(associatedData)
        }
        val plaintext = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            Contents(encrypted[0], text)
        } finally { plaintext.fill(0) }
    }

    /** beforeWrite records a validated attempt, never a successful commit. */
    fun save(contents: Contents, beforeWrite: () -> Unit = {}) = synchronized(SaitoLocalState.lock) {
        require(contents.version in 1.toByte()..2.toByte())
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(contents.text))
        val plaintext = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val encrypted = try {
            require(plaintext.size <= MAX_BYTES)
            val key = existingKey() ?: run {
                require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith(familyPrefix) } == true) {
                    "Existing drafts require recovery"
                }
                KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                    init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                    generateKey()
                }
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(associatedData)
            }
            byteArrayOf(contents.version) + cipher.iv + cipher.doFinal(plaintext)
        } finally { plaintext.fill(0) }
        require(!file.baseFile.exists() || file.baseFile.isFile)
        beforeWrite()
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { directorySync(directory) } finally { Os.close(directory) }
        check(load() == contents) { "Draft persistence verification failed" }
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(keyAlias, null) as? SecretKey

    companion object { private const val MAX_BYTES = 5 * 1024 * 1024 }
}

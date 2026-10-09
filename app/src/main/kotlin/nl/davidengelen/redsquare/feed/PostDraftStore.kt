package nl.davidengelen.redsquare.feed

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import nl.davidengelen.redsquare.saito.SaitoBackend

/** Unpublished text stays encrypted on this device, scoped to its wallet and backend. */
internal class PostDraftStore(private val context: Context, owner: String, backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "postdraft.${backend.id}.$owner.v1"))
    private val associatedData = "${context.packageName}:${file.baseFile.name}".toByteArray(Charsets.UTF_8)

    fun load(): String = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized ""
        val key = requireNotNull(existingKey()) { "Draft storage key unavailable" }
        val encrypted = file.openRead().use { input ->
            val buffer = ByteArray(MAX_BYTES + 30)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 29 until buffer.size && buffer[0] == 1.toByte())
            buffer.copyOf(size)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
            updateAAD(associatedData)
        }
        val text = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
        try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text)).toString()
        } finally { text.fill(0) }
    }

    fun save(text: String) = synchronized(lock) {
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val encrypted = try {
            require(plaintext.size <= MAX_BYTES)
            val key = existingKey() ?: run {
                require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("postdraft.") } == true) {
                    "Existing drafts require recovery"
                }
                KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                    init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                    generateKey()
                }
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(associatedData)
            }
            byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
        } finally { plaintext.fill(0) }
        require(!file.baseFile.exists() || file.baseFile.isFile)
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) { file.failWrite(output); throw failure }
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        check(load() == text) { "Draft persistence verification failed" }
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(KEY_ALIAS, null) as? SecretKey

    private companion object {
        const val KEY_ALIAS = "redsquare.postdraft.v1"
        const val MAX_BYTES = 1024 * 1024
        val lock = Any()
    }
}

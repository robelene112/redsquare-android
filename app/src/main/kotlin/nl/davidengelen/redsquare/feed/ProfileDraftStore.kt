package nl.davidengelen.redsquare.feed

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Unpublished profile changes stay encrypted and tied to their wallet and server. */
internal class ProfileDraftStore(private val context: Context, owner: String, backend: SaitoBackend) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "profiledraft.${backend.id}.$owner.v1"))
    private val associatedData = "${context.packageName}:${file.baseFile.name}".toByteArray(Charsets.UTF_8)
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): JsonObject = synchronized(SaitoLocalState.lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized JsonObject()
        val key = requireNotNull(existingKey()) { "Profile draft key unavailable" }
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
        val plaintext = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            requireNotNull(gson.fromJson(text, JsonObject::class.java)).also(::validate)
        } finally { plaintext.fill(0) }
    }

    fun save(changes: JsonObject) = synchronized(SaitoLocalState.lock) {
        validate(changes)
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(changes.toString()))
        val plaintext = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val encrypted = try {
            require(plaintext.size <= MAX_BYTES)
            val key = existingKey() ?: run {
                require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("profiledraft.") } == true) {
                    "Existing profile drafts require recovery"
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
        check(load() == changes) { "Profile draft persistence verification failed" }
    }

    private fun validate(changes: JsonObject) {
        require(changes.keySet().all { it == "description" })
        changes.get("description")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(KEY_ALIAS, null) as? SecretKey

    companion object {
        private const val KEY_ALIAS = "redsquare.profiledraft.v1"
        private const val MAX_BYTES = 5 * 1024 * 1024
    }
}

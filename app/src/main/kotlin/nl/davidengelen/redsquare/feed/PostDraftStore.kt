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
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.RedSquareFeed
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Unpublished text stays encrypted on this device, scoped to its wallet and backend. */
internal class PostDraftStore(private val context: Context, private val owner: String, backend: SaitoBackend,
    private val parent: FeedPost? = null) {
    init { require(owner.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir,
        "postdraft.${backend.id}.$owner${parent?.let { ".${it.id}" }.orEmpty()}.v1"))
    private val associatedData = "${context.packageName}:${file.baseFile.name}".toByteArray(Charsets.UTF_8)

    data class Saved(val text: String, val transaction: SaitoTransaction? = null)
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun load(): Saved = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized Saved("")
        val key = requireNotNull(existingKey()) { "Draft storage key unavailable" }
        val encrypted = file.openRead().use { input ->
            val buffer = ByteArray(MAX_BYTES + 30)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 29 until buffer.size && buffer[0] in 1.toByte()..2.toByte())
            buffer.copyOf(size)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
            updateAAD(associatedData)
        }
        val text = cipher.doFinal(encrypted.copyOfRange(13, encrypted.size))
        try {
            val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text)).toString()
            if (encrypted[0] == 1.toByte()) Saved(decoded)
            else {
                val record = gson.fromJson(decoded, JsonObject::class.java)
                val content = record.get("text").asString
                val transaction = record.get("transaction")?.takeUnless { it.isJsonNull }?.asString?.let(SaitoTransaction::restore)
                transaction?.let {
                    val post = RedSquareFeed.decode(it.envelope())
                    require(post.author == owner && post.text == content)
                    require(post.parentId == parent?.id.orEmpty() && (parent == null || post.threadId == parent.threadId))
                }
                Saved(content, transaction)
            }
        } finally { text.fill(0) }
    }

    fun save(saved: Saved) = synchronized(lock) {
        require(saved.text.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
        val record = JsonObject().apply {
            addProperty("text", saved.text)
            saved.transaction?.let { addProperty("transaction", it.encoded()) }
        }
        val plaintext = record.toString().toByteArray(Charsets.UTF_8)
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
            byteArrayOf(2) + cipher.iv + cipher.doFinal(plaintext)
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
        val reopened = load()
        check(reopened.text == saved.text && reopened.transaction?.encoded() == saved.transaction?.encoded()) {
            "Draft persistence verification failed"
        }
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(KEY_ALIAS, null) as? SecretKey

    private companion object {
        const val KEY_ALIAS = "redsquare.postdraft.v1"
        const val MAX_BYTES = 5 * 1024 * 1024
        val lock = Any()
    }
}

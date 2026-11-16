package nl.davidengelen.redsquare

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Fixed public fixture in the already-shipped131 disk format. Seed never calls post130 transfer APIs. */
internal object FileshareUpdateFixture {
    val contact: String = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private const val token = "00000000-0000-4000-8000-000000000131"
    private const val interrupted = "00000000-0000-4000-8000-000000000132"
    private const val checkpoint = "fileshare-signed-update131.json"
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)

    fun seed(context: Context, owner: String) {
        check(context.packageName == "nl.davidengelen.redsquare" && owner == "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU")
        val prefix = "fileshare.${hash("$owner:owner:$contact".toByteArray())}."
        check(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.startsWith("fileshare.") })
        fun metadata(id: String, phase: String, count: Long) {
            val body = JsonObject().apply {
                addProperty("token", id); addProperty("endpoint", "ws://127.0.0.1:1/wsopen")
                add("offer", JsonObject().apply {
                    addProperty("id", "signedupdate131"); addProperty("name", if (id == token) "Update retained file.txt" else "Interrupted file.txt")
                    addProperty("size", 5); addProperty("type", "text/plain")
                })
                addProperty("incoming", true); addProperty("phase", phase); addProperty("bytes", count)
                addProperty("createdAt", 1_700_000_000_000L)
            }
            EncryptedLocalFile(context, "$prefix$id.info", "fileshare.metadata.v1", "fileshare.")
                .save(EncryptedLocalFile.Contents(1, body.toString()))
        }
        metadata(token, "SAVED", 5); metadata(interrupted, "INTERRUPTED", 2)
        val existing = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("fileshare.spool.v1", null) as? SecretKey
        val key = existing ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("fileshare.spool.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD("${context.packageName}:$prefix$token".toByteArray() + ByteBuffer.allocate(12).putLong(0).putInt(5).array())
        }
        val data = File(context.noBackupFilesDir, "$prefix$token.data")
        FileOutputStream(data).use { output ->
            val stream = DataOutputStream(output)
            stream.writeInt(0x46534831); stream.writeInt(5); stream.write(cipher.iv); stream.write(cipher.doFinal(bytes)); output.fd.sync()
        }
        assertEquals(41L, data.length())
        val records = JsonObject()
        context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.forEach { records.addProperty(it.name, hash(it.readBytes())) }
        assertEquals(3, records.size())
        File(context.cacheDir, checkpoint).writeText(records.toString())
    }

    /** Run only after the real update, through current ordinary-app APIs and its preserved Keystore. */
    fun verify(context: Context, owner: String): Boolean {
        val record = File(context.cacheDir, checkpoint)
        if (!record.exists()) return false // Existing127 update scope has no131 Fileshare seed.
        val originals = JsonParser.parseString(record.readText()).asJsonObject
        originals.entrySet().forEach { (name, expected) ->
            check(name.matches(Regex("fileshare\\.[0-9a-f]{64}\\.[0-9a-f-]{36}\\.(info|data)")))
            assertEquals(expected.asString, hash(File(context.noBackupFilesDir, name).readBytes()))
        }
        val store = FileTransferStore(context, owner, SaitoBackend.OWNER, contact)
        val values = store.load()
        assertEquals(2, values.size)
        val received = values.single { it.token == token }
        assertEquals(FilePhase.SAVED, received.phase); assertEquals(LocalFileCopy.AVAILABLE, received.localCopy)
        assertArrayEquals(bytes, store.openPrepared(received).use { it.readBytes() })
        assertEquals(FilePhase.INTERRUPTED, values.single { it.token == interrupted }.phase)
        val storage = ReceivedFiles(context, ReceivedFilesScope(owner, SaitoBackend.OWNER)).snapshot()
        assertFalse(storage.incomplete); assertEquals(41L, storage.bytes); assertEquals(token, storage.files.single().transfer.token)
        return true
    }

    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}

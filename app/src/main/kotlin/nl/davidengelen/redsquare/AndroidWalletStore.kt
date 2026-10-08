package nl.davidengelen.redsquare

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only encrypted Saito options. A failed read must never replace an identity. */
class AndroidWalletStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "wallet.v1"))
    private val associatedData = "${context.packageName}:wallet.v1".toByteArray(Charsets.UTF_8)

    fun loadOrCreate(): SaitoWallet = synchronized(lock) {
        // AtomicFile also recovers an interrupted legacy backup before reading.
        val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (exists) {
            requireNotNull(existingKey) { "Wallet storage key unavailable" }
            readSavedWallet(existingKey)
        } else {
            val key = existingKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
            val wallet = SaitoWallet.create()
            val plaintext = wallet.exportJson().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(associatedData)
            val envelope = try { byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext) }
            finally { plaintext.fill(0) }
            val output = file.startWrite()
            try {
                output.write(envelope)
                // AtomicFile logs some sync/rename failures rather than throwing.
                // Propagate sync errors and verify the committed identity below.
                output.fd.sync()
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
            val directory = Os.open(file.baseFile.parentFile!!.path,
                OsConstants.O_RDONLY or OsConstants.O_DIRECTORY, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
            val saved = readSavedWallet(key)
            check(saved.publicKey == wallet.publicKey) { "Wallet persistence verification failed" }
            saved
        }
    }

    private fun readSavedWallet(key: SecretKey): SaitoWallet {
        val envelope = file.openRead().use { input ->
            val buffer = ByteArray(MAX_ENVELOPE_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 30..MAX_ENVELOPE_BYTES) { "Invalid wallet storage" }
            buffer.copyOf(size)
        }
        require(envelope[0] == 1.toByte()) { "Unsupported wallet storage" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(associatedData)
        val plaintext = cipher.doFinal(envelope.copyOfRange(13, envelope.size))
        return try { SaitoWallet.fromBackup(plaintext.toString(Charsets.UTF_8)) }
        finally { plaintext.fill(0) }
    }

    companion object {
        private val lock = Any()
        private const val KEY_ALIAS = "redsquare.wallet.v1"
        private const val MAX_ENVELOPE_BYTES = 5 * 1024 * 1024 + 29
    }
}

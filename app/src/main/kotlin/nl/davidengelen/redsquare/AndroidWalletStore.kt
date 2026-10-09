package nl.davidengelen.redsquare

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.system.Os
import android.system.OsConstants
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.File
import java.io.FileOutputStream
import java.io.FileDescriptor
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-only encrypted Saito options. A failed read must never replace an identity. */
class AndroidWalletStore(context: Context, private val directorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "wallet.v1"))
    private val associatedData = "${context.packageName}:wallet.v1".toByteArray(Charsets.UTF_8)
    private val initializing = File(context.noBackupFilesDir, "wallet.initializing")
    private val recovery = AtomicFile(File(context.noBackupFilesDir, "wallet.recovery.v1"))

    fun loadOrCreate(): SaitoWallet = synchronized(lock) {
        // AtomicFile also recovers an interrupted legacy backup before reading.
        val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (hasRecovery()) {
            require(!initializing.exists()) { "Conflicting wallet recovery state" }
            return@synchronized recover(requireNotNull(existingKey) { "Wallet storage key unavailable" })
        }
        if (initializing.exists()) {
            require(initializing.isFile && initializing.length() == 0L) { "Invalid wallet initialization state" }
            // The durable marker precedes key generation and is removed before
            // any identity is exposed. A pending valid save keeps its identity.
            val saved = if (exists) {
                requireNotNull(existingKey) { "Wallet storage key unavailable" }
                try { readSavedWallet(existingKey) }
                catch (_: IllegalArgumentException) { null }
                catch (_: AEADBadTagException) { null }
            } else null
            if (saved != null) {
                finishInitialization()
                return@synchronized saved
            }
            // Android 10 can leave a partial base file during a first write.
            // Only this unpublished initialization may discard incomplete data.
            for (suffix in listOf("", ".bak", ".new")) {
                val incomplete = File(file.baseFile.path + suffix)
                require(!incomplete.exists() || incomplete.delete()) { "Incomplete wallet storage unavailable" }
            }
            if (existingKey != null) keyStore.deleteEntry(KEY_ALIAS)
            syncDirectory()
        } else if (exists) {
            requireNotNull(existingKey) { "Wallet storage key unavailable" }
            val saved = readSavedWallet(existingKey)
            syncDirectory()
            return@synchronized saved
        } else {
            require(existingKey == null) { "Saved wallet storage unavailable" }
            require(!File(file.baseFile.path + ".new").exists()) { "Interrupted wallet storage requires recovery" }
        }
        // Persist the publication barrier before creating an unused storage key.
        FileOutputStream(initializing).use { it.fd.sync() }
        syncDirectory()
        run {
            val key = KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
            val wallet = SaitoWallet.create()
            val saved = writeVerified(file, wallet, key)
            finishInitialization()
            saved
        }
    }

    /** The recovery record remains authoritative until a verified commit removes it. */
    fun loadExisting(): SaitoWallet = synchronized(lock) {
        require(!initializing.exists()) { "Wallet initialization is incomplete" }
        val key = requireNotNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(KEY_ALIAS, null) as? SecretKey) { "Wallet storage key unavailable" }
        if (hasRecovery()) recover(key) else readSavedWallet(key).also { syncDirectory() }
    }

    /** The recovery record remains authoritative until a verified commit removes it. */
    fun replace(expected: SaitoWallet, replacement: SaitoWallet): SaitoWallet = synchronized(lock) {
        require(!initializing.exists()) { "Wallet initialization is incomplete" }
        val key = requireNotNull(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(KEY_ALIAS, null) as? SecretKey) { "Wallet storage key unavailable" }
        val previous = if (hasRecovery()) recover(key) else readSavedWallet(key)
        check(previous.exportJson() == expected.exportJson()) { "Active wallet changed" }
        try {
            writeVerified(recovery, previous, key)
            val saved = writeVerified(file, replacement, key)
            removeRecovery()
            saved
        } catch (_: Exception) {
            // Restoring must itself be durable and verified. A failure retains
            // recovery data and the caller must withhold a live signing wallet.
            try {
                writeVerified(recovery, previous, key)
                recover(key)
            } catch (_: Exception) { throw IllegalStateException("Wallet recovery required") }
            throw IllegalStateException("Wallet replacement failed")
        }
    }

    private fun hasRecovery() = recovery.baseFile.exists() || File(recovery.baseFile.path + ".bak").exists()

    private fun recover(key: SecretKey): SaitoWallet {
        val previous = readSavedWallet(key, recovery)
        val saved = writeVerified(file, previous, key)
        removeRecovery()
        return saved
    }

    private fun removeRecovery() {
        recovery.delete()
        check(listOf("", ".bak", ".new").none { File(recovery.baseFile.path + it).exists() }) {
            "Wallet recovery could not be committed"
        }
        syncDirectory()
    }

    private fun writeVerified(target: AtomicFile, wallet: SaitoWallet, key: SecretKey): SaitoWallet {
        val plaintext = wallet.exportJson().toByteArray(Charsets.UTF_8)
        val envelope = try {
            require(plaintext.size in 1..SaitoWallet.MAX_BACKUP_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(associatedData)
            byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
        } finally { plaintext.fill(0) }
        val output = target.startWrite()
        try {
            output.write(envelope)
            // AtomicFile logs some failures instead of throwing. Explicit sync
            // and exact decrypted readback precede publication or commit.
            output.fd.sync()
            target.finishWrite(output)
        } catch (failure: Exception) {
            target.failWrite(output)
            throw failure
        }
        syncDirectory()
        val saved = readSavedWallet(key, target)
        check(saved.exportJson() == wallet.exportJson()) { "Wallet persistence verification failed" }
        return saved
    }

    private fun finishInitialization() {
        check(initializing.delete()) { "Wallet initialization could not be committed" }
        syncDirectory()
    }

    private fun syncDirectory() {
        val directory = Os.open(file.baseFile.parentFile!!.path, OsConstants.O_RDONLY, 0)
        try { directorySync(directory) } finally { Os.close(directory) }
    }

    private fun readSavedWallet(key: SecretKey, source: AtomicFile = file): SaitoWallet {
        val envelope = source.openRead().use { input ->
            val buffer = ByteArray(MAX_ENVELOPE_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 30..MAX_ENVELOPE_BYTES) { "Invalid wallet storage" }
            input.fd.sync()
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

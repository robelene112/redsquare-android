package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Portable account recovery. The current MLS sending state stays on its original device.
 * Passphrase derivation and archive encoding belong off the UI thread. */
object AccountRecoveryBackup {
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val KEY_BYTES = 32
    private const val MAX_PAYLOAD_BYTES = 16 * 1024 * 1024
    internal const val MAX_HISTORY_SCOPES = 4_096
    private val magic = "SAITOREC".encodeToByteArray()
    private val headerSize = magic.size + 1 + SALT_BYTES + NONCE_BYTES
    private val random = SecureRandom()

    class Restored internal constructor(val wallet: SaitoWallet, val history: List<SpaceHistoryKeys>) {
        override fun toString(): String = "Protected account recovery"
    }

    /** Identify the protected file family before asking for the passphrase. */
    fun recognizes(bytes: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    fun seal(wallet: SaitoWallet, history: List<SpaceHistoryKeys>, passphrase: CharArray): ByteArray {
        require(passphrase.size in 1..1_024)
        require(history.size <= MAX_HISTORY_SCOPES && history.all { it.owner == wallet.publicKey })
        require(history.map { it.scope }.toSet().size == history.size) { "Duplicate history scope" }
        val header = ByteBuffer.allocate(headerSize).put(magic).put(VERSION.toByte())
            .put(ByteArray(SALT_BYTES).also(random::nextBytes))
            .put(ByteArray(NONCE_BYTES).also(random::nextBytes)).array()
        val bytes = encode(wallet, history)
        return try { header + crypt(Cipher.ENCRYPT_MODE, passphrase, header, bytes, 0) }
        finally { bytes.fill(0) }
    }

    fun open(bytes: ByteArray, passphrase: CharArray): Restored {
        require(passphrase.size in 1..1_024)
        require(bytes.size in (headerSize + TAG_BYTES + 1)..(headerSize + TAG_BYTES + MAX_PAYLOAD_BYTES) &&
            recognizes(bytes) && bytes[magic.size] == VERSION.toByte()) { "Unsupported account backup" }
        val plain = crypt(Cipher.DECRYPT_MODE, passphrase, bytes.copyOfRange(0, headerSize), bytes, headerSize)
        return try { decode(plain) }
        finally { plain.fill(0) }
    }

    private fun encode(wallet: SaitoWallet, history: List<SpaceHistoryKeys>): ByteArray {
        val records = mutableListOf<ByteArray>()
        var size = Int.SIZE_BYTES
        fun add(bytes: ByteArray) {
            records.add(bytes)
            size = Math.addExact(size, Math.addExact(Int.SIZE_BYTES, bytes.size))
            require(size <= MAX_PAYLOAD_BYTES) { "Account backup is too large" }
        }
        try {
            val identity = wallet.exportJson().encodeToByteArray()
            add(identity)
            require(identity.size <= SaitoWallet.MAX_BACKUP_BYTES) { "Wallet backup is too large" }
            history.forEach { add(it.save()) }
            val buffer = ByteBuffer.allocate(size).putInt(history.size)
            records.forEach { buffer.putInt(it.size).put(it) }
            return buffer.array()
        } finally { records.forEach { it.fill(0) } }
    }

    private fun decode(bytes: ByteArray): Restored {
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.remaining() >= Int.SIZE_BYTES)
        val count = buffer.int
        require(count in 0..MAX_HISTORY_SCOPES)
        val wallet = buffer.readRecord { SaitoWallet.fromBackup(it.decodeToString(throwOnInvalidSequence = true)) }
        val history = List(count) { buffer.readRecord { SpaceHistoryKeys.restore(it, wallet.publicKey) } }
        require(!buffer.hasRemaining() && history.map { it.scope }.toSet().size == count)
        return Restored(wallet, history)
    }

    private fun <T> ByteBuffer.readRecord(read: (ByteArray) -> T): T {
        require(remaining() >= Int.SIZE_BYTES)
        val size = int
        require(size in 1..remaining())
        val bytes = ByteArray(size).also { get(it) }
        return try { read(bytes) }
        finally { bytes.fill(0) }
    }

    private fun crypt(mode: Int, passphrase: CharArray, header: ByteArray, bytes: ByteArray, offset: Int): ByteArray {
        val saltStart = magic.size + 1
        val nonceStart = saltStart + SALT_BYTES
        // RFC 9106 section 4 memory-constrained profile. V1 fixes the cost
        // so an untrusted header cannot supply its own memory parameters.
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(header.copyOfRange(saltStart, nonceStart))
            .withMemoryAsKB(65_536).withIterations(3).withParallelism(4).build()
        val key = ByteArray(KEY_BYTES)
        return try {
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passphrase, key)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, header.copyOfRange(nonceStart, headerSize)))
            cipher.updateAAD(header)
            cipher.doFinal(bytes, offset, bytes.size - offset)
        } finally { key.fill(0) }
    }
}

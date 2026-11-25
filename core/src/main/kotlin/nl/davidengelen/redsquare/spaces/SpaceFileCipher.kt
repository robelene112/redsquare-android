package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Each file has its own random key. Only its channel-encrypted descriptor carries it. */
object SpaceFileCipher {
    data class Scope(val service: String, val space: String, val channel: String) {
        init {
            require(SaitoWallet.isPublicKey(service) && space.matches(FILE_ID) && channel.matches(FILE_ID))
        }
    }

    class Sealed(val id: String, val sha256: String, val size: Long, key: ByteArray) {
        private val material = key.copyOf()
        val bytes = ciphertextSize(size)

        init {
            require(id.matches(FILE_ID) && sha256.matches(FILE_ID) && material.size == 32)
        }

        fun keyBytes() = material.copyOf()
        override fun toString() = "Encrypted Space file descriptor"
    }

    const val MAX_FILE_BYTES = 128L * 1024 * 1024
    private const val CHUNK_BYTES = 256 * 1024
    private const val TAG_BYTES = 16
    const val MAX_CIPHERTEXT_BYTES = MAX_FILE_BYTES + MAX_FILE_BYTES / CHUNK_BYTES * TAG_BYTES

    fun ciphertextSize(size: Long): Long {
        require(size in 0..MAX_FILE_BYTES)
        val chunks = maxOf(1, (size + CHUNK_BYTES - 1) / CHUNK_BYTES)
        return size + chunks * TAG_BYTES
    }

    fun seal(scope: Scope, size: Long, source: InputStream, destination: OutputStream): Sealed {
        ciphertextSize(size)
        val random = SecureRandom()
        val id = ByteArray(32).also(random::nextBytes).fileHex()
        val key = ByteArray(32).also(random::nextBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        transform(scope, id, size, key, Cipher.ENCRYPT_MODE, source, DigestOutputStream(destination, digest))
        return Sealed(id, digest.digest().fileHex(), size, key)
    }

    /** Write only into a private staging sink. Publish the sink after this returns. */
    fun open(scope: Scope, sealed: Sealed, source: InputStream, staging: OutputStream) {
        val digest = MessageDigest.getInstance("SHA-256")
        transform(scope, sealed.id, sealed.size, sealed.keyBytes(), Cipher.DECRYPT_MODE,
            DigestInputStream(source, digest), staging)
        require(digest.digest().fileHex() == sealed.sha256) { "File transfer was incomplete or changed" }
    }

    private fun transform(scope: Scope, id: String, size: Long, key: ByteArray, mode: Int,
        source: InputStream, destination: OutputStream) {
        val chunkCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secret = SecretKeySpec(key, "AES")
        val input = DataInputStream(source)
        val overhead = if (mode == Cipher.DECRYPT_MODE) TAG_BYTES else 0
        val buffer = ByteArray(CHUNK_BYTES + overhead)
        val associatedData = "SaitoSpaceFile/v1:${scope.service}:${scope.space}:${scope.channel}:$id:$size"
            .toByteArray(Charsets.US_ASCII)
        var remaining = size
        var index = 0L
        do {
            val count = minOf(CHUNK_BYTES.toLong(), remaining).toInt()
            input.readFully(buffer, 0, count + overhead)
            val nonce = ByteBuffer.allocate(12).putInt(0).putLong(index).array()
            chunkCipher.init(mode, secret, GCMParameterSpec(TAG_BYTES * 8, nonce))
            chunkCipher.updateAAD(associatedData)
            val result = try {
                chunkCipher.doFinal(buffer, 0, count + overhead)
            } catch (failure: AEADBadTagException) {
                throw IllegalArgumentException("File bytes failed authentication", failure)
            }
            destination.write(result)
            remaining -= count
            index++
        } while (remaining > 0)
        require(input.read() == -1) { "File length changed during transfer" }
    }
}

internal val FILE_ID = Regex("[0-9a-f]{64}")
internal val FILE_SIGNATURE = Regex("[0-9a-f]{128}")
internal fun ByteArray.fileHex() = joinToString("") { "%02x".format(it.toInt() and 255) }

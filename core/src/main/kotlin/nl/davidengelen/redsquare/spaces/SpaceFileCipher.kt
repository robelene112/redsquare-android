package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.Closeable
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
    private const val LENGTH_BUCKET = 64 * 1024
    private const val CHUNK_BYTES = 256 * 1024
    private const val TAG_BYTES = 16
    const val MIN_CIPHERTEXT_BYTES = (LENGTH_BUCKET + TAG_BYTES).toLong()
    const val MAX_CIPHERTEXT_BYTES = MAX_FILE_BYTES + MAX_FILE_BYTES / CHUNK_BYTES * TAG_BYTES

    fun ciphertextSize(size: Long): Long {
        val padded = paddedSize(size)
        val chunks = (padded + CHUNK_BYTES - 1) / CHUNK_BYTES
        return padded + chunks * TAG_BYTES
    }

    private fun paddedSize(size: Long): Long {
        require(size in 0..MAX_FILE_BYTES)
        return maxOf(1L, (size + LENGTH_BUCKET - 1) / LENGTH_BUCKET) * LENGTH_BUCKET
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
        val associatedData = associatedData(scope, id, size)
        var remaining = size
        var padded = paddedSize(size)
        var index = 0L
        while (padded > 0) {
            val count = minOf(CHUNK_BYTES.toLong(), padded).toInt()
            val meaningful = minOf(count.toLong(), remaining).toInt()
            if (mode == Cipher.ENCRYPT_MODE) {
                input.readFully(buffer, 0, meaningful)
                buffer.fill(0, meaningful, count)
            } else {
                input.readFully(buffer, 0, count + overhead)
            }
            val result = crypt(chunkCipher, secret, mode, associatedData, index, buffer, count + overhead)
            destination.write(result, 0, if (mode == Cipher.ENCRYPT_MODE) result.size else meaningful)
            remaining -= meaningful
            padded -= count
            index++
        }
        require(input.read() == -1) { "File length changed during transfer" }
    }

    /**
     * A native document viewer may seek. Read one authenticated chunk at a time
     * from an already verified encrypted local copy.
     */
    class Reader(scope: Scope, private val file: Sealed, private val source: RandomAccessFile) : Closeable {
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val secret = SecretKeySpec(file.keyBytes(), "AES")
        private val aad = associatedData(scope, file.id, file.size)
        private var index = -1L
        private var plaintext = ByteArray(0)
        val size get() = file.size

        init {
            require(source.length() == file.bytes)
        }

        fun read(offset: Long, length: Int, into: ByteArray): Int {
            require(offset >= 0 && length in 0..into.size)
            val requested = minOf(length.toLong(), (size - offset).coerceAtLeast(0)).toInt()
            var copied = 0
            while (copied < requested) {
                val at = offset + copied
                val chunk = at / CHUNK_BYTES
                val from = (at % CHUNK_BYTES).toInt()
                if (chunk != index) load(chunk)
                val count = minOf(requested - copied, plaintext.size - from)
                plaintext.copyInto(into, copied, from, from + count)
                copied += count
            }
            return copied
        }

        private fun load(chunk: Long) {
            val count = minOf(CHUNK_BYTES.toLong(), paddedSize(size) - chunk * CHUNK_BYTES).toInt()
            val encrypted = ByteArray(count + TAG_BYTES)
            source.seek(chunk * (CHUNK_BYTES + TAG_BYTES))
            source.readFully(encrypted)
            val decoded = crypt(cipher, secret, Cipher.DECRYPT_MODE, aad, chunk, encrypted, encrypted.size)
            plaintext.fill(0)
            plaintext = decoded
            index = chunk
        }

        override fun close() {
            plaintext.fill(0)
            source.close()
        }
    }

    private fun associatedData(scope: Scope, id: String, size: Long) =
        "SaitoSpaceFile/v1:${scope.service}:${scope.space}:${scope.channel}:$id:$size".toByteArray(Charsets.US_ASCII)

    private fun crypt(cipher: Cipher, secret: SecretKeySpec, mode: Int, aad: ByteArray, index: Long,
        buffer: ByteArray, count: Int): ByteArray {
        val nonce = ByteBuffer.allocate(12).putInt(0).putLong(index).array()
        cipher.init(mode, secret, GCMParameterSpec(TAG_BYTES * 8, nonce))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(buffer, 0, count)
        } catch (failure: AEADBadTagException) {
            throw IllegalArgumentException("File bytes failed authentication", failure)
        }
    }
}

internal val FILE_ID = Regex("[0-9a-f]{64}")
internal val FILE_SIGNATURE = Regex("[0-9a-f]{128}")
internal fun ByteArray.fileHex() = joinToString("") { "%02x".format(it.toInt() and 255) }

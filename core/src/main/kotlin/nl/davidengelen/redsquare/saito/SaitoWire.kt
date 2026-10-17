package nl.davidengelen.redsquare.saito

import java.nio.ByteBuffer
import java.math.BigInteger

/** Saito binary framing, independent of Android and individual modules. */
object SaitoWire {
    fun applicationRequest(publicKey: String, message: ByteArray, index: Int, timestamp: Long): ByteArray {
        require(message.size <= 5 * 1024 * 1024 && index > 0 && timestamp >= 0)
        val key = Base58.decode(publicKey)
        require(key.size == 33)
        val buffer = ByteBuffer.allocate(5 + 93 + 4 * 59 + message.size)
        buffer.put(12).putInt(index)
        buffer.putInt(2).putInt(2).putInt(message.size).putInt(0)
        buffer.put(ByteArray(64)).putLong(timestamp).putInt(1).put(0)
        repeat(4) { buffer.put(key).put(ByteArray(26)) }
        buffer.put(message)
        return buffer.array()
    }

    /** Recent SPV bootstrap, then full continuation from an explicitly processed cursor. */
    fun blockchainRequest(publicKey: String, height: BigInteger, hash: String, forkId: String): ByteArray {
        require(height.signum() >= 0 && height.bitLength() <= 64)
        require(hash.matches(Regex("[0-9a-f]{64}")) && forkId.matches(Regex("[0-9a-f]{64}")))
        val key = Base58.decode(publicKey).also { require(it.size == 33) }
        fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ByteBuffer.allocate(111).put(21).putLong(height.toLong()).put(bytes(hash)).put(bytes(forkId))
            .put(if (height.signum() == 0) 1.toByte() else 0.toByte()).put(key).putInt(0).array()
    }

    fun handshakeReply(wallet: SaitoWallet, nonce: ByteArray, counter: ByteArray): ByteArray {
        require(nonce.size == 32 && counter.size == 32)
        val signature = wallet.sign(nonce).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return byteArrayOf(2) + Base58.decode(wallet.publicKey) + signature + counter
    }

    fun verifyHandshake(frame: ByteArray, nonce: ByteArray): String? {
        if (frame.size != 130 || frame[0] != 2.toByte() || nonce.size != 32) return null
        val key = Base58.encode(frame.copyOfRange(1, 34))
        val signature = frame.copyOfRange(34, 98).joinToString("") { "%02x".format(it) }
        return key.takeIf { SaitoWallet.verify(nonce, signature, it) }
    }
}

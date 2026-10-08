package nl.davidengelen.redsquare.saito

import java.nio.ByteBuffer

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

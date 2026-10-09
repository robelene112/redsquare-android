package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.util.Base64

/** Immutable zero-payment Normal transaction; routing never changes its signed identity. */
class SaitoTransaction private constructor(private val bytes: ByteArray) {
    val id: String get() = bytes.copyOfRange(16, 80).joinToString("") { "%02x".format(it) }
    fun encoded(): String = Base64.getEncoder().encodeToString(bytes)

    fun envelope(): String {
        val buffer = ByteBuffer.wrap(bytes)
        val messageOffset = 93 + 59 * (buffer.getInt(0) + buffer.getInt(4))
        val messageLength = buffer.getInt(8)
        val detached = bytes.copyOfRange(0, messageOffset)
        ByteBuffer.wrap(detached).putInt(8, 0)
        val encoder = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("t", encoder.encodeToString(detached))
            addProperty("m", encoder.encodeToString(bytes.copyOfRange(messageOffset, messageOffset + messageLength)))
            addProperty("opt", encoder.encodeToString("{}".toByteArray(Charsets.UTF_8)))
        }.toString()
    }

    fun broadcast(wallet: SaitoWallet, destination: String): ByteArray {
        require(SaitoEnvelope.decode(envelope()).author == wallet.publicKey)
        val recipient = Base58.decode(destination)
        require(recipient.size == 33)
        val signature = bytes.copyOfRange(16, 80)
        val hopSignature = wallet.sign(signature + recipient).hexBytes()
        val routed = bytes.copyOf()
        ByteBuffer.wrap(routed).putInt(12, 1)
        return byteArrayOf(4) + routed + Base58.decode(wallet.publicKey) + recipient + hopSignature
    }

    companion object {
        fun create(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipients: List<String> = emptyList()): SaitoTransaction {
            require(timestamp >= 0)
            val message = body.toString().toByteArray(Charsets.UTF_8)
            require(message.size <= 1024 * 1024)
            val publicKey = Base58.decode(wallet.publicKey)
            val outputs = (listOf(wallet.publicKey) + recipients).distinct().map { address ->
                val point = Base58.decode(address)
                require(point.size == 33 && point[0] in 2.toByte()..3.toByte())
                org.bouncycastle.crypto.ec.CustomNamedCurves.getByName("secp256k1").curve.decodePoint(point)
                point
            }
            require(outputs.size <= 255)
            val preimage = ByteBuffer.allocate(16 + (1 + outputs.size) * 43 + message.size)
                .putLong(timestamp).put(publicKey).put(ByteArray(10))
            outputs.forEachIndexed { index, point -> preimage.put(point).putLong(0).put(index.toByte()).put(0) }
            preimage.putInt(1).putInt(0).put(message)
            val signature = wallet.sign(preimage.array()).hexBytes()
            val network = ByteBuffer.allocate(93 + (1 + outputs.size) * 59 + message.size)
                .putInt(1).putInt(outputs.size).putInt(message.size).putInt(0).put(signature)
                .putLong(timestamp).putInt(1).put(0)
            network.put(publicKey).put(ByteArray(26))
            outputs.forEachIndexed { index, point -> network.put(point).put(ByteArray(24)).put(index.toByte()).put(0) }
            network.put(message)
            return SaitoTransaction(network.array())
        }

        fun restore(encoded: String): SaitoTransaction {
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size in 211..(1024 * 1024 + 93 + 256 * 59))
            val header = ByteBuffer.wrap(bytes)
            val outputs = header.getInt(4)
            require(header.getInt(0) == 1 && outputs in 1..255 && header.getInt(12) == 0)
            require(header.getInt(8) in 0..1024 * 1024 && header.getInt(8) == bytes.size - 93 - 59 * (1 + outputs))
            require(header.getInt(88) == 1)
            repeat(1 + outputs) { index ->
                val offset = 93 + 59 * index
                require(bytes.copyOfRange(offset + 33, offset + 57).all { it == 0.toByte() })
                require(bytes[offset + 57] == (if (index == 0) 0 else index - 1).toByte() && bytes[offset + 58] == 0.toByte())
            }
            require(bytes.copyOfRange(93, 126).contentEquals(bytes.copyOfRange(152, 185)))
            return SaitoTransaction(bytes).also { SaitoEnvelope.decode(it.envelope()) }
        }

        private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

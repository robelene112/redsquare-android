package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.util.Base64

/** Immutable zero-payment Normal transaction; routing never changes its signed identity. */
class SaitoTransaction private constructor(private val bytes: ByteArray) {
    class InvalidMessage : IllegalArgumentException("Message must be valid Unicode within the native size limit")

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

    /** Signed application RPC, with no broadcast routing hop. */
    fun applicationRequest(index: Int): ByteArray {
        require(index > 0)
        return byteArrayOf(12) + ByteBuffer.allocate(4).putInt(index).array() + bytes
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
        const val MAX_MESSAGE_BYTES = 1024 * 1024

        fun create(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipients: List<String> = emptyList()): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, (listOf(wallet.publicKey) + recipients).distinct())

        internal fun createTo(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipient: String): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, listOf(recipient, wallet.publicKey).distinct())

        private fun createWithOutputs(wallet: SaitoWallet, body: JsonObject, timestamp: Long, addresses: List<String>): SaitoTransaction {
            require(timestamp >= 0)
            val message = try {
                val encoded = Charsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(body.toString()))
                ByteArray(encoded.remaining()).also { encoded.get(it) }
            } catch (_: java.nio.charset.CharacterCodingException) { throw InvalidMessage() }
            if (message.size > MAX_MESSAGE_BYTES) throw InvalidMessage()
            val publicKey = Base58.decode(wallet.publicKey)
            val outputs = addresses.map { address ->
                require(SaitoWallet.isPublicKey(address))
                Base58.decode(address)
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
            val author = bytes.copyOfRange(93, 126)
            require((0 until outputs).any { index ->
                val offset = 152 + 59 * index
                author.contentEquals(bytes.copyOfRange(offset, offset + 33))
            })
            return SaitoTransaction(bytes).also { SaitoEnvelope.decode(it.envelope()) }
        }

        private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

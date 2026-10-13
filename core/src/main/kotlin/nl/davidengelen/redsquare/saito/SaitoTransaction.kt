package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.nio.ByteBuffer
import java.util.Base64
import java.math.BigInteger

/** Immutable Normal transaction. Zero-payment restoration remains a separate strict contract. */
class SaitoTransaction private constructor(private val bytes: ByteArray) {
    class InvalidMessage : IllegalArgumentException("Message must be valid Unicode within the native size limit")
    internal data class OwnedSlip(val owner: String, val slip: SaitoBalanceSnapshot.Slip)

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

    /** Original signed bytes in the existing client's Relay JSON representation. */
    fun signedJson(): JsonObject {
        val buffer = ByteBuffer.wrap(bytes)
        fun slips(start: Int, count: Int) = com.google.gson.JsonArray().apply {
            repeat(count) { index ->
                val offset = 93 + 59 * (start + index)
                add(JsonObject().apply {
                    addProperty("publicKey", Base58.encode(bytes.copyOfRange(offset, offset + 33)))
                    addProperty("type", bytes[offset + 58].toInt() and 255)
                    addProperty("amount", BigInteger(1, bytes.copyOfRange(offset + 33, offset + 41)))
                    addProperty("index", bytes[offset + 57].toInt() and 255)
                    addProperty("blockId", BigInteger(1, bytes.copyOfRange(offset + 41, offset + 49)))
                    addProperty("txOrdinal", BigInteger(1, bytes.copyOfRange(offset + 49, offset + 57)))
                    addProperty("utxoKey", "0".repeat(118))
                })
            }
        }
        val inputs = buffer.getInt(0)
        val outputs = buffer.getInt(4)
        return JsonObject().apply {
            add("to", slips(inputs, outputs))
            add("from", slips(0, inputs))
            addProperty("type", bytes[92].toInt() and 255)
            addProperty("timestamp", buffer.getLong(80))
            addProperty("signature", id)
            addProperty("buffer", com.google.gson.JsonParser.parseString(envelope()).asJsonObject.get("m").asString)
            addProperty("txs_replacements", buffer.getInt(88))
            addProperty("total_fees", 0)
            add("routing_path", com.google.gson.JsonArray())
        }
    }

    /** Signed application RPC, with no broadcast routing hop. */
    fun applicationRequest(index: Int): ByteArray {
        require(index > 0)
        return byteArrayOf(12) + ByteBuffer.allocate(4).putInt(index).array() + bytes
    }

    fun broadcast(wallet: SaitoWallet, destination: String): ByteArray {
        require(SaitoEnvelope.decodePayload(envelope()).author == wallet.publicKey)
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

        /** Used only after the funded-payment factory has checked its complete slips and amounts. */
        internal fun fromFundedBytes(bytes: ByteArray): SaitoTransaction =
            SaitoTransaction(bytes.copyOf()).also { SaitoEnvelope.decode(it.envelope()) }

        fun create(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipients: List<String> = emptyList()): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, (listOf(wallet.publicKey) + recipients).distinct())

        /** Saito Chat signs a JSON string containing the cipher formatter, not a plaintext object. */
        fun createEncrypted(wallet: SaitoWallet, ciphertext: String, timestamp: Long, recipient: String): SaitoTransaction =
            createWithOutputs(wallet, JsonPrimitive(ciphertext), timestamp, listOf(wallet.publicKey, recipient).distinct())

        internal fun createTo(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipient: String): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, listOf(recipient, wallet.publicKey).distinct())

        private fun createWithOutputs(wallet: SaitoWallet, body: JsonElement, timestamp: Long, addresses: List<String>): SaitoTransaction {
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            return createNormal(wallet, body, timestamp, listOf(OwnedSlip(wallet.publicKey, zero)),
                addresses.mapIndexed { index, address -> OwnedSlip(address, zero.copy(index = index)) })
        }

        /** Admission belongs to the zero-payment and funded factories, not the common wire serializer. */
        internal fun createNormal(wallet: SaitoWallet, body: JsonElement, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>): SaitoTransaction {
            require(timestamp >= 0)
            require(inputs.size in 1..255 && outputs.size in 0..255 && inputs.first().owner == wallet.publicKey)
            val message = try {
                val encoded = Charsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(body.toString()))
                ByteArray(encoded.remaining()).also { encoded.get(it) }
            } catch (_: java.nio.charset.CharacterCodingException) { throw InvalidMessage() }
            if (message.size > MAX_MESSAGE_BYTES) throw InvalidMessage()
            val slips = (inputs + outputs).map { owned ->
                require(SaitoWallet.isPublicKey(owned.owner))
                val slip = owned.slip
                require(listOf(slip.amount, slip.block, slip.ordinal).all { it.signum() >= 0 && it.bitLength() <= 64 })
                require(slip.index in 0..255 && slip.type in 0..10)
                Base58.decode(owned.owner) to slip
            }
            val preimage = ByteBuffer.allocate(16 + slips.size * 43 + message.size).putLong(timestamp)
            slips.forEach { (point, slip) -> preimage.put(point).putLong(slip.amount.toLong())
                .put(slip.index.toByte()).put(slip.type.toByte()) }
            preimage.putInt(1).putInt(0).put(message)
            val signature = wallet.sign(preimage.array()).hexBytes()
            val network = ByteBuffer.allocate(93 + slips.size * 59 + message.size)
                .putInt(inputs.size).putInt(outputs.size).putInt(message.size).putInt(0).put(signature)
                .putLong(timestamp).putInt(1).put(0)
            slips.forEach { (point, slip) -> network.put(point).putLong(slip.amount.toLong())
                .putLong(slip.block.toLong()).putLong(slip.ordinal.toLong()).put(slip.index.toByte()).put(slip.type.toByte()) }
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
            return SaitoTransaction(bytes).also {
                val body = SaitoEnvelope.decodePayload(it.envelope()).body
                require(body.isJsonObject || (body.isJsonPrimitive && body.asJsonPrimitive.isString))
            }
        }

        private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

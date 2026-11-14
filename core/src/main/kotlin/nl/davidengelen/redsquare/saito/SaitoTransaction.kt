package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.nio.ByteBuffer
import java.util.Base64
import java.math.BigInteger

/** Immutable signed transaction. Zero-payment restoration remains a separate strict contract. */
class SaitoTransaction private constructor(private val bytes: ByteArray, private val escrowSigner: String? = null) {
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
        val author = escrowSigner ?: if (bytes[92] == 8.toByte()) SaitoEnvelope.decodeBound(envelope()).author
            else SaitoEnvelope.decodePayload(envelope()).author
        require(author == wallet.publicKey)
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

        /** Only the separately validated zero-fund Store carrier admits this original. */
        internal fun fromStoreCarrierBytes(bytes: ByteArray): SaitoTransaction {
            require(bytes.size >= 329 && ByteBuffer.wrap(bytes).getInt(0) == 2 && ByteBuffer.wrap(bytes).getInt(4) == 2)
            require((0..3).all { index -> bytes.copyOfRange(93 + 59 * index + 33, 93 + 59 * index + 41).all { it == 0.toByte() } })
            return SaitoTransaction(bytes.copyOf()).also { SaitoEnvelope.decode(it.envelope()) }
        }

        /** Only the strict Vault candidate reader admits retained Bound bytes. */
        internal fun fromVaultMintBytes(bytes: ByteArray): SaitoTransaction =
            SaitoTransaction(bytes.copyOf()).also { SaitoEnvelope.decodeBound(it.envelope()) }

        /** Only the separately validated seller escrow-spend reader uses this signer admission. */
        internal fun fromStoreDelistBytes(bytes: ByteArray, seller: String): SaitoTransaction {
            require(SaitoWallet.isPublicKey(seller) && bytes.size >= 448)
            val header = ByteBuffer.wrap(bytes)
            require(header.getInt(0) == 3 && header.getInt(4) == 3 && bytes[92] == 8.toByte() && header.getInt(12) == 0 &&
                header.getInt(88) == 1 && header.getInt(8) in 1..MAX_MESSAGE_BYTES && bytes.size == 447 + header.getInt(8))
            val payload = bytes.copyOfRange(447, bytes.size)
            val body = com.google.gson.JsonParser.parseString(payload.toString(Charsets.UTF_8)).asJsonObject
            require(body.get("module").asString == "Store" && body.get("request").asString == "delist-asset" && body.get("seller").asString == seller &&
                SaitoWallet.verify(saitoTransactionPreimage(bytes, payload), bytes.copyOfRange(16, 80).joinToString("") { "%02x".format(it.toInt() and 255) }, seller))
            return SaitoTransaction(bytes.copyOf(), seller)
        }

        fun create(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipients: List<String> = emptyList()): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, (listOf(wallet.publicKey) + recipients).distinct())

        /** Saito Chat signs a JSON string containing the cipher formatter, not a plaintext object. */
        fun createEncrypted(wallet: SaitoWallet, ciphertext: String, timestamp: Long, recipient: String): SaitoTransaction =
            createWithOutputs(wallet, JsonPrimitive(ciphertext), timestamp, listOf(wallet.publicKey, recipient).distinct())

        internal fun createTo(wallet: SaitoWallet, body: JsonObject, timestamp: Long, recipient: String,
            additionalRecipients: List<String> = emptyList()): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, (listOf(recipient, wallet.publicKey) + additionalRecipients).distinct())

        private fun createWithOutputs(wallet: SaitoWallet, body: JsonElement, timestamp: Long, addresses: List<String>, messageLimit: Int = MAX_MESSAGE_BYTES): SaitoTransaction {
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            return createSigned(wallet, body, timestamp, listOf(OwnedSlip(wallet.publicKey, zero)),
                addresses.mapIndexed { index, address -> OwnedSlip(address, zero.copy(index = index)) }, messageLimit = messageLimit)
        }

        /** Admission belongs to the zero-payment and funded factories, not the common wire serializer. */
        internal fun createNormal(wallet: SaitoWallet, body: JsonElement, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>): SaitoTransaction =
            createSigned(wallet, body, timestamp, inputs, outputs)

        /** Vault file/RPC messages have their own bounded admission, without changing social limits. */
        internal fun createVaultMessage(wallet: SaitoWallet, body: JsonObject, timestamp: Long): SaitoTransaction =
            createWithOutputs(wallet, body, timestamp, listOf(wallet.publicKey), 8 * 1024 * 1024)

        internal fun createVaultMint(wallet: SaitoWallet, body: JsonObject, timestamp: Long,
            inputs: List<SaitoBalanceSnapshot.Slip>, id: String, deposit: BigInteger, quantity: BigInteger = BigInteger.ONE): SaitoTransaction {
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val outputs = listOf(OwnedSlip(wallet.publicKey, zero.copy(amount = quantity, type = 9)),
                OwnedSlip(wallet.publicKey, zero.copy(index = 1, amount = deposit)),
                OwnedSlip(Base58.encode(id.chunked(2).map { it.toInt(16).toByte() }.toByteArray()), zero.copy(index = 2, type = 9))) +
                (inputs.fold(BigInteger.ZERO) { total, slip -> total + slip.amount } - deposit).let { change ->
                    if (change.signum() > 0) listOf(OwnedSlip(wallet.publicKey, zero.copy(index = 3, amount = change))) else emptyList()
                }
            return createSigned(wallet, body, timestamp, inputs.map { OwnedSlip(wallet.publicKey, it) }, outputs,
                transactionType = 8, metadataSlots = setOf(inputs.size + 2))
        }

        internal fun createNftTransfer(wallet: SaitoWallet, body: JsonObject, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>): SaitoTransaction {
            require(inputs.size == 3 && outputs.size == 3 && inputs[1].owner == wallet.publicKey)
            return createSigned(wallet, body, timestamp, inputs, outputs, transactionType = 8, metadataSlots = setOf(2, 5))
        }

        /** Bound sale custody is admitted only after the listing factory commits its script. */
        internal fun createStoreListing(wallet: SaitoWallet, body: JsonObject, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>): SaitoTransaction {
            require(inputs.size == 4 && outputs.size in setOf(4, 7) && inputs[1].owner == wallet.publicKey)
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            require(body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash &&
                Base58.decode(outputs[1].owner).joinToString("") { "%02x".format(it.toInt() and 255) } == "00" + script.hash)
            return createSigned(wallet, body, timestamp, inputs, outputs, transactionType = 8,
                metadataSlots = if (outputs.size == 7) setOf(2, 6, 9) else setOf(2, 6), escrowSlot = 5)
        }

        /** Only the Store purchase factory admits its committed CHECKMULTISIG escrow output. */
        internal fun createStorePurchase(wallet: SaitoWallet, body: JsonObject, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>): SaitoTransaction {
            require(outputs.size == 2 && inputs.all { it.owner == wallet.publicKey })
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            require(body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash)
            require(Base58.decode(outputs.first().owner).contentEquals(("00" + script.hash).chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
            return createSigned(wallet, body, timestamp, inputs, outputs, escrowSlot = inputs.size)
        }

        private fun createSigned(wallet: SaitoWallet, body: JsonElement, timestamp: Long,
            inputs: List<OwnedSlip>, outputs: List<OwnedSlip>, transactionType: Int = 0,
            messageLimit: Int = MAX_MESSAGE_BYTES, metadataSlots: Set<Int> = emptySet(), escrowSlot: Int? = null): SaitoTransaction {
            require(timestamp >= 0)
            require(inputs.size in 1..255 && outputs.size in 0..255)
            require((if (transactionType == 8) inputs.firstOrNull { it.slip.type != 9 } else inputs.first())?.owner == wallet.publicKey)
            val message = try {
                val encoded = Charsets.UTF_8.newEncoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(body.toString()))
                ByteArray(encoded.remaining()).also { encoded.get(it) }
            } catch (_: java.nio.charset.CharacterCodingException) { throw InvalidMessage() }
            if (message.size > messageLimit) throw InvalidMessage()
            val slips = (inputs + outputs).mapIndexed { index, owned ->
                if (index in metadataSlots) require(transactionType == 8 && owned.slip.type == 9 &&
                    owned.slip.amount.signum() == 0 && Base58.decode(owned.owner).size == 33)
                else if (index == escrowSlot) require(transactionType in setOf(0, 8) && owned.slip.type == 0 &&
                    Base58.decode(owned.owner).let { it.size == 33 && it[0] == 0.toByte() })
                else require(SaitoWallet.isPublicKey(owned.owner))
                val slip = owned.slip
                require(listOf(slip.amount, slip.block, slip.ordinal).all { it.signum() >= 0 && it.bitLength() <= 64 })
                require(slip.index in 0..255 && slip.type in 0..10)
                Base58.decode(owned.owner) to slip
            }
            val preimage = ByteBuffer.allocate(16 + slips.size * 43 + message.size).putLong(timestamp)
            slips.forEach { (point, slip) -> preimage.put(point).putLong(slip.amount.toLong())
                .put(slip.index.toByte()).put(slip.type.toByte()) }
            preimage.putInt(1).putInt(transactionType).put(message)
            val signature = wallet.sign(preimage.array()).hexBytes()
            val network = ByteBuffer.allocate(93 + slips.size * 59 + message.size)
                .putInt(inputs.size).putInt(outputs.size).putInt(message.size).putInt(0).put(signature)
                .putLong(timestamp).putInt(1).put(transactionType.toByte())
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

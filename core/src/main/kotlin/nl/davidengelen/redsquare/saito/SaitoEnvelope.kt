package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.Strictness
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

data class SignedSaitoMessage(val id: String, val author: String, val createdAt: Long, val body: JsonObject,
    val recipients: List<String>)

data class VerifiedSaitoPayload(val id: String, val author: String, val createdAt: Long, val body: JsonElement,
    val recipients: List<String>)

/** Verifies detached web Archive message bytes before exposing any module content. */
object SaitoEnvelope {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    /** Framing only: Relay wrappers may be unsigned. Callers must authenticate their original inner message. */
    internal fun unverifiedNetworkEnvelope(binary: ByteArray): String {
        require(binary.size in 93..(4 * SaitoTransaction.MAX_MESSAGE_BYTES + 200_000))
        val header = ByteBuffer.wrap(binary)
        val inputs = header.getInt(0)
        val outputs = header.getInt(4)
        val messageLength = header.getInt(8)
        val hops = header.getInt(12)
        require(inputs in 1..255 && outputs in 0..255 && hops in 0..1024)
        require(messageLength in 1..(4 * SaitoTransaction.MAX_MESSAGE_BYTES) && binary[92] == 0.toByte())
        val start = 93 + 59 * (inputs + outputs)
        val end = start + messageLength
        require(binary.size.toLong() == end.toLong() + 130L * hops)
        val detached = binary.copyOfRange(0, start) + binary.copyOfRange(end, binary.size)
        ByteBuffer.wrap(detached).putInt(8, 0)
        val encoder = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("t", encoder.encodeToString(detached))
            addProperty("m", encoder.encodeToString(binary.copyOfRange(start, end)))
            addProperty("opt", encoder.encodeToString("{}".toByteArray(Charsets.UTF_8)))
        }.toString()
    }

    /** Framing only. The outer Relay can be unsigned; callers authenticate the returned original. */
    internal fun originalChatEnvelope(networkTransaction:ByteArray,acceptsDirect:(JsonElement)->Boolean):String {
        val outer=unverifiedNetworkEnvelope(networkTransaction)
        val detached=gson.fromJson(outer,JsonObject::class.java)
        val bytes=Base64.getDecoder().decode(detached.get("m").asString)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val payload=gson.fromJson(text,JsonElement::class.java)
        if(acceptsDirect(payload))return outer
        require(payload.isJsonObject && payload.asJsonObject.get("module")==com.google.gson.JsonPrimitive("Relay") &&
            payload.asJsonObject.get("request")==com.google.gson.JsonPrimitive("chat relay"))
        return fromSignedJson(payload.asJsonObject.getAsJsonObject("data"))
    }

    /** Reconstruct the original signed payload from Relay's inner client JSON. Outer Relay is not trusted. */
    fun fromSignedJson(inner: JsonObject): String {
        val inputs = inner.getAsJsonArray("from")
        val outputs = inner.getAsJsonArray("to")
        require(inputs.size() in 1..255 && outputs.size() in 0..255)
        fun unsigned(value: JsonElement, bits: Int): java.math.BigInteger {
            require(value.isJsonPrimitive && value.asString.matches(Regex("[0-9]{1,20}")))
            return java.math.BigInteger(value.asString).also { require(it.bitLength() <= bits) }
        }
        val signature = inner.get("signature")
        require(signature.isJsonPrimitive && signature.asJsonPrimitive.isString && signature.asString.matches(Regex("[0-9a-fA-F]{128}")))
        val encoded = inner.get("buffer")
        require(encoded.isJsonPrimitive && encoded.asJsonPrimitive.isString && encoded.asString.length <= 2 * SaitoTransaction.MAX_MESSAGE_BYTES)
        val message = Base64.getDecoder().decode(encoded.asString)
        require(message.size <= SaitoTransaction.MAX_MESSAGE_BYTES)
        require(unsigned(inner.get("type"), 8).toInt() == 0)
        val detached = ByteBuffer.allocate(93 + 59 * (inputs.size() + outputs.size()))
            .putInt(inputs.size()).putInt(outputs.size()).putInt(0).putInt(0)
            .put(signature.asString.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            .putLong(unsigned(inner.get("timestamp"), 63).toLong())
            .putInt(unsigned(inner.get("txs_replacements"), 32).toInt()).put(0)
        (inputs.toList() + outputs.toList()).forEach { element ->
            val slip = element.asJsonObject
            val key = slip.get("publicKey")
            require(key.isJsonPrimitive && key.asJsonPrimitive.isString && SaitoWallet.isPublicKey(key.asString))
            detached.put(Base58.decode(key.asString)).putLong(unsigned(slip.get("amount"), 64).toLong())
                .putLong(unsigned(slip.get("blockId"), 64).toLong())
                .putLong(unsigned(slip.get("txOrdinal"), 64).toLong())
                .put(unsigned(slip.get("index"), 8).toByte()).put(unsigned(slip.get("type"), 8).toByte())
        }
        val encoder = Base64.getEncoder()
        return JsonObject().apply {
            addProperty("t", encoder.encodeToString(detached.array()))
            addProperty("m", encoder.encodeToString(message))
            addProperty("opt", encoder.encodeToString("{}".toByteArray(Charsets.UTF_8)))
        }.toString().also { decodePayload(it) }
    }

    fun decode(envelope: String): SignedSaitoMessage {
        val signed = decodePayload(envelope)
        require(signed.body.isJsonObject) { "Expected application object" }
        return SignedSaitoMessage(signed.id, signed.author, signed.createdAt, signed.body.asJsonObject, signed.recipients)
    }

    /** Chat encrypts before signing: authenticate the JSON string before any decryption. */
    fun decodePayload(envelope: String): VerifiedSaitoPayload {
        require(envelope.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024)
        val outer = gson.fromJson(envelope, JsonObject::class.java)
        val binary = Base64.getDecoder().decode(outer.get("t").asString)
        val message = Base64.getDecoder().decode(outer.get("m").asString)
        require(binary.size >= 93)
        val header = ByteBuffer.wrap(binary)
        val inputs = header.int
        val outputs = header.int
        val embeddedLength = header.int
        val hops = header.int
        require(inputs in 1..255 && outputs in 0..255 && hops in 0..1024 && embeddedLength == 0)
        require(binary.size.toLong() == 93L + 59L * (inputs + outputs) + 130L * hops)
        val signature = binary.copyOfRange(16, 80).joinToString("") { "%02x".format(it) }
        val timestamp = header.getLong(80)
        require(timestamp >= 0 && binary[92] == 0.toByte())
        val author = Base58.encode(binary.copyOfRange(93, 126))
        require(SaitoWallet.verify(saitoTransactionPreimage(binary, message), signature, author)) { "Invalid transaction signature" }
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(message)).toString()
        val body = gson.fromJson(utf8, JsonElement::class.java)
            ?: throw IllegalArgumentException("Missing transaction message")
        val recipients = (0 until outputs).map { index ->
            val offset = 93 + 59 * (inputs + index)
            Base58.encode(binary.copyOfRange(offset, offset + 33))
        }
        return VerifiedSaitoPayload(signature, author, timestamp, body, recipients)
    }
}

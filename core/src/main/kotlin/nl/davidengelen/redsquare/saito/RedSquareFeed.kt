package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Original signed content; archive metadata and chain inclusion are separate assertions. */
data class FeedPost(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: Long,
    val parentId: String,
    val threadId: String,
    val envelope: String,
)

object RedSquareFeed {
    private const val MAX_ENVELOPE_BYTES = 8 * 1024 * 1024
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    /** Reads web Archive envelopes, verifies the original bytes, then exposes post content. */
    fun decode(envelope: String): FeedPost {
        require(envelope.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES)
        val outer = gson.fromJson(envelope, JsonObject::class.java)
        val binary = Base64.getDecoder().decode(outer.string("t"))
        val message = Base64.getDecoder().decode(outer.string("m"))
        require(binary.size >= 93)
        val header = ByteBuffer.wrap(binary)
        val inputs = header.int
        val outputs = header.int
        val embeddedLength = header.int
        val hops = header.int
        require(inputs in 1..1024 && outputs in 0..1024 && hops in 0..1024 && embeddedLength == 0)
        require(binary.size.toLong() == 93L + 59L * (inputs + outputs) + 130L * hops)
        val signature = binary.copyOfRange(16, 80).joinToString("") { "%02x".format(it) }
        val timestamp = header.getLong(80)
        require(timestamp >= 0 && binary[92] == 0.toByte())
        val author = Base58.encode(binary.copyOfRange(93, 126))
        val preimage = ByteBuffer.allocate(16 + 43 * (inputs + outputs) + message.size)
        preimage.putLong(timestamp)
        repeat(inputs + outputs) { index ->
            val offset = 93 + index * 59
            preimage.put(binary, offset, 41) // point and amount
            preimage.put(binary, offset + 57, 2) // index and type, not block/ordinal
        }
        preimage.putInt(header.getInt(88))
        preimage.putInt(binary[92].toInt() and 0xff)
        preimage.put(message)
        require(SaitoWallet.verify(preimage.array(), signature, author)) { "Invalid post signature" }
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(message)).toString()
        val body = gson.fromJson(utf8, JsonObject::class.java)
        require(body.string("module") == "RedSquare" && body.string("request") == "create tweet")
        val data = body.getAsJsonObject("data") ?: throw IllegalArgumentException("Missing post data")
        val parent = data.optionalString("parent_id")
        val thread = data.optionalString("thread_id").ifEmpty { signature }
        return FeedPost(signature, author, data.optionalString("text"), timestamp, parent, thread, envelope)
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }

    private fun JsonObject.optionalString(key: String): String = if (!has(key)) "" else string(key)
}

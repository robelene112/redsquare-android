package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

data class SignedSaitoMessage(val id: String, val author: String, val createdAt: Long, val body: JsonObject,
    val recipients: List<String>)

/** Verifies detached web Archive message bytes before exposing any module content. */
object SaitoEnvelope {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun decode(envelope: String): SignedSaitoMessage {
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
        val body = gson.fromJson(utf8, JsonObject::class.java)
            ?: throw IllegalArgumentException("Missing transaction message")
        val recipients = (0 until outputs).map { index ->
            val offset = 93 + 59 * (inputs + index)
            Base58.encode(binary.copyOfRange(offset, offset + 33))
        }
        return SignedSaitoMessage(signature, author, timestamp, body, recipients)
    }
}

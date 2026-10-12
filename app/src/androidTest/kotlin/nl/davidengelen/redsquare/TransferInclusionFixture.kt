package nl.davidengelen.redsquare

import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.util.Base64
import nl.davidengelen.redsquare.saito.SaitoPayment
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.bouncycastle.crypto.digests.Blake3Digest

/** Synthetic independent block content with fresh, test-signed header observations. */
internal class TransferInclusionFixture(json: String) {
    private val reference = JsonParser.parseString(json).asJsonObject.also { check(it.get("synthetic").asBoolean) }
    val payment = SaitoPayment.restore(reference.getAsJsonObject("payment").get("candidateBase64").asString)
    // Published unsafe reference identities. Never use or fund outside isolated tests.
    val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").also { check(it.publicKey == payment.author) }
    private val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").also {
        check(it.publicKey == reference.getAsJsonObject("block").get("creator").asString)
    }
    val timestamp = System.currentTimeMillis()
    val inclusion = freshBlock("oddBlock", timestamp - 1000)
    val tip = freshBlock("chainTip", timestamp, inclusion.first)
    val alternate = freshBlock("alternateBlock", timestamp + 1000)
    val alternateTip = freshBlock("alternateTip", timestamp + 2000, alternate.first)
    val blocks = mapOf(inclusion, tip, alternate, alternateTip)

    private fun freshBlock(name: String, time: Long, parent: String? = null): Pair<String, ByteArray> {
        val bytes = Base64.getDecoder().decode(reference.getAsJsonObject(name).get("fullBase64").asString)
        ByteBuffer.wrap(bytes).putLong(12, time)
        parent?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()?.copyInto(bytes, 20)
        val preimage = bytes.copyOfRange(4, 117) + bytes.copyOfRange(181, 213) + bytes.copyOfRange(221, 285)
        creator.sign(preimage).chunked(2).map { it.toInt(16).toByte() }.toByteArray().copyInto(bytes, 117)
        val hash = digest(bytes.copyOfRange(20, 52) + digest(preimage))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return hash to bytes
    }

    private fun digest(bytes: ByteArray): ByteArray = ByteArray(32).also { result ->
        Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) }
    }
}

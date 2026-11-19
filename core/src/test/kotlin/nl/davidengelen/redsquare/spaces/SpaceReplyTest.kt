package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Supplementary wire regression: replies may nest receipts above the social
 * limit; ordinary publication limits and original signatures stay intact. */
class SpaceReplyTest {
    @Test fun aLargeSignedReadReplyPreservesItsExactPayloadWithoutRelaxingOrdinaryPublicationLimits() {
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val payload = "Bounded read reply:" + "x".repeat(1_100_000)
        val body = JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("version", 1); addProperty("request", "page")
            add("items", JsonArray().apply { add(payload) })
        }
        assertThrows(SaitoTransaction.InvalidMessage::class.java) { SaitoTransaction.create(service, body, 1_700_000_000_000, listOf(caller.publicKey)) }
        val reply = SaitoTransaction.createSpaceReply(service, body, 1_700_000_000_000, caller.publicKey)
        val verified = SaitoEnvelope.decode(reply.envelope())
        assertEquals(service.publicKey, verified.author)
        assertEquals(listOf(service.publicKey, caller.publicKey), verified.recipients)
        assertTrue("Exact original payload survives signed framing", MessageDigest.isEqual(payload.toByteArray(Charsets.UTF_8),
            verified.body.getAsJsonArray("items")[0].asString.toByteArray(Charsets.UTF_8)))
        val wrongModule = body.deepCopy().apply { addProperty("module", "Chat") }
        assertThrows(IllegalArgumentException::class.java) { SaitoTransaction.createSpaceReply(service, wrongModule, 1_700_000_000_000, caller.publicKey) }
        val tooLarge = body.deepCopy().apply { add("items", JsonArray().apply { add("x".repeat(4_300_000)) }) }
        assertThrows(SaitoTransaction.InvalidMessage::class.java) { SaitoTransaction.createSpaceReply(service, tooLarge, 1_700_000_000_000, caller.publicKey) }
    }
}

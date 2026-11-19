package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceJournalTest {
    // Public, unsafe fixture scalars. No owner/device wallet is used.
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val space = "a".repeat(64)
    private val channel = "b".repeat(64)

    @Test fun anAuthenticatedCreationEstablishesExactlyOneOwnerAndDuplicateReceiptDoesNotCreateAnother() {
        val event = SaitoTransaction.create(owner, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", space); addProperty("operation_id", "c".repeat(64)); addProperty("kind", "create")
            addProperty("visibility", "private"); addProperty("channel_id", channel)
            addProperty("epoch", 0); addProperty("metadata", java.util.Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
        }, 1_700_000_000_000, listOf(service.publicKey))
        val receipt = SaitoTransaction.create(service, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "receipt"); addProperty("version", 1)
            addProperty("space_id", space); addProperty("stream", "control")
            addProperty("position", 0); addProperty("event", event.envelope())
        }, 1_700_000_000_001, listOf(owner.publicKey))
        val journal = SpaceJournal(space, service.publicKey)
        val created = journal.receive(receipt.envelope())
        assertEquals(owner.publicKey, created.owner)
        assertEquals(mapOf(owner.publicKey to SpaceRole.OWNER), created.members)
        assertEquals(event.id, created.controlId)
        assertEquals(1L, created.nextPosition)
        assertEquals(created, journal.receive(receipt.envelope()))
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class FileshareTest {
    private val alice = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val bob = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")

    @Test fun receiveFileOnlyCompletesAfterTheExactOrderedBytesAreWritten() {
        val offer = Fileshare.Offer("a12345678901", "notes.txt", 5, "text/plain")
        val receiver = Fileshare.Receiver(offer)
        val output = ByteArrayOutputStream()
        val first = Fileshare.Chunk(offer, 0, 1, byteArrayOf(72, 101, 108))
        assertFalse(receiver.append(first, output::write))
        assertEquals(3L, receiver.received)
        assertFalse(receiver.append(first, output::write)) // A repeated chunk does not duplicate bytes.
        assertThrows(IllegalArgumentException::class.java) {
            receiver.append(Fileshare.Chunk(offer, 2, 2, byteArrayOf(108, 111)), output::write)
        }
        assertTrue(receiver.append(Fileshare.Chunk(offer, 3, 2, byteArrayOf(108, 111)), output::write))
        assertArrayEquals(byteArrayOf(72, 101, 108, 108, 111), output.toByteArray())
    }

    @Test fun anOfferAndChunkRetainTheExistingFileshareMessageShapeAndSigner() {
        val offer = Fileshare.Offer("a12345678901", "notes.txt", 5, "text/plain")
        val tx = Fileshare.offer(alice, bob.publicKey, offer, 100)
        val message = Fileshare.direct(tx.envelope(), alice.publicKey, bob.publicKey)
        assertEquals("query file permission", message.request)
        assertEquals(offer, Fileshare.readOffer(message.data))
        val chunk = Fileshare.chunk(alice, bob.publicKey, Fileshare.Chunk(offer, 0, 1,
            byteArrayOf(72, 101, 108, 108, 111)), 101)
        val body = SaitoEnvelope.decode(chunk.envelope()).body
        assertEquals("Fileshare", body.get("module").asString)
        assertEquals("share file", body.get("request").asString)
        assertEquals("SGVsbG8=", body.getAsJsonObject("data").get("raw").asString)
        assertArrayEquals(byteArrayOf(72, 101, 108, 108, 111),
            Fileshare.readChunk(Fileshare.direct(chunk.envelope(), alice.publicKey, bob.publicKey).data).bytes)
        assertThrows(IllegalArgumentException::class.java) { Fileshare.direct(chunk.envelope(), bob.publicKey, alice.publicKey) }
    }

    @Test fun unsignedWebControlsAreOnlyUsableOnTheAlreadyAuthenticatedDataChannel() {
        val tx = Fileshare.control(alice, bob.publicKey, "grant file permission", JsonObject().apply {
            addProperty("id", "a12345678901")
        }, 200)
        val outer = com.google.gson.JsonParser.parseString(tx.envelope()).asJsonObject
        val binary = java.util.Base64.getDecoder().decode(outer.get("t").asString)
        binary.fill(0, 16, 80)
        outer.addProperty("t", java.util.Base64.getEncoder().encodeToString(binary))
        assertEquals("grant file permission", Fileshare.direct(outer.toString(), alice.publicKey, bob.publicKey).request)
        assertThrows(IllegalArgumentException::class.java) { Fileshare.direct(outer.toString(), bob.publicKey, alice.publicKey) }
        val body = java.util.Base64.getDecoder().decode(outer.get("m").asString)
        java.nio.ByteBuffer.wrap(binary).putInt(8, body.size)
        val network = binary + body
        assertThrows(IllegalArgumentException::class.java) { Fileshare.network(network, alice.publicKey, bob.publicKey) }
    }

    @Test fun failedStorageDoesNotAdvanceOrAcknowledgeTheReceivedFile() {
        val offer = Fileshare.Offer("a12345678901", "notes.txt", 5, "text/plain")
        val receive = Fileshare.Receiver(offer)
        val chunk = Fileshare.Chunk(offer, 0, 1, byteArrayOf(72, 101, 108, 108, 111))
        assertThrows(java.io.IOException::class.java) { receive.append(chunk) { throw java.io.IOException("Full") } }
        assertEquals(0L, receive.received)
        assertTrue(receive.append(chunk) {})
        assertThrows(IllegalArgumentException::class.java) { Fileshare.Offer("../file", "notes.txt", 5, "text/plain") }
        assertThrows(IllegalArgumentException::class.java) { Fileshare.Offer("normal", "../notes.txt", 5, "text/plain") }
    }
}

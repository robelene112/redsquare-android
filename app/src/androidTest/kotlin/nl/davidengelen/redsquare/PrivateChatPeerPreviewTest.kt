package nl.davidengelen.redsquare

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Test
import nl.davidengelen.redsquare.saito.PrivateChat
import nl.davidengelen.redsquare.saito.SaitoWire
import nl.davidengelen.redsquare.saito.ReceivedPrivateChat
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import androidx.test.platform.app.InstrumentationRegistry

class PrivateChatPeerPreviewTest {
    @Test fun anAuthenticatedSocketRoutesAndAcknowledgesTheOriginalPrivateRelayMessage() = runBlocking {
        val reference = InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-private-chat-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject
            }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val relay = JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", "chat relay")
            add("data", reference.getAsJsonObject("signedInner"))
        }
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        fixture.incomingTransaction = SaitoWire.applicationRequest(wallet.publicKey,
            relay.toString().toByteArray(Charsets.UTF_8), 1, reference.get("timestamp").asLong).drop(5).toByteArray()
        val received = java.util.concurrent.atomic.AtomicReference<ReceivedPrivateChat>()
        val delivered = java.util.concurrent.CountDownLatch(1)
        val server = fixture.start()
        try {
            SaitoPeer(wallet, server.url("/").toString(), onIncoming = { bytes ->
                received.set(PrivateChat.receive(wallet, bytes))
                delivered.countDown()
                true
            }).use { peer ->
                assertEquals(setOf("archive", "relay"), peer.services())
                org.junit.Assert.assertTrue(delivered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(reference.get("signature").asString, received.get().message.id)
                assertEquals(reference.getAsJsonObject("plaintext").get("message").asString, received.get().message.text)
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                while (fixture.incomingAcknowledgements.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20)
                assertEquals(1, fixture.incomingAcknowledgements.size)
                assertEquals(13.toByte(), fixture.incomingAcknowledgements.single()[0])
            }
            fixture.assertHealthy()
        } finally { server.close() }
    }

    @Test fun authenticatedSelectedPeerAdvertisesBothPrivateChatServices() = runBlocking {
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        val server = fixture.start()
        try {
            val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe local socket fixture.
            SaitoPeer(wallet, server.url("/").toString()).use { peer ->
                assertEquals(setOf("archive", "relay"), peer.services())
            }
            fixture.assertHealthy()
        } finally { server.close() }
    }
}

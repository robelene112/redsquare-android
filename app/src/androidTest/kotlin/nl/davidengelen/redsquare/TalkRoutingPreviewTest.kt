package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TalkRoutingPreviewTest {
    @Test fun anIncomingCallReachesTalkWhenTheAuthenticatedSocketBelongsToChat() = runBlocking {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        fixture.start().use { server ->
            val endpoint = server.url("/").toString()
            val delivered = CountDownLatch(1)
            val chatDelivered = CountDownLatch(1)
            val talk = AtomicInteger(); val chat = AtomicInteger()
            val received = java.util.concurrent.atomic.AtomicReference<TalkMessage>()
            val hook = RealtimeIncoming.register(owner.publicKey, endpoint) { bytes ->
                val message = TalkProtocol.network(bytes, owner.publicKey)
                if (message == null) null else {
                    received.set(message)
                    talk.incrementAndGet(); delivered.countDown(); true
                }
            }
            try {
                SaitoPeer(owner, endpoint, onIncoming = { chat.incrementAndGet(); chatDelivered.countDown(); true }).use { peer ->
                    assertEquals(setOf("archive", "relay"), peer.services())
                    awaitAuthentication(fixture)
                    val room = TalkRoom(caller.publicKey, caller.publicKey)
                    val transaction = TalkProtocol.invite(caller, owner.publicKey, room, TalkDial.REQUEST, 100)
                        .applicationRequest(1).drop(5).toByteArray()
                    fixture.sendIncoming(transaction)
                    assertTrue("Talk receives the real authenticated socket frame", delivered.await(5, TimeUnit.SECONDS))
                    awaitAcknowledgements(fixture, 1)
                    assertEquals(caller.publicKey, received.get().sender)
                    assertTrue(received.get().identityVerified)
                    assertEquals(0, chat.get())
                    assertEquals(13.toByte(), fixture.incomingAcknowledgements.single()[0])
                    hook.close()
                    fixture.sendIncoming(transaction, 778)
                    assertTrue("Closing the Talk observer restores the ordinary receiver", chatDelivered.await(5, TimeUnit.SECONDS))
                    awaitAcknowledgements(fixture, 2)
                    assertEquals(1, talk.get()); assertEquals(1, chat.get())
                    capture("routing", "Talk1 Chat1 ACK13/13")
                }
                fixture.assertHealthy()
            } finally { hook.close() }
        }
    }

    @Test fun aRejectedCallIsNotAcknowledgedByAnUnrelatedReceiver() = runBlocking {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val fixture = ControlledFeedPeer("[]", advertisedServices = "relay||")
        fixture.start().use { server ->
            val endpoint = server.url("/").toString()
            val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
            val unrelated = AtomicInteger(); val fallback = AtomicInteger()
            RealtimeIncoming.register(other.publicKey, endpoint) { unrelated.incrementAndGet(); true }.use {
                RealtimeIncoming.register(owner.publicKey, endpoint) { false }.use {
                    SaitoPeer(owner, endpoint, onIncoming = { fallback.incrementAndGet(); true }).use { peer ->
                        peer.services()
                        awaitAuthentication(fixture)
                        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
                        fixture.sendIncoming(TalkProtocol.invite(caller, owner.publicKey,
                            TalkRoom(caller.publicKey, caller.publicKey), TalkDial.REQUEST, 100).applicationRequest(1).drop(5).toByteArray())
                        awaitAcknowledgements(fixture, 1)
                        assertEquals(14.toByte(), fixture.incomingAcknowledgements.single()[0])
                        assertEquals(0, unrelated.get()); assertEquals(0, fallback.get())
                        capture("rejected", "OtherOwner0 Fallback0 ACK14")
                    }
                }
            }
            fixture.assertHealthy()
        }
    }

    /** Client service discovery can complete before the server consumes the final handshake frame. */
    private fun awaitAuthentication(fixture: ControlledFeedPeer) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (fixture.authenticatedConnectionIds.isEmpty() && android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(20)
        assertTrue("The owned server has verified the actual client handshake", fixture.authenticatedConnectionIds.isNotEmpty())
    }

    private fun awaitAcknowledgements(fixture: ControlledFeedPeer, count: Int) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (fixture.incomingAcknowledgements.size < count && android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(20)
        assertEquals(count, fixture.incomingAcknowledgements.size)
    }

    private fun capture(name: String, observation: String) {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "talk-routing-evidence")
            .apply { mkdirs() }.resolve("$name.txt").writeText(observation)
    }
}

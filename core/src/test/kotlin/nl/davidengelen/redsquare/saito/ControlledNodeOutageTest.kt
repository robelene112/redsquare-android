package nl.davidengelen.redsquare.saito

import kotlinx.coroutines.*
import nl.davidengelen.redsquare.ControlledFeedPeer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Actual owned-socket outage seam used by the inherited native recovery tests. */
class ControlledNodeOutageTest {
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
    private val archive = """{"request":"archive","data":{"request":"load","field1":"RedSquare","field4":"","flagged_ne":1,"limit":100}}""".toByteArray()

    @Test fun anOversizedReplyPreparedBeforeAnOutageRemainsAnUnqueuedReplyAfterClose() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val oversized = " ".repeat(SaitoPeer.MAX_RESPONSE_BYTES + 1)
        val fixture = ControlledFeedPeer("[]", archiveResponse = {
            entered.countDown()
            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            oversized
        }).apply { sendOversizedArchiveFrames = true }
        fixture.start().use { server ->
            SaitoPeer(wallet, "ws://127.0.0.1:${server.port}/wsopen").use { reading ->
                val pending = async { runCatching { reading.requestArchive(archive) } }
                try {
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    fixture.goOffline()
                    release.countDown()
                    assertTrue(withTimeout(5_000) { pending.await() }.exceptionOrNull() is IOException)
                    withTimeout(5_000) {
                        while (fixture.requests.get() == 0) { fixture.assertHealthy(); delay(10) }
                    }
                    fixture.assertHealthy()
                    val response = fixture.archiveObservations.single { it.get("event").asString == "reply" }
                    assertFalse(response.get("queued").asBoolean)
                    assertTrue(response.get("bytes").asInt > SaitoPeer.MAX_RESPONSE_BYTES)
                    assertTrue(fixture.transactions.isEmpty())
                } finally {
                    release.countDown()
                    withContext(NonCancellable) { withTimeout(5_000) { pending.cancelAndJoin() } }
                }
            }
        }
    }

    @Test fun outageRetiresSharedConnectionsWhileUpgradeRejectionAloneKeepsThemUsable() = runBlocking {
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        fixture.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            SaitoPeer(wallet, endpoint).use { account ->
                account.services()
                assertEquals("[]", account.request(archive).decodeToString())
                fixture.rejectNewConnections = true
                SaitoPeer(wallet, endpoint).use { reading ->
                    assertEquals("[]", reading.request(archive).decodeToString())
                    fixture.goOffline()
                    withTimeout(5_000) { account.awaitDisconnection(); reading.awaitDisconnection() }
                    assertTrue(runCatching { reading.request(archive) }.exceptionOrNull() is IOException)
                    SaitoPeer(wallet, endpoint).use { unavailable ->
                        assertTrue(runCatching { unavailable.authenticate() }.exceptionOrNull() is IOException)
                    }
                    fixture.rejectNewConnections = false
                    SaitoPeer(wallet, endpoint).use { recovered ->
                        assertEquals("[]", recovered.request(archive).decodeToString())
                    }
                }
            }
            fixture.assertHealthy()
        }
    }

    @Test fun outageAlsoStopsAnAdmittedUpgradeWhoseHandshakeHasNotFinished() = runBlocking {
        val fixture = ControlledFeedPeer("[]").apply { withholdHandshakeReplies = true }
        fixture.start().use { server ->
            SaitoPeer(wallet, "ws://127.0.0.1:${server.port}/wsopen").use { connecting ->
                val authenticated = async { runCatching { connecting.authenticate() } }
                withTimeout(5_000) { while (fixture.admittedConnectionCount == 0) delay(10) }
                assertEquals(0, fixture.servedChallenges.get())
                fixture.goOffline()
                fixture.releaseWithheldHandshakeReplies()
                assertTrue(withTimeout(5_000) { authenticated.await() }.exceptionOrNull() is IOException)
                assertEquals(0, fixture.servedChallenges.get())
            }
            fixture.assertHealthy()
        }
    }
}

package nl.davidengelen.redsquare.saito

import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import nl.davidengelen.redsquare.ControlledFeedPeer
import com.google.gson.JsonObject
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class SharedNodeConnectionTest {
    // Public, deterministic test identities only.
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")

    @Test fun lateCancelledLargeCallbackCannotInterruptTheCurrentBoundedArchiveRead() = runBlocking {
        val oversized = " ".repeat(SaitoPeer.MAX_RESPONSE_BYTES + 1)
        val response = java.util.concurrent.atomic.AtomicReference(oversized)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val fixture = ControlledFeedPeer("[]", archiveResponse = { response.get() },
            onArchiveResponse = { _, _, _ -> reads.incrementAndGet() }).apply {
            sendOversizedArchiveFrames = true
            withholdArchiveReply = true
            retainWithheldArchiveReplies = true
        }
        fixture.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            val query = """{"request":"archive","data":{"request":"load","field1":"RedSquare","field4":"","flagged_ne":1,"limit":20}}""".toByteArray()
            SaitoPeer(wallet, endpoint).use { old ->
                SaitoPeer(wallet, endpoint).use { current ->
                    val cancelled = async { old.requestArchive(query) }
                    var active: Deferred<Result<ByteArray>>? = null
                    try {
                        withTimeout(5_000) { while (reads.get() != 1) delay(10) }
                        withTimeout(1_000) { cancelled.cancelAndJoin() }
                        assertTrue(cancelled.isCancelled)
                        response.set("[]")
                        val next = async { runCatching { current.requestArchive(query) } }
                        active = next
                        withTimeout(5_000) { while (reads.get() != 2) delay(10) }
                        fixture.withholdArchiveReply = false
                        assertEquals(2, fixture.releaseWithheldArchiveReplies())
                        val result = withTimeout(5_000) { next.await() }
                        assertTrue("The cancelled owner's large callback must not interrupt the current reader: ${result.exceptionOrNull()}", result.isSuccess)
                        assertEquals("[]", result.getOrThrow().decodeToString())
                        assertEquals(1, fixture.connectionAttempts.get())
                        response.set(oversized)
                        assertTrue("An active oversized reply must still be rejected",
                            runCatching { current.requestArchive(query) }.exceptionOrNull() is SaitoPeer.ResponseTooLarge)
                        fixture.assertHealthy()
                        assertTrue(fixture.transactions.isEmpty())
                    } finally {
                        withContext(NonCancellable) { withTimeout(5_000) {
                            cancelled.cancelAndJoin(); active?.cancelAndJoin()
                        } }
                    }
                }
            }
        }
    }

    @Test fun heldIncomingRetentionDoesNotBlockSharedReadCallbacksOrAcknowledgeEarly() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val retained = java.util.concurrent.atomic.AtomicBoolean()
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, {
                    assertEquals("held incoming", it.decodeToString())
                    entered.countDown()
                    check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                    retained.set(true)
                    true
                }).use { peer ->
                    peer.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "held incoming", 8801)
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    try {
                        assertFalse(retained.get())
                        assertTrue(node.acknowledgements.tryReceive().isFailure)
                        // A real callback must remain readable while durable incoming work waits.
                        assertEquals("history", withTimeout(5_000) {
                            peer.request("history".toByteArray()).decodeToString()
                        })
                        assertFalse(retained.get())
                        assertTrue("Do not acknowledge before retention", node.acknowledgements.tryReceive().isFailure)
                    } finally { release.countDown() }
                    val ack = withTimeout(5_000) { node.acknowledgements.receive() }
                    assertTrue(retained.get())
                    assertEquals(13, ack[0].toInt())
                    assertEquals(8801, ByteBuffer.wrap(ack, 1, 4).int)
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                }
            }
        } finally { release.countDown() }
    }

    @Test fun queuedIncomingMessagesStayInOrderWhileAnotherFeatureReads() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val retained = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, { bytes ->
                    val body = bytes.decodeToString()
                    if (body == "first") {
                        entered.countDown()
                        check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    retained.add(body)
                    true
                }).use { receiver ->
                    receiver.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "first", 8811)
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    node.deliver(wallet.publicKey, "second", 8812)
                    try {
                        SaitoPeer(wallet, node.endpoint).use { reader ->
                            // This callback is behind both incoming frames on the same real socket.
                            assertEquals("history", withTimeout(5_000) { reader.request("history".toByteArray()).decodeToString() })
                        }
                        assertTrue(retained.isEmpty())
                        assertTrue(node.acknowledgements.tryReceive().isFailure)
                    } finally { release.countDown() }
                    for (index in listOf(8811, 8812)) {
                        val ack = withTimeout(5_000) { node.acknowledgements.receive() }
                        assertEquals(13, ack[0].toInt())
                        assertEquals(index, ByteBuffer.wrap(ack, 1, 4).int)
                    }
                    assertEquals(listOf("first", "second"), retained)
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                }
            }
        } finally { release.countDown() }
    }

    @Test fun retiringTheLastOwnerDiscardsQueuedDeliveryBeforeAReplacementConnects() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val started = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, { bytes ->
                    started.add(bytes.decodeToString())
                    entered.countDown()
                    try { check(release.await(15, java.util.concurrent.TimeUnit.SECONDS)); true }
                    finally { finished.countDown() }
                }).use { receiver ->
                    receiver.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "active", 8821)
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    node.deliver(wallet.publicKey, "queued", 8822)
                    SaitoPeer(wallet, node.endpoint).use { reader ->
                        assertEquals("barrier", withTimeout(5_000) { reader.request("barrier".toByteArray()).decodeToString() })
                    }
                    receiver.close()
                    assertTrue(withContext(Dispatchers.IO) { finished.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    assertEquals(listOf("active"), started)
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                    val fresh = java.util.concurrent.CopyOnWriteArrayList<String>()
                    SaitoPeer(wallet, node.endpoint, { fresh.add(it.decodeToString()); true }).use { replacement ->
                        replacement.request("ready".toByteArray())
                        node.deliver(wallet.publicKey, "fresh", 8823)
                        val ack = withTimeout(5_000) { node.acknowledgements.receive() }
                        assertEquals(13, ack[0].toInt())
                        assertEquals(8823, ByteBuffer.wrap(ack, 1, 4).int)
                        assertEquals(listOf("fresh"), fresh)
                        assertEquals(listOf("active"), started)
                        assertTrue(node.acknowledgements.tryReceive().isFailure)
                    }
                }
            }
        } finally { release.countDown() }
    }

    @Test fun anIncomingBacklogRetiresWithoutAcknowledgingDiscardedWork() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val deliveries = java.util.concurrent.atomic.AtomicInteger()
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, {
                    deliveries.incrementAndGet(); entered.countDown()
                    try { check(release.await(15, java.util.concurrent.TimeUnit.SECONDS)); true }
                    finally { finished.countDown() }
                }).use { receiver ->
                    receiver.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "active", 8831)
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    var queued = 0
                    repeat(64) { if (node.tryDeliver(wallet.publicKey, "waiting-$it", 8900 + it)) queued++ }
                    assertTrue(queued > 1)
                    withTimeout(5_000) { receiver.awaitDisconnection() }
                    assertTrue(withContext(Dispatchers.IO) { finished.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    assertEquals(1, deliveries.get())
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                }
            }
        } finally { release.countDown() }
    }

    @Test fun aSecondValidLargeIncomingFrameCannotExceedTheRetainedByteBudget() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val deliveries = java.util.concurrent.atomic.AtomicInteger()
        val body = "z".repeat(9 * 1024 * 1024)
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, { bytes ->
                    assertEquals(body.length, bytes.size)
                    deliveries.incrementAndGet(); entered.countDown()
                    try { check(release.await(15, java.util.concurrent.TimeUnit.SECONDS)); true }
                    finally { finished.countDown() }
                }).use { receiver ->
                    receiver.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, body, 8841)
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    // Both frames independently fit the existing16MiB frame bound.
                    // Only two frames are sent, so this cannot exhaust the count limit.
                    assertTrue(node.tryDeliver(wallet.publicKey, body, 8842))
                    withTimeout(5_000) { receiver.awaitDisconnection() }
                    assertTrue(withContext(Dispatchers.IO) { finished.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                    assertEquals(1, deliveries.get())
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                }
            }
        } finally { release.countDown() }
    }

    @Test fun exactPendingOriginalsShareRetentionWhileDistinctWireRequestsRemainIndependent() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val retained = java.util.concurrent.CopyOnWriteArrayList<String>()
        try {
            Node().use { node ->
                SaitoPeer(wallet, node.endpoint, { bytes ->
                    val body = bytes.decodeToString()
                    if (body == "same") {
                        entered.countDown()
                        check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    retained.add(body)
                    true
                }).use { receiver ->
                    receiver.request("ready".toByteArray())
                    SaitoPeer(wallet, node.endpoint).use { reader ->
                        node.deliver(wallet.publicKey, "same", 8851)
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                        try {
                            repeat(64) { assertTrue("A repeated pending original must not retire its connection",
                                node.tryDeliver(wallet.publicKey, "same", 8851)) }
                            node.deliver(wallet.publicKey, "same", 8852)
                            node.deliver(wallet.publicKey, "different", 8851)
                            assertEquals("barrier", withTimeout(5_000) { reader.request("barrier".toByteArray()).decodeToString() })
                            assertTrue(retained.isEmpty())
                            assertTrue("Retention is still held", node.acknowledgements.tryReceive().isFailure)
                        } finally { release.countDown() }
                        for (index in listOf(8851, 8852, 8851)) {
                            val ack = withTimeout(5_000) { node.acknowledgements.receive() }
                            assertEquals(13, ack[0].toInt())
                            assertEquals(index, ByteBuffer.wrap(ack, 1, 4).int)
                        }
                        assertEquals(listOf("same", "same", "different"), retained)
                        assertTrue(node.acknowledgements.tryReceive().isFailure)
                    }
                }
            }
        } finally { release.countDown() }
    }

    @Test fun aHeldRelayReplyDoesNotBlockAnIncomingAcknowledgementOnTheSharedSocket() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val incoming = CompletableDeferred<Unit>()
        val replies = java.util.concurrent.Executors.newSingleThreadExecutor()
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponseExecutor = replies, relayResponse = {
            entered.countDown()
            check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
            "{\"err\":\"\",\"success\":1}"
        })
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val original = PrivateChat.create(wallet, contact, "Held Relay reply", 1700000000000L)
        val relay = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", "chat relay")
            add("data", original.signedJson())
        }, 1700000000001L, listOf(contact))
        val request = JsonObject().apply {
            addProperty("request", "relay peer message"); add("data", relay.signedJson())
        }.toString().toByteArray()
        try {
            fixture.start().use { server ->
                SaitoPeer(wallet, "ws://127.0.0.1:${server.port}/wsopen", {
                    assertEquals("Incoming while Relay waits", it.decodeToString())
                    incoming.complete(Unit)
                    true
                }).use { receiver ->
                    receiver.services()
                    val waiting = async { receiver.request(request).decodeToString() }
                    try {
                        assertTrue(withContext(Dispatchers.IO) { entered.await(5, java.util.concurrent.TimeUnit.SECONDS) })
                        fixture.sendIncoming("Incoming while Relay waits".toByteArray(), requestId = 8810)
                        withTimeout(5_000) { incoming.await() }
                        withTimeout(5_000) {
                            while (fixture.incomingAcknowledgements.none {
                                it[0] == 13.toByte() && ByteBuffer.wrap(it, 1, 4).int == 8810
                            }) delay(10)
                        }
                        assertFalse("The Relay response is still deliberately held", waiting.isCompleted)
                    } finally {
                        release.countDown()
                    }
                    assertEquals("{\"err\":\"\",\"success\":1}", withTimeout(5_000) { waiting.await() })
                    fixture.assertHealthy()
                }
            }
        } finally {
            release.countDown()
            replies.shutdown()
            try { assertTrue(replies.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) }
            finally { replies.shutdownNow() }
        }
    }

    @Test fun concurrentFeatureRequestsReceiveTheirOwnRepliesThroughSaitosPublicKeyRoute() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { feed ->
                feed.authenticate()
                val first = async { feed.request("feed".toByteArray()).decodeToString() }
                withTimeout(5_000) { node.feedReceived.await() }
                SaitoPeer(wallet, node.endpoint).use { talk ->
                    assertEquals("talk", withTimeout(5_000) { talk.request("talk".toByteArray()).decodeToString() })
                    node.releaseFeed()
                    assertEquals("feed", withTimeout(5_000) { first.await() })
                }
                assertEquals("still receiving", withTimeout(5_000) {
                    feed.request("still receiving".toByteArray()).decodeToString()
                })
            }
        }
    }

    @Test fun sharedDeliveryNotifiesBothFeaturesOnceAndRetiringOneKeepsTheOtherAlive() = runBlocking {
        Node().use { node ->
            val firstMessages = java.util.concurrent.CopyOnWriteArrayList<String>()
            val secondMessages = java.util.concurrent.CopyOnWriteArrayList<String>()
            SaitoPeer(wallet, node.endpoint, { firstMessages.add(it.decodeToString()); true }).use { first ->
                SaitoPeer(wallet, node.endpoint, { secondMessages.add(it.decodeToString()); true }).use { second ->
                    second.authenticate()
                    // Request/response proves the server has authenticated this exact public key.
                    second.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "one", 777)
                    assertEquals(777, ByteBuffer.wrap(withTimeout(5_000) { node.acknowledgements.receive() }, 1, 4).int)
                    assertEquals(listOf("one"), firstMessages)
                    assertEquals(listOf("one"), secondMessages)
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                    first.close()
                    node.deliver(wallet.publicKey, "two", 778)
                    assertEquals(778, ByteBuffer.wrap(withTimeout(5_000) { node.acknowledgements.receive() }, 1, 4).int)
                    assertEquals(listOf("one"), firstMessages)
                    assertEquals(listOf("one", "two"), secondMessages)
                    assertEquals("alive", second.request("alive".toByteArray()).decodeToString())
                    assertTrue(node.acknowledgements.tryReceive().isFailure)
                }
            }
        }
    }

    @Test fun finishingPublisherDoesNotWaitForAnotherFeaturesLaterBlockedWrites() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { publisher ->
                SaitoPeer(wallet, node.endpoint).use { chat ->
                    assertEquals("published", publisher.request("published".toByteArray()).decodeToString())
                    val blocked = async { runCatching { chat.request("block".toByteArray()) } }
                    withTimeout(5_000) { node.blockReceived.await() }
                    val later = (1..2).map { async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { chat.request(ByteArray(5 * 1024 * 1024) { 120 }) }
                    } }
                    try {
                        withTimeout(1_000) { publisher.finishBroadcast() }
                        assertFalse("The other feature is deliberately still waiting", blocked.isCompleted)
                    } finally {
                        node.unblock()
                        blocked.cancelAndJoin()
                        later.forEach { it.cancelAndJoin() }
                    }
                }
            }
        }
    }

    @Test fun closingAFeatureCancelsOnlyItsPendingRequestAndLateReplyCannotStealAnotherReply() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { first ->
                SaitoPeer(wallet, node.endpoint).use { second ->
                    val waiting = async { runCatching { first.request("feed".toByteArray()) } }
                    withTimeout(5_000) { node.feedReceived.await() }
                    first.close()
                    assertTrue(withTimeout(5_000) { waiting.await() }.exceptionOrNull() is java.io.IOException)
                    node.releaseFeed()
                    assertEquals("second", second.request("second".toByteArray()).decodeToString())
                    assertTrue(runCatching { first.services() }.exceptionOrNull() is java.io.IOException)
                }
            }
        }
    }

    @Test fun walletsAndExactEndpointsKeepIndependentAuthenticatedRoutes() = runBlocking {
        Node().use { node -> Node().use { another ->
            SaitoPeer(wallet, node.endpoint).use { first ->
                val waiting = async { first.request("feed".toByteArray()).decodeToString() }
                withTimeout(5_000) { node.feedReceived.await() }
                val otherWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
                SaitoPeer(otherWallet, node.endpoint).use {
                    assertEquals("other identity", it.request("other identity".toByteArray()).decodeToString())
                }
                SaitoPeer(wallet, another.endpoint).use {
                    assertEquals("other node", it.request("other node".toByteArray()).decodeToString())
                }
                node.releaseFeed()
                assertEquals("feed", withTimeout(5_000) { waiting.await() })
            }
        } }
    }

    @Test fun disconnectedOwnersCannotDisposeTheReplacementConnection() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { first ->
                SaitoPeer(wallet, node.endpoint).use { second ->
                    first.request("ready".toByteArray())
                    node.disconnect(wallet.publicKey)
                    withTimeout(5_000) { first.awaitDisconnection(); second.awaitDisconnection() }
                    SaitoPeer(wallet, node.endpoint).use { replacement ->
                        assertEquals("replacement", replacement.request("replacement".toByteArray()).decodeToString())
                        first.close(); second.close()
                        assertEquals("still alive", replacement.request("still alive".toByteArray()).decodeToString())
                    }
                }
            }
        }
    }

    @Test fun registeredFeatureRejectionIsTerminalButOrdinaryObserverFailureIsIsolated() = runBlocking {
        Node().use { node ->
            val observed = java.util.concurrent.CopyOnWriteArrayList<String>()
            SaitoPeer(wallet, node.endpoint, { error("Observer failure") }).use {
                SaitoPeer(wallet, node.endpoint, { observed.add(it.decodeToString()); true }).use { second ->
                    second.request("ready".toByteArray())
                    node.deliver(wallet.publicKey, "normal", 779)
                    assertEquals(13, withTimeout(5_000) { node.acknowledgements.receive() }[0].toInt())
                    assertEquals(listOf("normal"), observed)
                    RealtimeIncoming.register(wallet.publicKey, node.endpoint) { false }.use {
                        node.deliver(wallet.publicKey, "claimed", 780)
                        assertEquals(14, withTimeout(5_000) { node.acknowledgements.receive() }[0].toInt())
                        assertEquals(listOf("normal"), observed)
                    }
                }
            }
        }
    }

    @Test fun aLateCallbackFromBeforeReconnectCannotAnswerANewRequest() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { first ->
                val old = async { runCatching { first.request("feed".toByteArray()) } }
                withTimeout(5_000) { node.feedReceived.await() }
                node.disconnect(wallet.publicKey)
                withTimeout(5_000) { first.awaitDisconnection() }
                assertTrue(old.await().isFailure)
                SaitoPeer(wallet, node.endpoint).use { replacement ->
                    val fresh = async { replacement.request("replacement-held".toByteArray()).decodeToString() }
                    withTimeout(5_000) { node.replacementReceived.await() }
                    // The node addresses both callbacks by the same key. Old reply arrives first.
                    node.releaseFeed()
                    node.releaseReplacement()
                    assertEquals("replacement-held", withTimeout(5_000) { fresh.await() })
                }
            }
        }
    }

    @Test fun sharedPublisherReportsWriteFailureWithinItsDrainBound() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { chat ->
                SaitoPeer(wallet, node.endpoint).use { publisher ->
                    val blocked = async { runCatching { chat.request("block".toByteArray()) } }
                    withTimeout(5_000) { node.blockReceived.await() }
                    val writes = (1..2).map { async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { publisher.request(ByteArray(5 * 1024 * 1024) { 120 }) }
                    } }
                    try {
                        val started = System.nanoTime()
                        val result = withTimeout(15_000) { runCatching { publisher.finishBroadcast() } }
                        assertTrue(result.exceptionOrNull() is java.io.IOException || result.exceptionOrNull() is TimeoutCancellationException)
                        val elapsed = System.nanoTime() - started
                        assertTrue("Exercise real write pressure before the transport/deadline failure", elapsed >= 9_000_000_000L)
                        assertTrue("The drain cannot exceed its twelve-second bound", elapsed < 13_000_000_000L)
                    } finally {
                        node.unblock(); blocked.cancelAndJoin(); writes.forEach { it.cancelAndJoin() }
                    }
                }
            }
        }
    }

    @Test fun solePublisherReportsCloseFailureWithinItsDrainBound() = runBlocking {
        Node().use { node ->
            SaitoPeer(wallet, node.endpoint).use { publisher ->
                publisher.request("ready".toByteArray())
                node.withholdCloseReplies = true
                val started = System.nanoTime()
                val finishing = async { runCatching { publisher.finishBroadcast() } }
                withTimeout(5_000) { node.closeRequested.await() }
                val result = withTimeout(15_000) { finishing.await() }
                assertTrue(result.exceptionOrNull() is java.io.IOException || result.exceptionOrNull() is TimeoutCancellationException)
                val elapsed = System.nanoTime() - started
                assertTrue("Exercise the real withheld-close timeout", elapsed >= 9_000_000_000L)
                assertTrue("The close cannot exceed its twelve-second bound", elapsed < 13_000_000_000L)
                node.withholdCloseReplies = false
            }
        }
    }

    /** Same callback address as pinned Saito: authenticated public key, then message index.
     * Holding a reply does not block the socket reader or the next request.
     */
    private class Node : Closeable {
        private val server = MockWebServer()
        private val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        private val nonce = ByteArray(32) { 7 }
        private val peers = ConcurrentHashMap<String, WebSocket>()
        private var held: Pair<String, Int>? = null
        private var replacementHeld: Pair<String, Int>? = null
        val replacementReceived = CompletableDeferred<Unit>()
        val feedReceived = CompletableDeferred<Unit>()
        val blockReceived = CompletableDeferred<Unit>()
        @Volatile var withholdCloseReplies = false
        val closeRequested = CompletableDeferred<Unit>()
        private val releaseBlock = java.util.concurrent.CountDownLatch(1)
        val acknowledgements = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val endpoint: String
        init {
            server.dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest) =
                    MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                        private var owner: String? = null
                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            val frame = bytes.toByteArray()
                            when (frame[0].toInt()) {
                                1 -> webSocket.send(SaitoWire.handshakeReply(identity, frame.copyOfRange(1, 33), nonce).toByteString())
                                2 -> {
                                    owner = requireNotNull(SaitoWire.verifyHandshake(frame, nonce))
                                    peers[owner!!] = webSocket
                                }
                                20 -> webSocket.send((byteArrayOf(9) + "relay||;chat||".toByteArray()).toByteString())
                                13, 14 -> { acknowledgements.trySend(frame) }
                                12 -> {
                                    val key = requireNotNull(owner)
                                    val index = ByteBuffer.wrap(frame, 1, 4).int
                                    val inputs = ByteBuffer.wrap(frame, 5, 4).int
                                    val outputs = ByteBuffer.wrap(frame, 9, 4).int
                                    val body = frame.copyOfRange(5 + 93 + 59 * (inputs + outputs), frame.size).decodeToString()
                                    if (body == "feed") { held = key to index; feedReceived.complete(Unit) }
                                    else if (body == "replacement-held") {
                                        replacementHeld = key to index; replacementReceived.complete(Unit)
                                    } else if (body == "block") {
                                        blockReceived.complete(Unit)
                                        check(releaseBlock.await(20, java.util.concurrent.TimeUnit.SECONDS))
                                        reply(key, index, body)
                                    } else reply(key, index, body)
                                }
                            }
                        }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            closeRequested.complete(Unit)
                            if (!withholdCloseReplies) webSocket.close(code, null)
                        }
                    }).build()
            }
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            endpoint = server.url("/wsopen").toString().replaceFirst("http", "ws")
        }
        private fun reply(key: String, index: Int, body: String) {
            check(requireNotNull(peers[key]).send((byteArrayOf(13) +
                ByteBuffer.allocate(4).putInt(index).array() + body.toByteArray()).toByteString()))
        }
        fun deliver(key: String, body: String, index: Int) { check(tryDeliver(key, body, index)) }
        fun tryDeliver(key: String, body: String, index: Int): Boolean =
            requireNotNull(peers[key]).send((byteArrayOf(12) + ByteBuffer.allocate(4).putInt(index).array() +
                body.toByteArray()).toByteString())
        fun releaseReplacement() { val (key, index) = requireNotNull(replacementHeld); reply(key, index, "replacement-held") }
        fun releaseFeed() { val (key, index) = requireNotNull(held); reply(key, index, "feed") }
        fun disconnect(key: String) { check(requireNotNull(peers[key]).close(1001, "Owned test disconnect")) }
        fun unblock() { releaseBlock.countDown() }
        override fun close() { unblock(); peers.values.forEach { it.close(1000, null) }; server.close() }
    }
}

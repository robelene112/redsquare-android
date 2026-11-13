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
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class SharedNodeConnectionTest {
    // Public, deterministic test identities only.
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")

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

    /** Same callback address as pinned Saito: authenticated public key, then message index.
     * Holding a reply does not block the socket reader or the next request.
     */
    private class Node : Closeable {
        private val server = MockWebServer()
        private val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        private val nonce = ByteArray(32) { 7 }
        private val peers = ConcurrentHashMap<String, WebSocket>()
        private var held: Pair<String, Int>? = null
        val feedReceived = CompletableDeferred<Unit>()
        val blockReceived = CompletableDeferred<Unit>()
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
                                    else if (body == "block") {
                                        blockReceived.complete(Unit)
                                        check(releaseBlock.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                        reply(key, index, body)
                                    } else reply(key, index, body)
                                }
                            }
                        }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
                    }).build()
            }
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            endpoint = server.url("/wsopen").toString().replaceFirst("http", "ws")
        }
        private fun reply(key: String, index: Int, body: String) {
            check(requireNotNull(peers[key]).send((byteArrayOf(13) +
                ByteBuffer.allocate(4).putInt(index).array() + body.toByteArray()).toByteString()))
        }
        fun deliver(key: String, body: String, index: Int) {
            check(requireNotNull(peers[key]).send((byteArrayOf(12) + ByteBuffer.allocate(4).putInt(index).array() +
                body.toByteArray()).toByteString()))
        }
        fun releaseFeed() { val (key, index) = requireNotNull(held); reply(key, index, "feed") }
        fun disconnect(key: String) { check(requireNotNull(peers[key]).close(1001, "Owned test disconnect")) }
        fun unblock() { releaseBlock.countDown() }
        override fun close() { unblock(); peers.values.forEach { it.close(1000, null) }; server.close() }
    }
}

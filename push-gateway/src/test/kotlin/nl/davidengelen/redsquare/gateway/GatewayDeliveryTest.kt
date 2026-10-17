package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayDeliveryTest {
    @get:Rule val directory = TemporaryFolder()

    @Test(timeout = 60_000) fun selectedArchiveReplyRetainsOneWakeAcrossLostSendAcknowledgementRestartAndOptOut() {
        val origin = "https://push.fixture.invalid"
        for (backend in SaitoBackend.entries) {
            val owner = SaitoWallet.create()
            val actor = SaitoWallet.create()
            val identity = SaitoWallet.create()
            val now = System.currentTimeMillis()
            fun post(wallet: SaitoWallet, text: String, parent: String = "") = SaitoTransaction.create(wallet,
                JsonObject().apply {
                    addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                    add("data", JsonObject().apply { addProperty("text", text); addProperty("parent_id", parent) })
                }, now - if (parent.isEmpty()) 2000 else 1000)
            val root = post(owner, "Owned parent")
            val reply = post(actor, "A fresh reply", root.id)
            val state = directory.newFolder("gateway-${backend.id}").toPath()
            val clientJournal = object : PushSubscriptionClient.Journal {
                private var value: String? = null
                override fun load() = value
                override fun save(value: String) { this.value = value }
            }
            val connection = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            fun request(port: Int, path: String, body: String, wallet: SaitoWallet? = null, wake: String? = null,
                bindingSignature: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                if (wallet != null) {
                    builder.header("X-Saito-Owner", wallet.publicKey)
                    builder.header("X-Saito-Signature", wallet.sign(
                        "Saito push resolve v1\n$origin/v1/wakes/resolve\n$wake".toByteArray(Charsets.UTF_8)))
                } else if (bindingSignature != null) builder.header("X-Saito-Signature", bindingSignature)
                return connection.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            fun bind(port: Int, enabled: Boolean) = PushSubscriptionClient(origin, clientJournal,
                PushSubscriptionClient.Http { suppliedOrigin, path, body, signature ->
                    assertEquals(origin, suppliedOrigin)
                    val response = request(port, path, body, bindingSignature = signature)
                    PushSubscriptionClient.Reply(response.statusCode(), response.body())
                }).reconcile { if (enabled) PushSubscriptionClient.Demand(owner, backend, "controlled-current-fid", "replies") else null }

            ArchivePeer(identity, listOf(root, reply), now).use { peer ->
                MockWebServer().use { fcm ->
                    val verifiedBeforeSend = java.util.concurrent.atomic.AtomicBoolean(false)
                    val attempts = java.util.concurrent.atomic.AtomicInteger()
                    fcm.dispatcher = object : mockwebserver3.Dispatcher() {
                        override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                            if (attempts.incrementAndGet() == 1) {
                                verifiedBeforeSend.set(peer.exactReads.containsAll(setOf(root.id, reply.id)))
                                return MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.CloseSocket()).build()
                            }
                            return MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/accepted\"}").build()
                        }
                    }
                    fcm.start(InetAddress.getByName("127.0.0.1"), 0)
                    val sender = FcmSender("owned-fixture", GoogleCredentials.create(
                        AccessToken("synthetic-owned-oauth", Date(now + 3_600_000))), "http://127.0.0.1:${fcm.port}")
                    val wake: String
                    val expected = JsonObject().apply {
                        addProperty("version", 1); addProperty("wallet", owner.publicKey)
                        addProperty("backend", backend.id); addProperty("signature", reply.id)
                    }
                    PushGateway(state, origin).use { gateway ->
                        gateway.start(); bind(gateway.port, true)
                        GatewayArchive(identity, backend, peer.origin).use { gateway.poll(it, sender) }
                        peer.check()
                        val sent = fcm.takeRequest(2, TimeUnit.SECONDS)
                        assertNotNull("A verified selected-backend reply must reach the outgoing transport", sent)
                        assertEquals("An uncertain send must wait for durable backoff instead of immediate HTTP replay", 1, fcm.requestCount)
                        assertTrue("Exact selected-Archive candidate and parent reads must precede the first send", verifiedBeforeSend.get())
                        val message = JsonParser.parseString(requireNotNull(requireNotNull(sent).body).utf8())
                            .asJsonObject.getAsJsonObject("message")
                        assertEquals("controlled-current-fid", message.get("fid").asString)
                        val data = message.getAsJsonObject("data")
                        assertEquals(setOf("version", "wake"), data.keySet())
                        wake = data.get("wake").asString
                        assertTrue(wake.matches(Regex("[0-9a-f]{64}")))
                        val body = "{\"version\":1,\"wake\":\"$wake\"}"
                        val resolved = request(gateway.port, "/v1/wakes/resolve", body, owner, wake)
                        assertEquals(200, resolved.statusCode()); assertEquals(expected, JsonParser.parseString(resolved.body()))
                        assertEquals(401, request(gateway.port, "/v1/wakes/resolve", body, actor, wake).statusCode())
                    }
                    PushGateway(state, origin).use { restarted ->
                        restarted.start()
                        val body = "{\"version\":1,\"wake\":\"$wake\"}"
                        val restored = request(restarted.port, "/v1/wakes/resolve", body, owner, wake)
                        assertEquals(200, restored.statusCode()); assertEquals(expected, JsonParser.parseString(restored.body()))
                        peer.exposeHead.set(false)
                        GatewayArchive(identity, backend, peer.origin).use { restarted.poll(it, sender) }
                        assertEquals("A pending send must respect its retained minimum delay", 1, fcm.requestCount)
                        Thread.sleep(11_100) // Actual durable10s retry minimum plus bounded jitter, not a fake clock.
                        GatewayArchive(identity, backend, peer.origin).use { restarted.poll(it, sender) }
                        val retried = requireNotNull(fcm.takeRequest(2, TimeUnit.SECONDS))
                        val retriedData = JsonParser.parseString(requireNotNull(retried.body).utf8())
                            .asJsonObject.getAsJsonObject("message").getAsJsonObject("data")
                        assertEquals("A lost acknowledgement must reuse the retained logical wake", wake, retriedData.get("wake").asString)
                        assertEquals(2, fcm.requestCount)
                        peer.exposeHead.set(true)
                        GatewayArchive(identity, backend, peer.origin).use { restarted.poll(it, sender) }
                        assertEquals("A retained accepted event must not create another wake", 2, fcm.requestCount)
                        assertTrue(peer.exactReads.containsAll(setOf(root.id, reply.id)))
                        bind(restarted.port, false)
                        assertEquals(410, request(restarted.port, "/v1/wakes/resolve", body, owner, wake).statusCode())
                        peer.add(post(actor, "A distinct reply after opt-out", root.id), now)
                        GatewayArchive(identity, backend, peer.origin).use { restarted.poll(it, sender) }
                        assertEquals("Opt-out must stop subsequent sends", 2, fcm.requestCount)
                        peer.check()
                    }
                }
            }
        }
    }

    /** The external peer owns signed fixture content and independently enforces the read-only query contract. */
    private class ArchivePeer(identity: SaitoWallet, posts: List<SaitoTransaction>, timestamp: Long) : Closeable {
        private val failure = AtomicReference<Throwable?>()
        private val server = MockWebServer()
        val exposeHead = java.util.concurrent.atomic.AtomicBoolean(true)
        val exactReads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val origin: String get() = "http://127.0.0.1:${server.port}"
        private val rows = java.util.concurrent.ConcurrentHashMap<String, JsonObject>()
        fun add(post: SaitoTransaction, timestamp: Long) {
            rows[post.id] = JsonObject().apply {
                addProperty("sig", post.id); addProperty("created_at", timestamp); addProperty("tx", post.envelope())
            }
        }
        init {
            posts.forEach { add(it, timestamp) }
            repeat(8) {
                val authenticated = java.util.concurrent.atomic.AtomicBoolean(false)
                val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture.
                val counter = ByteArray(32) { 7 }
                server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            val frame = bytes.toByteArray()
                            when (frame[0].toInt()) {
                                1 -> webSocket.send(SaitoWire.handshakeReply(serverWallet, frame.copyOfRange(1, 33), counter).toByteString())
                                2 -> { require(SaitoWire.verifyHandshake(frame, counter) == identity.publicKey); authenticated.set(true) }
                                20 -> webSocket.send((byteArrayOf(9) + "archive|Archive|".toByteArray()).toByteString())
                                12 -> {
                                    require(authenticated.get())
                                    val inputs = ByteBuffer.wrap(frame, 5, 4).int
                                    val outputs = ByteBuffer.wrap(frame, 9, 4).int
                                    val length = ByteBuffer.wrap(frame, 13, 4).int
                                    val offset = 5 + 93 + (inputs + outputs) * 59
                                    require(offset + length == frame.size)
                                    val request = JsonParser.parseString(String(frame, offset, length, Charsets.UTF_8)).asJsonObject
                                    require(request.keySet() == setOf("request", "data") && request.get("request").asString == "archive")
                                    val criteria = request.getAsJsonObject("data")
                                    require(criteria.get("request").asString == "load" && criteria.get("field1").asString == "RedSquare" &&
                                        criteria.get("flagged_ne").asInt == 1)
                                    val response = JsonArray()
                                    if (criteria.has("sig")) {
                                        require(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "sig") && criteria.get("limit").asInt == 1)
                                        val id = criteria.get("sig").asString
                                        exactReads.add(id); rows[id]?.let { response.add(it) }
                                    } else {
                                        require(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit") && criteria.get("limit").asInt == 100)
                                        if (exposeHead.get()) rows.values.forEach { response.add(it) }
                                    }
                                    webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toString().toByteArray()).toByteString())
                                }
                                else -> error("Gateway may only authenticate and read Archive")
                            }
                        } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Controlled protocol mismatch") }
                    }
                }).build())
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
        }
        fun check() { failure.get()?.let { throw AssertionError("Owned Archive rejected gateway request", it) } }
        override fun close() { server.close(); check() }
    }
}

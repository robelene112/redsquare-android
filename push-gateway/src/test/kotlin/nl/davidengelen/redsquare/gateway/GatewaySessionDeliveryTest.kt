package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoWire
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
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
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GatewaySessionDeliveryTest {
    @get:Rule val directory = TemporaryFolder()

    @Test(timeout = 30_000) fun observerDeliversVerifiedReplyWithPublicKeyRoutedArchiveResponses() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/gateway-social-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val now = fixture.get("now").asLong
        val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture, never fund.
        val origin = "https://push.fixture.invalid"
        for (backend in SaitoBackend.entries) {
            val identity = SaitoWallet.create()
            RoutedPeer(identity, fixture).use { peer ->
                MockWebServer().use { fcm ->
                    val verifiedBeforeSend = AtomicBoolean()
                    fcm.dispatcher = object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            verifiedBeforeSend.set(peer.exactReads.containsAll(listOf("root", "reply")
                                .map { fixture.getAsJsonObject(it).get("sig").asString }))
                            return MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/accepted\"}").build()
                        }
                    }
                    fcm.start(InetAddress.getByName("127.0.0.1"), 0)
                    FcmSender("owned-fixture", GoogleCredentials.create(AccessToken("synthetic-owned-oauth",
                        Date(System.currentTimeMillis() + 3_600_000))), "http://127.0.0.1:${fcm.port}", clock).use { sender ->
                        PushGateway(directory.newFolder(backend.id).toPath(), origin, clock = clock).use { gateway ->
                            gateway.start()
                            val connection = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                            fun request(path: String, body: JsonObject, signature: String? = null): JsonObject {
                                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}$path"))
                                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                signature?.let { request.header("X-Saito-Signature", it) }
                                val response = connection.send(request.build(), HttpResponse.BodyHandlers.ofString())
                                assertEquals(201, response.statusCode())
                                return JsonParser.parseString(response.body()).asJsonObject
                            }
                            val challenge = request("/v1/challenges", JsonObject().apply {
                                addProperty("version", 1); addProperty("owner", owner.publicKey)
                            }).get("nonce").asString
                            fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                                .joinToString("") { "%02x".format(it.toInt() and 255) }
                            val subscription = "17".repeat(32)
                            val installation = UUID.randomUUID().toString()
                            val expiry = now + 86_400_000
                            val revocation = sha("19".repeat(32))
                            val binding = JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", challenge); addProperty("subscription", subscription)
                                addProperty("installation", installation); addProperty("fid", "owned-session-fid")
                                addProperty("owner", owner.publicKey); addProperty("backend", backend.id); addProperty("categories", "replies")
                                addProperty("expiresAt", expiry); addProperty("revocationHash", revocation)
                            }
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$challenge\n$subscription\n$installation\n" +
                                "${sha("owned-session-fid")}\n${owner.publicKey}\n${backend.id}\nreplies\n$expiry\n$revocation"
                            request("/v1/subscriptions/bind", binding, owner.sign(intent.toByteArray()))
                            GatewayObserver(gateway, identity, backend, sender, intervalMillis = 60_000,
                                localChainOrigin = peer.origin,
                                localArchiveOrigin = peer.origin + if (backend == SaitoBackend.OFFICIAL) "/" else "").use { observer ->
                                observer.start()
                                val sent = fcm.takeRequest(5, TimeUnit.SECONDS)
                                peer.check()
                                assertNotNull("Verified reply must reach FCM on ${backend.id}; " +
                                    "connections=${peer.connections.get()}, misroutedArchiveReplies=${peer.misrouted.get()}", sent)
                                assertTrue("Exact signed reply and parent must be read before sending", verifiedBeforeSend.get())
                                val message = JsonParser.parseString(requireNotNull(sent?.body).utf8()).asJsonObject.getAsJsonObject("message")
                                assertEquals("owned-session-fid", message.get("fid").asString)
                                assertEquals(setOf("version", "wake"), message.getAsJsonObject("data").keySet())
                                assertEquals("1", message.getAsJsonObject("data").get("version").asString)
                                assertTrue(message.getAsJsonObject("data").get("wake").asString.matches(Regex("[0-9a-f]{64}")))
                                assertEquals(0, peer.misrouted.get())
                                assertEquals("One selected endpoint must have one authenticated observer session", 1, peer.connections.get())
                                assertEquals(1, fcm.requestCount)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Pinned214504e: chain responses use peer IDs; API responses use the last authenticated public-key socket.
     * Delay the first handshake so competing connections complete in the opposite order. No live node is used.
     */
    private class RoutedPeer(private val identity: SaitoWallet, private val fixture: JsonObject) : Closeable {
        private val server = MockWebServer()
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
        private val failure = AtomicReference<Throwable?>()
        private val byPublicKey = ConcurrentHashMap<String, WebSocket>()
        val connections = AtomicInteger()
        val misrouted = AtomicInteger()
        val exactReads = ConcurrentHashMap.newKeySet<String>()
        val origin: String get() = "http://127.0.0.1:${server.port}"
        private val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture.

        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.url.encodedPath.startsWith("/block/")) {
                        val block = fixture.getAsJsonObject("original")
                        require(request.url.encodedPath == "/block/${block.get("hash").asString}")
                        return MockResponse.Builder().body(Buffer().write(Base64.getDecoder()
                            .decode(block.get("fullBase64").asString))).build()
                    }
                    require(request.url.encodedPath == "/wsopen")
                    val number = connections.incrementAndGet()
                    return MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                        val nonce = ByteArray(32) { number.toByte() }
                        var authenticated = false
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            try {
                                val frame = bytes.toByteArray()
                                when (frame[0].toInt()) {
                                    1 -> scheduler.schedule({ webSocket.send(SaitoWire.handshakeReply(serverWallet,
                                        frame.copyOfRange(1, 33), nonce).toByteString()) }, if (number == 1) 250 else 0, TimeUnit.MILLISECONDS)
                                    2 -> {
                                        require(SaitoWire.verifyHandshake(frame, nonce) == identity.publicKey)
                                        authenticated = true
                                        byPublicKey[identity.publicKey] = webSocket
                                    }
                                    20 -> webSocket.send((byteArrayOf(9) + "archive|Archive|".toByteArray()).toByteString())
                                    21 -> {
                                        require(authenticated && frame.size == 111)
                                        val hash = fixture.getAsJsonObject("original").get("hash").asString
                                            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                        val body = ByteBuffer.allocate(249)
                                            .putLong(2_000_002).put(hash).put(ByteArray(32) { 1 })
                                            .putLong(0).put(ByteArray(32))
                                            .putLong(2_000_002).put(hash).putLong(2_000_002).put(hash).putInt(1)
                                            .putLong(2_000_002).put(hash).putLong(1_700_000_110_000).putInt(0).put(0).array()
                                        webSocket.send((byteArrayOf(22) + body).toByteString())
                                    }
                                    12 -> {
                                        require(authenticated)
                                        val inputs = ByteBuffer.wrap(frame, 5, 4).int
                                        val outputs = ByteBuffer.wrap(frame, 9, 4).int
                                        val length = ByteBuffer.wrap(frame, 13, 4).int
                                        val offset = 5 + 93 + (inputs + outputs) * 59
                                        require(offset + length == frame.size)
                                        val query = JsonParser.parseString(String(frame, offset, length, Charsets.UTF_8)).asJsonObject
                                        require(query.get("request").asString == "archive")
                                        val criteria = query.getAsJsonObject("data")
                                        require(criteria.get("request").asString == "load" && criteria.get("field1").asString == "RedSquare" &&
                                            criteria.get("flagged_ne").asInt == 1)
                                        val rows = JsonArray()
                                        if (criteria.has("sig")) {
                                            val signature = criteria.get("sig").asString
                                            exactReads.add(signature)
                                            listOf("root", "reply").map { fixture.getAsJsonObject(it) }
                                                .filter { it.get("sig").asString == signature }.forEach(rows::add)
                                        } // Head intentionally empty: only chain discovery can deliver this reply.
                                        val target = requireNotNull(byPublicKey[identity.publicKey])
                                        if (target !== webSocket) misrouted.incrementAndGet()
                                        target.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + rows.toString().toByteArray()).toByteString())
                                    }
                                    else -> error("Only authenticated read-only requests are expected")
                                }
                            } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Owned routing mismatch") }
                        }
                    }).build()
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
        }

        fun check() { failure.get()?.let { throw AssertionError("Owned routed peer failed", it) } }
        override fun close() { scheduler.shutdownNow(); server.close(); check() }
    }
}

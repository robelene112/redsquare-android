package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GatewayChainDeliveryTest {
    @get:Rule val directory = TemporaryFolder()
    @Test(timeout = 30_000) fun chainReplyIsVerifiedBeforeSendAndAbandonedPendingWakeIsWithdrawn() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/gateway-social-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val now = fixture.get("now").asLong
        val time = java.util.concurrent.atomic.AtomicLong(now)
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(time.get())
        }
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture, never fund.
        assertEquals(fixture.get("owner").asString, owner.publicKey)
        val original = fixture.getAsJsonObject("original")
        val alternate = fixture.getAsJsonObject("alternate")
        val reply = fixture.getAsJsonObject("reply")
        val root = fixture.getAsJsonObject("root")
        val origin = "https://push.fixture.invalid"
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        for (backend in SaitoBackend.entries) {
            time.set(now)
            val identity = SaitoWallet.create()
            val state = directory.newFolder("chain-social-${backend.id}").toPath()
            val calls = AtomicInteger()
            val failure = AtomicReference<Throwable?>()
            val downloads = java.util.concurrent.CopyOnWriteArrayList<String>()
            val connection = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            fun request(gateway: PushGateway, path: String, body: String, signature: String? = null,
                recipient: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}$path"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body))
                signature?.let { builder.header("X-Saito-Signature", it) }
                recipient?.let { builder.header("X-Saito-Owner", it) }
                return connection.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            GatewayDeliveryTest.ArchivePeer(identity, JsonArray().apply { add(root); add(reply) }).use { archivePeer ->
                archivePeer.exposeHead.set(false) // Exact chain-discovered IDs remain available; ordinary head discovery cannot pass.
                MockWebServer().use { chainPeer ->
                    chainPeer.dispatcher = object : mockwebserver3.Dispatcher() {
                        override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                            if (request.url.encodedPath.startsWith("/block/")) {
                                val hash = request.url.encodedPath.removePrefix("/block/")
                                val block = listOf(original, alternate).single { it.get("hash").asString == hash }
                                downloads.add(hash)
                                return MockResponse.Builder().body(Buffer().write(Base64.getDecoder()
                                    .decode(block.get("fullBase64").asString))).build()
                            }
                            require(request.url.encodedPath == "/wsopen")
                            return MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                                val nonce = ByteArray(32) { 7 }
                                var authenticated = false
                                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                                    try {
                                        val frame = bytes.toByteArray()
                                        when (frame[0].toInt()) {
                                            1 -> webSocket.send(SaitoWire.handshakeReply(owner, frame.copyOfRange(1, 33), nonce).toByteString())
                                            2 -> { require(SaitoWire.verifyHandshake(frame, nonce) == identity.publicKey); authenticated = true }
                                            20 -> webSocket.send(byteArrayOf(9).toByteString())
                                            21 -> {
                                                require(authenticated && frame.size == 111)
                                                val sequence = calls.incrementAndGet()
                                                require(sequence >= 1)
                                                val block = if (sequence == 1 || sequence >= 4) original else alternate
                                                if (sequence == 1) require(frame.copyOfRange(1, 73).all { it == 0.toByte() } && frame[73] == 1.toByte())
                                                else {
                                                    require(ByteBuffer.wrap(frame, 1, 8).long == 2_000_002L && frame[73] == 0.toByte())
                                                    require(frame.copyOfRange(9, 41).contentEquals(hex((if (sequence == 2 || sequence >= 5) original else alternate).get("hash").asString)))
                                                }
                                                val count = if (sequence in listOf(1, 2, 4)) 1 else 0
                                                val hash = hex(block.get("hash").asString)
                                                val body = ByteBuffer.allocate(196 + 53 * count)
                                                body.putLong(2_000_002).put(hash).put(ByteArray(32) { sequence.toByte() })
                                                body.putLong(if (sequence == 1) 0 else if (sequence == 2 || sequence == 4) 2_000_001 else 2_000_002)
                                                    .put(if (sequence == 1) ByteArray(32) else if (sequence == 2 || sequence == 4) ByteArray(32) { 0xab.toByte() } else hash)
                                                body.putLong(2_000_002).put(hash).putLong(2_000_002).put(hash).putInt(count)
                                                if (count == 1) body.putLong(2_000_002).put(hash).putLong(1_700_000_110_000).putInt(0).put(0)
                                                webSocket.send((byteArrayOf(22) + body.array()).toByteString())
                                            }
                                            else -> error("Only read-only chain requests are expected")
                                        }
                                    } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Owned chain mismatch") }
                                }
                            }).build()
                        }
                    }
                    chainPeer.start(InetAddress.getByName("127.0.0.1"), 0)
                    MockWebServer().use { fcm ->
                        val verifiedBeforeSend = java.util.concurrent.atomic.AtomicBoolean(false)
                        fcm.dispatcher = object : mockwebserver3.Dispatcher() {
                            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                                verifiedBeforeSend.set(archivePeer.exactReads.containsAll(setOf(root.get("sig").asString, reply.get("sig").asString)))
                                return MockResponse.Builder().code(503).addHeader("Retry-After", "60").build()
                            }
                        }
                        fcm.start(InetAddress.getByName("127.0.0.1"), 0)
                        val sender = FcmSender("owned-fixture", GoogleCredentials.create(AccessToken("synthetic-owned-oauth",
                            Date(System.currentTimeMillis() + 3_600_000))), "http://127.0.0.1:${fcm.port}", clock)
                        fun observe(gateway: PushGateway) {
                            GatewayChain(identity, backend, "http://127.0.0.1:${chainPeer.port}").use { chain ->
                                GatewayArchive(identity, backend, archivePeer.origin).use { archive -> gateway.observe(chain, archive, sender) }
                            }
                        }
                        fun resolve(gateway: PushGateway, wake: String) = request(gateway, "/v1/wakes/resolve",
                            "{\"version\":1,\"wake\":\"$wake\"}", owner.sign(
                                "Saito push resolve v1\n$origin/v1/wakes/resolve\n$wake".toByteArray()), owner.publicKey)
                        val wake: String
                        PushGateway(state, origin, clock = clock).use { gateway ->
                            gateway.start()
                            val challenge = request(gateway, "/v1/challenges", "{\"version\":1,\"owner\":\"${owner.publicKey}\"}")
                            assertEquals(201, challenge.statusCode())
                            val nonce = JsonParser.parseString(challenge.body()).asJsonObject.get("nonce").asString
                            val id = "17".repeat(32); val installation = UUID.randomUUID().toString(); val fid = "owned-chain-fid"
                            val expiry = now + 86_400_000; val revocation = sha("19".repeat(32))
                            val binding = JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", nonce); addProperty("subscription", id)
                                addProperty("installation", installation); addProperty("fid", fid); addProperty("owner", owner.publicKey)
                                addProperty("backend", backend.id); addProperty("categories", "replies")
                                addProperty("expiresAt", expiry); addProperty("revocationHash", revocation)
                            }
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$id\n$installation\n${sha(fid)}\n${owner.publicKey}\n${backend.id}\nreplies\n$expiry\n$revocation"
                            assertEquals(201, request(gateway, "/v1/subscriptions/bind", binding.toString(), owner.sign(intent.toByteArray())).statusCode())
                            observe(gateway)
                            val sent = requireNotNull(fcm.takeRequest(2, TimeUnit.SECONDS))
                            assertTrue("Selected Archive exact reply and parent must precede chain-triggered send", verifiedBeforeSend.get())
                            val message = JsonParser.parseString(requireNotNull(sent.body).utf8()).asJsonObject.getAsJsonObject("message")
                            assertEquals(fid, message.get("fid").asString)
                            val data = message.getAsJsonObject("data"); assertEquals(setOf("version", "wake"), data.keySet())
                            wake = data.get("wake").asString
                            val resolved = resolve(gateway, wake)
                            assertEquals(200, resolved.statusCode())
                            assertEquals(reply.get("sig").asString, JsonParser.parseString(resolved.body()).asJsonObject.get("signature").asString)
                            assertEquals(listOf(original.get("hash").asString), downloads.toList())
                        }
                        PushGateway(state, origin, clock = clock).use { restarted ->
                            restarted.start(); assertEquals(200, resolve(restarted, wake).statusCode())
                            observe(restarted)
                            assertEquals("A pending wake from an abandoned branch must be withdrawn", 410, resolve(restarted, wake).statusCode())
                            assertEquals(1, fcm.requestCount)
                            assertEquals(listOf(original, alternate).map { it.get("hash").asString }, downloads.toList())
                        }
                        time.set(now + 61_001) // Past Retry-After60s plus the maximum999ms jitter; still inside receipt/binding expiry.
                        PushGateway(state, origin, clock = clock).use { recovered ->
                            recovered.start(); assertEquals(410, resolve(recovered, wake).statusCode()); observe(recovered)
                            assertEquals(3, calls.get()); assertEquals(1, fcm.requestCount)
                            GatewayObserver(recovered, identity, backend, sender, intervalMillis = 100,
                                localChainOrigin = "http://127.0.0.1:${chainPeer.port}", localArchiveOrigin = archivePeer.origin).use { observer ->
                                observer.start()
                                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                                while (calls.get() < 5 && System.nanoTime() < deadline) Thread.sleep(20)
                                assertTrue("Automatic observer must perform successive selected-backend passes", calls.get() >= 5)
                                val restoredSend = requireNotNull(fcm.takeRequest(2, TimeUnit.SECONDS))
                                val restoredWake = JsonParser.parseString(requireNotNull(restoredSend.body).utf8()).asJsonObject
                                    .getAsJsonObject("message").getAsJsonObject("data").get("wake").asString
                                assertEquals("Automatic verified re-inclusion must restore the same logical wake", wake, restoredWake)
                                assertEquals(200, resolve(recovered, wake).statusCode()); assertEquals(2, fcm.requestCount)
                                assertEquals(listOf(original, alternate, original).map { it.get("hash").asString }, downloads.toList())
                            }
                        }
                    }
                    failure.get()?.let { throw AssertionError("Owned chain failed", it) }
                }
            }
        }
    }
}

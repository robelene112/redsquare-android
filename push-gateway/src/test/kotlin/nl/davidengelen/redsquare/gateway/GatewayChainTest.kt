package nl.davidengelen.redsquare.gateway

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
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.AccessToken
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GatewayChainTest {
    @get:Rule val directory = TemporaryFolder()
    @Test(timeout = 30_000) fun bootstrapReadsEveryFullBlockThenContinuesFromTheProcessedCursor() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val known = listOf("oddBlock", "chainTip").map { fixture.getAsJsonObject(it) }
        val hashes = known.map { it.get("hash").asString }
        val alternate = listOf("alternateBlock", "alternateTip").map { fixture.getAsJsonObject(it) }
        val alternateHashes = alternate.map { it.get("hash").asString }
        val heights = listOf(2_000_002L, 2_000_003L)
        val times = listOf(1_700_000_010_000L, 1_700_000_020_000L)
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val fork = ByteArray(32) { 11 }
        val alternateFork = ByteArray(32) { 12 }
        for (backend in SaitoBackend.entries) {
            val identity = SaitoWallet.create()
            val failure = AtomicReference<Throwable?>()
            val reads = AtomicInteger()
            val downloads = java.util.concurrent.CopyOnWriteArrayList<String>()
            val peerKey = AtomicReference<ByteArray?>()
            val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture.
            val nonce = ByteArray(32) { 7 }
            MockWebServer().use { server ->
                server.dispatcher = object : mockwebserver3.Dispatcher() {
                    override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                        val path = request.url.encodedPath
                        if (request.method == "GET" && path.startsWith("/block/")) {
                            val hash = path.removePrefix("/block/")
                            val full = (known + alternate).firstOrNull { it.get("hash").asString == hash }
                            requireNotNull(full)
                            downloads.add(hash)
                            return MockResponse.Builder().body(Buffer().write(Base64.getDecoder()
                                .decode(full.get("fullBase64").asString))).build()
                        }
                        require(request.method == "GET" && path == "/wsopen")
                        return MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                                try {
                                    val frame = bytes.toByteArray()
                                    when (frame[0].toInt()) {
                                        1 -> webSocket.send(SaitoWire.handshakeReply(serverWallet, frame.copyOfRange(1, 33), nonce).toByteString())
                                        2 -> {
                                            require(SaitoWire.verifyHandshake(frame, nonce) == identity.publicKey)
                                            peerKey.set(frame.copyOfRange(1, 34))
                                        }
                                        20 -> webSocket.send(byteArrayOf(9).toByteString())
                                        21 -> {
                                            require(frame.size == 111 && frame.copyOfRange(107, 111).all { it == 0.toByte() })
                                            require(frame.copyOfRange(74, 107).contentEquals(requireNotNull(peerKey.get())))
                                            val sequence = reads.incrementAndGet()
                                            val continuation = ByteBuffer.wrap(frame, 1, 8).long != 0L
                                            require(sequence in 1..6 && continuation == (sequence !in listOf(1, 3)))
                                            val changed = sequence >= 5
                                            val responseHashes = if (changed) alternateHashes else hashes
                                            val responseFork = if (changed) alternateFork else fork
                                            if (!continuation) {
                                                require(frame.copyOfRange(1, 73).all { it == 0.toByte() } && frame[73] == 1.toByte())
                                            } else {
                                                require(ByteBuffer.wrap(frame, 1, 8).long == heights.last() && frame[73] == 0.toByte())
                                                require(frame.copyOfRange(9, 41).contentEquals(hex(if (sequence == 6) alternateHashes.last() else hashes.last())))
                                                require(frame.copyOfRange(41, 73).contentEquals(if (sequence == 6) alternateFork else fork))
                                            }
                                            // Pinned wire schema:196-byte body header,53 bytes per reference.
                                            val count = if (!continuation || sequence == 5) 2 else 0
                                            val body = ByteBuffer.allocate(196 + 53 * count)
                                            body.putLong(heights.last()).put(hex(responseHashes.last())).put(responseFork)
                                            val ancestorHeight = if (sequence == 5) 2_000_001L else if (continuation) heights.last() else 0L
                                            val ancestorHash = if (sequence == 5) hex("ab".repeat(32)) else if (continuation) hex(responseHashes.last()) else ByteArray(32)
                                            body.putLong(ancestorHeight).put(ancestorHash)
                                            body.putLong(if (count == 0) heights.last() else heights.first())
                                                .put(hex(if (count == 0) responseHashes.last() else responseHashes.first()))
                                            body.putLong(heights.last()).put(hex(responseHashes.last())).putInt(count)
                                            repeat(count) { index ->
                                                // SPV relevance is zero for both; neither full block may be skipped.
                                                body.putLong(heights[index]).put(hex(responseHashes[index])).putLong(times[index]).putInt(0).put(0)
                                            }
                                            webSocket.send((byteArrayOf(22) + body.array()).toByteString())
                                        }
                                        else -> error("Chain reader may only authenticate and request chain data")
                                    }
                                } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Controlled chain mismatch") }
                            }
                        }).build()
                    }
                }
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                GatewayChain(identity, backend, "http://127.0.0.1:${server.port}").use { chain ->
                    val page = chain.range()
                    failure.get()?.let { throw AssertionError("Owned chain rejected gateway request", it) }
                    assertEquals("Bootstrap must expose both full-block references", 2, page.references.size)
                    assertEquals(hashes, page.references.map { it.hash })
                    val blocks = page.references.map(chain::block)
                    assertEquals(hashes, blocks.map { it.hash })
                    assertEquals(heights.map(BigInteger::valueOf), blocks.map { it.height })
                    assertEquals(blocks.first().hash, blocks.last().previousHash)
                    val last = blocks.last()
                    val next = chain.range(GatewayChain.Cursor(last.height, last.hash, page.tip.forkId))
                    assertTrue(next.references.isEmpty())
                    assertEquals(last.height, next.ancestorHeight); assertEquals(last.hash, next.ancestorHash)
                    assertEquals(2, reads.get()); assertEquals(hashes, downloads.toList())
                }
                val state = directory.newFolder("chain-${backend.id}").toPath()
                val sender = FcmSender("owned-fixture", GoogleCredentials.create(AccessToken("synthetic-owned-oauth",
                    Date(System.currentTimeMillis() + 3_600_000))), "http://127.0.0.1:${server.port}")
                fun observe(gateway: PushGateway) {
                    GatewayChain(identity, backend, "http://127.0.0.1:${server.port}").use { chain ->
                        GatewayArchive(identity, backend, "http://127.0.0.1:${server.port}").use { archive ->
                            gateway.observe(chain, archive, sender)
                        }
                    }
                }
                fun cursor(gateway: PushGateway): com.google.gson.JsonObject {
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}/v1/observer/status"))
                        .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(
                            "{\"version\":1,\"backend\":\"${backend.id}\"}"))
                    val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                        .send(request.build(), HttpResponse.BodyHandlers.ofString())
                    assertEquals(200, response.statusCode())
                    val status = JsonParser.parseString(response.body()).asJsonObject
                    assertNotNull("Processed chain cursor must be durably observable", status.get("cursor"))
                    if (reads.get() >= 5) assertEquals("chain-reorganization", status.getAsJsonObject("gap").get("reason").asString)
                    return status.getAsJsonObject("cursor")
                }
                PushGateway(state, "https://push.fixture.invalid").use { gateway ->
                    gateway.start(); observe(gateway)
                    val processed = cursor(gateway)
                    assertEquals("2000003", processed.get("height").asString)
                    assertEquals(hashes.last(), processed.get("hash").asString)
                }
                PushGateway(state, "https://push.fixture.invalid").use { restarted ->
                    restarted.start()
                    assertEquals(hashes.last(), cursor(restarted).get("hash").asString)
                    observe(restarted)
                    assertEquals("2000003", cursor(restarted).get("height").asString)
                    assertEquals(4, reads.get())
                    assertEquals(hashes + hashes, downloads.toList())
                    observe(restarted)
                    assertEquals("Replacement branch must become the processed cursor", alternateHashes.last(), cursor(restarted).get("hash").asString)
                    assertEquals(hashes + hashes + alternateHashes, downloads.toList())
                }
                PushGateway(state, "https://push.fixture.invalid").use { recovered ->
                    recovered.start()
                    assertEquals(alternateHashes.last(), cursor(recovered).get("hash").asString)
                    observe(recovered)
                    assertEquals(alternateHashes.last(), cursor(recovered).get("hash").asString)
                    assertEquals(6, reads.get())
                    assertEquals(hashes + hashes + alternateHashes, downloads.toList())
                }
                failure.get()?.let { throw AssertionError("Owned chain peer failed", it) }
            }
        }
    }
}

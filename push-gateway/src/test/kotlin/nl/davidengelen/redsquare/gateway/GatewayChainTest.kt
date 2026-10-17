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
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GatewayChainTest {
    @Test(timeout = 30_000) fun bootstrapReadsEveryFullBlockThenContinuesFromTheProcessedCursor() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-block-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val known = listOf("oddBlock", "chainTip").map { fixture.getAsJsonObject(it) }
        val hashes = known.map { it.get("hash").asString }
        val heights = listOf(2_000_002L, 2_000_003L)
        val times = listOf(1_700_000_010_000L, 1_700_000_020_000L)
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val fork = ByteArray(32) { 11 }
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
                            val index = hashes.indexOf(hash)
                            require(index >= 0)
                            downloads.add(hash)
                            return MockResponse.Builder().body(Buffer().write(Base64.getDecoder()
                                .decode(known[index].get("fullBase64").asString))).build()
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
                                            val continuation = reads.getAndIncrement() != 0
                                            if (!continuation) {
                                                require(frame.copyOfRange(1, 73).all { it == 0.toByte() } && frame[73] == 1.toByte())
                                            } else {
                                                require(ByteBuffer.wrap(frame, 1, 8).long == heights.last() && frame[73] == 0.toByte())
                                                require(frame.copyOfRange(9, 41).contentEquals(hex(hashes.last())))
                                                require(frame.copyOfRange(41, 73).contentEquals(fork))
                                            }
                                            // Pinned wire schema:196-byte body header,53 bytes per reference.
                                            val count = if (continuation) 0 else 2
                                            val body = ByteBuffer.allocate(196 + 53 * count)
                                            body.putLong(heights.last()).put(hex(hashes.last())).put(fork)
                                            body.putLong(if (continuation) heights.last() else 0L)
                                                .put(if (continuation) hex(hashes.last()) else ByteArray(32))
                                            body.putLong(if (continuation) heights.last() else heights.first())
                                                .put(hex(if (continuation) hashes.last() else hashes.first()))
                                            body.putLong(heights.last()).put(hex(hashes.last())).putInt(count)
                                            repeat(count) { index ->
                                                // SPV relevance is zero for both; neither full block may be skipped.
                                                body.putLong(heights[index]).put(hex(hashes[index])).putLong(times[index]).putInt(0).put(0)
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
                failure.get()?.let { throw AssertionError("Owned chain peer failed", it) }
            }
        }
    }
}

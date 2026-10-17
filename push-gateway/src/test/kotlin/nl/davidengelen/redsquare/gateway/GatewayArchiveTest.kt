package nl.davidengelen.redsquare.gateway

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
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GatewayArchiveTest {
    @Test(timeout = 30_000) fun gatewayIdentityReadsSignedRootsRepliesAndMentionsFromTheSelectedArchive() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-notification-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val rows = JsonArray().apply {
            for (name in listOf("root", "incomingReply", "incomingMention")) add(fixture.get(name))
        }
        val expectedIds = rows.map { it.asJsonObject.get("sig").asString }.toSet()
        val gatewayIdentity = SaitoWallet.create()
        assertNotEquals(fixture.get("owner").asString, gatewayIdentity.publicKey)
        assertNotEquals(fixture.get("actor").asString, gatewayIdentity.publicKey)
        for (backend in SaitoBackend.entries) {
            val failure = AtomicReference<Throwable?>()
            val authenticated = AtomicReference<String?>()
            val reads = AtomicInteger()
            val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture.
            val counter = ByteArray(32) { 7 }
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            val frame = bytes.toByteArray()
                            when (frame[0].toInt()) {
                                1 -> {
                                    require(frame.size == 33)
                                    webSocket.send(SaitoWire.handshakeReply(serverWallet,
                                        frame.copyOfRange(1, 33), counter).toByteString())
                                }
                                2 -> {
                                    require(frame.size == 130 && frame.copyOfRange(98, 130).all { it == 0.toByte() })
                                    val actual = requireNotNull(SaitoWire.verifyHandshake(frame, counter))
                                    require(actual == gatewayIdentity.publicKey)
                                    authenticated.set(actual)
                                }
                                20 -> webSocket.send((byteArrayOf(9) + "archive|Archive|".toByteArray()).toByteString())
                                12 -> {
                                    require(authenticated.get() == gatewayIdentity.publicKey && frame.size >= 334)
                                    val inputCount = ByteBuffer.wrap(frame, 5, 4).int
                                    val outputCount = ByteBuffer.wrap(frame, 9, 4).int
                                    val length = ByteBuffer.wrap(frame, 13, 4).int
                                    val offset = 5 + 93 + (inputCount + outputCount) * 59
                                    require(offset + length == frame.size)
                                    val request = JsonParser.parseString(String(frame, offset, length, Charsets.UTF_8)).asJsonObject
                                    require(request.keySet() == setOf("request", "data") && request.get("request").asString == "archive")
                                    val criteria = request.getAsJsonObject("data")
                                    // No root-only or recipient filter: content-only mentions and replies remain visible.
                                    require(criteria == JsonParser.parseString(
                                        "{\"request\":\"load\",\"field1\":\"RedSquare\",\"flagged_ne\":1,\"limit\":100}"))
                                    reads.incrementAndGet()
                                    webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) +
                                        rows.toString().toByteArray(Charsets.UTF_8)).toByteString())
                                }
                                else -> error("Read-only gateway must not broadcast or write application data")
                            }
                        } catch (caught: Throwable) {
                            failure.set(caught); webSocket.close(1008, "Controlled protocol mismatch")
                        }
                    }
                }).build())
                server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                GatewayArchive(gatewayIdentity, backend, "http://127.0.0.1:${server.port}").use { archive ->
                    val page = archive.recentSocial()
                    failure.get()?.let { throw AssertionError("Owned Archive peer rejected the request", it) }
                    assertEquals("Selected Archive must expose all three signed candidates", 3, page.posts.size)
                    assertEquals(expectedIds, page.posts.map { it.id }.toSet())
                    assertEquals(3, page.rowCount); assertEquals(0, page.ignored)
                    assertTrue(page.posts.any { it.parentId == fixture.getAsJsonObject("root").get("sig").asString })
                    assertEquals(gatewayIdentity.publicKey, authenticated.get())
                    assertEquals(1, reads.get())
                }
                failure.get()?.let { throw AssertionError("Owned Archive peer failed", it) }
            }
        }
    }
}

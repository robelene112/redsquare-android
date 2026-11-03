package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.RedSquareLikes
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoWire
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The fixture's real authenticated binary boundary, using independent signed Archive rows. */
class ControlledLikeArchiveContractTest {
    @Test fun exactReceiptAndIncomingAndAuthorHistoryReturnTheirSignedRows() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val row = fixture.getAsJsonObject("incomingLike")
        val event = RedSquareLikes.decode(row.get("tx").asString)
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey // Public unsafe fixture only.
        val observed = java.util.concurrent.CopyOnWriteArrayList<JsonObject>()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria -> observed.add(criteria.deepCopy()); "[$row]" })
        withSocket(peer) { request ->
            val exact = query().apply { remove("limit"); addProperty("sig", event.id) }
            assertEquals(event.id, RedSquareLikes.readArchive(request(exact), event.author).likes.single().id)
            val incoming = query().apply {
                addProperty("field3", owner); addProperty("flagged_ne", 1)
                addProperty("created_earlier_than", event.createdAt + 1)
            }
            assertEquals(event.id, RedSquareLikes.readIncoming(request(incoming), owner).likes.single().id)
            val profile = query().apply {
                addProperty("field2", event.author); addProperty("limit", 20)
                addProperty("created_earlier_than", event.createdAt + 1)
            }
            assertEquals(event.id, RedSquareLikes.readArchive(request(profile), event.author).likes.single().id)
            profile.addProperty("created_earlier_than", event.createdAt)
            assertEquals(event.envelope, RedSquareLikes.readArchive(request(profile), event.author).likes.single().envelope)
            peer.assertHealthy()
            assertEquals(listOf(exact, incoming, profile.deepCopy().apply { addProperty("created_earlier_than", event.createdAt + 1) }, profile), observed.toList())
        }
    }

    @Test fun malformedReceiptOrUnboundedOrUnscopedHistoryIsStillRejected() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey // Public unsafe fixture only.
        val invalid = listOf(
            query().apply { addProperty("sig", "not-a-signature") },
            query(),
            query().apply { addProperty("field2", owner); addProperty("limit", 101) },
            query().apply { addProperty("field3", "not-an-identity"); addProperty("flagged_ne", 1) }
        )
        invalid.forEach { criteria ->
            val called = java.util.concurrent.atomic.AtomicBoolean(false)
            val peer = ControlledFeedPeer("[]", archiveResponse = { called.set(true); "[]" })
            assertThrows(AssertionError::class.java) {
                withSocket(peer) { request -> request(criteria) }
            }
            assertFalse("Invalid query reached callback", called.get())
        }
    }

    private fun query() = JsonObject().apply {
        addProperty("request", "load"); addProperty("field1", "RedSquareLike"); addProperty("limit", 100)
    }

    private fun withSocket(peer: ControlledFeedPeer, block: ((JsonObject) -> String) -> Unit) {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val nonce = ByteArray(32) { 5 }
        val ready = java.util.concurrent.CountDownLatch(1)
        val replies = LinkedBlockingQueue<ByteArray>()
        val client = OkHttpClient()
        peer.start().use { server ->
            val socket = client.newWebSocket(Request.Builder().url("ws://127.0.0.1:${server.port}/wsopen").build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send((byteArrayOf(1) + nonce).toByteString()) }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val frame = bytes.toByteArray()
                    if (frame[0] == 2.toByte()) {
                        assertNotNull(SaitoWire.verifyHandshake(frame, nonce))
                        webSocket.send(SaitoWire.handshakeReply(wallet, frame.copyOfRange(98, 130), ByteArray(32)).toByteString())
                        ready.countDown()
                    } else if (frame[0] == 13.toByte()) replies.add(frame)
                }
            })
            try {
                assertTrue("Authenticated socket was not ready", ready.await(5, TimeUnit.SECONDS))
                var index = 0
                block { criteria ->
                    val message = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                    socket.send(SaitoWire.applicationRequest(wallet.publicKey, message.toString().toByteArray(), ++index, 1).toByteString())
                    peer.assertRequestObserved()
                    val reply = replies.poll(2, TimeUnit.SECONDS)
                    peer.assertHealthy()
                    assertNotNull("No Archive response", reply)
                    reply!!.copyOfRange(5, reply.size).toString(Charsets.UTF_8)
                }
            } finally {
                socket.cancel(); client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
            }
        }
    }
}

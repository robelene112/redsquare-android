package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.PrivateChat
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoWire
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayPrivateChatDeliveryTest {
    @get:Rule val directory = TemporaryFolder()

    @Test(timeout = 30_000) fun privateCandidateProducesOnlyAnOpaqueWakeAndRecipientProofRetrievesItsOriginal() = privateJourney()

    @Test(timeout = 30_000) fun oneAuthenticatedStreamDeliversSuccessiveMessagesWithoutRepeatingAnOriginal() = privateJourney(continueStream = true)

    @Test(timeout = 60_000) fun pendingPrivateDeliveryAndCiphertextSurviveRestartThenTheLiveStreamReconnectsWithoutDuplicates() =
        privateJourney(lifecycle = true)

    private fun privateJourney(continueStream: Boolean = false, lifecycle: Boolean = false) {
        // Published unsafe fixture identities. Never fund or use outside owned fixtures.
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val text = "An ordinary background private message"
        val origin = "https://push.fixture.invalid"
        for (backend in SaitoBackend.entries) {
            val now = System.currentTimeMillis()
            val identity = SaitoWallet.create()
            assertNotEquals(recipient.publicKey, identity.publicKey)
            val original = PrivateChat.create(author, recipient.publicKey, text, now)
            // Fresh ordinary transport fixture; independent encryption interoperability stays with the pinned core cases.
            val failure = AtomicReference<Throwable?>()
            val stream = AtomicReference<WebSocket?>()
            val state = directory.newFolder(backend.id).toPath()
            var port = 0
            var retainedWake: String? = null
            val connection = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()
            fun post(path: String, body: JsonObject, signature: String? = null, status: Int = 200): JsonObject {
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(java.time.Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .header("X-Saito-Owner", recipient.publicKey)
                signature?.let { request.header("X-Saito-Signature", it) }
                val response = connection.send(request.build(), HttpResponse.BodyHandlers.ofString())
                assertEquals("HTTP $path", status, response.statusCode())
                return JsonParser.parseString(response.body()).asJsonObject
            }
            fun resolve(wake: String, operation: String = "resolve") = post("/v1/wakes/$operation", JsonObject().apply {
                addProperty("version", 1); addProperty("wake", wake)
            }, recipient.sign("Saito push $operation v1\n$origin/v1/wakes/$operation\n$wake".toByteArray()))
            MockWebServer().use { peer ->
                val counter = ByteArray(32) { 7 }
                fun upgrade(candidates: List<SaitoTransaction>) = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            val frame = bytes.toByteArray()
                            when (frame[0].toInt()) {
                                1 -> webSocket.send(SaitoWire.handshakeReply(serverWallet,
                                    frame.copyOfRange(1, 33), counter).toByteString())
                                2 -> {
                                    require(SaitoWire.verifyHandshake(frame, counter) == identity.publicKey)
                                    stream.set(webSocket)
                                    candidates.forEach { webSocket.send(it.broadcast(author, identity.publicKey).toByteString()) }
                                }
                                20 -> webSocket.send(byteArrayOf(9).toByteString())
                                16 -> Unit
                                else -> error("The private observer must only authenticate and listen")
                            }
                        } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Owned Chat mismatch") }
                    }
                }).build()
                peer.enqueue(upgrade(listOf(original)))
                peer.start(InetAddress.getByName("127.0.0.1"), 0)
                MockWebServer().use { fcm ->
                    val attempts = CopyOnWriteArrayList<Long>()
                    if (lifecycle) fcm.dispatcher = object : mockwebserver3.Dispatcher() {
                        override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                            attempts.add(System.nanoTime())
                            return if (attempts.size == 1) MockResponse.Builder().code(503).build()
                            else MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/recovered\"}").build()
                        }
                    } else fcm.enqueue(MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/private\"}").build())
                    fcm.start(InetAddress.getByName("127.0.0.1"), 0)
                    FcmSender("owned-fixture", GoogleCredentials.create(AccessToken("synthetic-owned-oauth", Date(now + 3_600_000))),
                        "http://127.0.0.1:${fcm.port}").use { sender ->
                        PushGateway(state, origin).use { gateway ->
                            gateway.start()
                            port = gateway.port
                            val nonce = post("/v1/challenges", JsonObject().apply {
                                addProperty("version", 1); addProperty("owner", recipient.publicKey)
                            }, status = 201).get("nonce").asString
                            fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                                .joinToString("") { "%02x".format(it.toInt() and 255) }
                            val subscription = "17".repeat(32)
                            val installation = UUID.randomUUID().toString()
                            val expiry = now + 86_400_000
                            val revocation = sha("19".repeat(32))
                            val fid = "owned-private-fid"
                            val categories = "private-chats"
                            val binding = JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", nonce); addProperty("subscription", subscription)
                                addProperty("installation", installation); addProperty("fid", fid); addProperty("owner", recipient.publicKey)
                                addProperty("backend", backend.id); addProperty("categories", categories)
                                addProperty("expiresAt", expiry); addProperty("revocationHash", revocation)
                            }
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$subscription\n$installation\n" +
                                "${sha(fid)}\n${recipient.publicKey}\n${backend.id}\n$categories\n$expiry\n$revocation"
                            post("/v1/subscriptions/bind", binding, recipient.sign(intent.toByteArray()), 201)
                            GatewayChatObserver(gateway, identity, backend, sender, "http://127.0.0.1:${peer.port}").use { observer ->
                                observer.start()
                                val sent = fcm.takeRequest(5, TimeUnit.SECONDS)
                                failure.get()?.let { throw AssertionError("Owned incoming Chat failed", it) }
                                assertNotNull("A propagated private candidate must produce an opaque wake", sent)
                                val message = JsonParser.parseString(requireNotNull(sent?.body).utf8()).asJsonObject.getAsJsonObject("message")
                                assertEquals(setOf("fid", "data", "android"), message.keySet())
                                assertEquals(fid, message.get("fid").asString)
                                val data = message.getAsJsonObject("data")
                                assertEquals(setOf("version", "wake"), data.keySet())
                                assertEquals("1", data.get("version").asString)
                                val wake = data.get("wake").asString
                                retainedWake = wake
                                assertTrue(wake.matches(Regex("[0-9a-f]{64}")))
                                val request = JsonObject().apply { addProperty("version", 1); addProperty("wake", wake) }
                                val resolved = post("/v1/wakes/resolve", request, recipient.sign(
                                    "Saito push resolve v1\n$origin/v1/wakes/resolve\n$wake".toByteArray()))
                                assertEquals(setOf("version", "wallet", "backend", "signature", "kind"), resolved.keySet())
                                assertEquals("private-chat", resolved.get("kind").asString)
                                assertEquals(recipient.publicKey, resolved.get("wallet").asString)
                                assertEquals(backend.id, resolved.get("backend").asString)
                                assertEquals(original.id, resolved.get("signature").asString)
                                val retained = post("/v1/wakes/original", request, recipient.sign(
                                    "Saito push original v1\n$origin/v1/wakes/original\n$wake".toByteArray()))
                                assertEquals(setOf("version", "envelope"), retained.keySet())
                                val received = PrivateChat.recover(recipient, retained.get("envelope").asString)
                                assertEquals(original.id, received.message.id)
                                assertEquals(text, received.message.text)
                                assertEquals(author.publicKey, received.counterparty)
                                assertEquals(1, fcm.requestCount)
                                if (continueStream) {
                                    fcm.enqueue(MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/next-private\"}").build())
                                    val next = PrivateChat.create(author, recipient.publicKey, "Another message on the same connection",
                                        System.currentTimeMillis())
                                    val socket = requireNotNull(stream.get())
                                    assertTrue(socket.send(original.broadcast(author, identity.publicKey).toByteString()))
                                    assertTrue(socket.send(next.broadcast(author, identity.publicKey).toByteString()))
                                    val nextSent = fcm.takeRequest(5, TimeUnit.SECONDS)
                                    failure.get()?.let { throw AssertionError("Owned continuing Chat failed", it) }
                                    assertNotNull("The existing authenticated stream must deliver the next distinct message", nextSent)
                                    val nextMessage = JsonParser.parseString(requireNotNull(nextSent?.body).utf8())
                                        .asJsonObject.getAsJsonObject("message")
                                    assertEquals(setOf("fid", "data", "android"), nextMessage.keySet())
                                    assertEquals(fid, nextMessage.get("fid").asString)
                                    val nextData = nextMessage.getAsJsonObject("data")
                                    assertEquals(setOf("version", "wake"), nextData.keySet())
                                    assertEquals("1", nextData.get("version").asString)
                                    val nextWake = nextData.get("wake").asString
                                    assertNotEquals(wake, nextWake)
                                    val nextRequest = JsonObject().apply { addProperty("version", 1); addProperty("wake", nextWake) }
                                    val nextResolved = post("/v1/wakes/resolve", nextRequest, recipient.sign(
                                        "Saito push resolve v1\n$origin/v1/wakes/resolve\n$nextWake".toByteArray()))
                                    assertEquals(next.id, nextResolved.get("signature").asString)
                                    val nextRetained = post("/v1/wakes/original", nextRequest, recipient.sign(
                                        "Saito push original v1\n$origin/v1/wakes/original\n$nextWake".toByteArray()))
                                    val nextReceived = PrivateChat.recover(recipient, nextRetained.get("envelope").asString)
                                    assertEquals(next.id, nextReceived.message.id)
                                    assertEquals("Another message on the same connection", nextReceived.message.text)
                                    assertEquals(author.publicKey, nextReceived.counterparty)
                                    assertEquals("Repeating the first original must not send another alert", 2, fcm.requestCount)
                                }
                            }
                        }
                        if (lifecycle) {
                            val wake = requireNotNull(retainedWake)
                            PushGateway(state, origin).use { restarted ->
                                restarted.start(); port = restarted.port
                                val restored = resolve(wake)
                                assertEquals(original.id, restored.get("signature").asString)
                                assertEquals(backend.id, restored.get("backend").asString)
                                val cached = PrivateChat.recover(recipient, resolve(wake, "original").get("envelope").asString)
                                assertEquals(text, cached.message.text)
                                assertEquals(original.id, cached.message.id)
                                assertEquals(1, fcm.requestCount)
                                peer.enqueue(upgrade(listOf(original)))
                                GatewayChatObserver(restarted, identity, backend, sender, "http://127.0.0.1:${peer.port}").use { observer ->
                                    observer.start()
                                    val retried = requireNotNull(fcm.takeRequest(15, TimeUnit.SECONDS))
                                    val retryData = JsonParser.parseString(requireNotNull(retried.body).utf8())
                                        .asJsonObject.getAsJsonObject("message").getAsJsonObject("data")
                                    assertEquals(setOf("version", "wake"), retryData.keySet())
                                    assertEquals("1", retryData.get("version").asString)
                                    assertEquals("A restart must reuse the pending logical wake", wake, retryData.get("wake").asString)
                                    assertEquals(2, fcm.requestCount)
                                    assertTrue("The recovered send must respect the actual minimum backoff",
                                        attempts[1] - attempts[0] >= TimeUnit.SECONDS.toNanos(10))
                                    val next = PrivateChat.create(author, recipient.publicKey, "A private message after reconnect", System.currentTimeMillis())
                                    peer.enqueue(upgrade(listOf(original, next)))
                                    assertTrue(requireNotNull(stream.get()).close(1012, "Owned fixture reconnect"))
                                    val fresh = requireNotNull(fcm.takeRequest(10, TimeUnit.SECONDS))
                                    val freshData = JsonParser.parseString(requireNotNull(fresh.body).utf8())
                                        .asJsonObject.getAsJsonObject("message").getAsJsonObject("data")
                                    assertEquals(setOf("version", "wake"), freshData.keySet())
                                    val nextWake = freshData.get("wake").asString
                                    assertNotEquals(wake, nextWake)
                                    assertEquals(next.id, resolve(nextWake).get("signature").asString)
                                    assertEquals("A private message after reconnect", PrivateChat.recover(recipient,
                                        resolve(nextWake, "original").get("envelope").asString).message.text)
                                    assertEquals("Replayed originals must not create additional sends", 3, fcm.requestCount)
                                    val status = post("/v1/observer/status", JsonObject().apply {
                                        addProperty("version", 1); addProperty("backend", backend.id)
                                    })
                                    assertTrue("The reconnect must preserve the explicit stream coverage gap",
                                        status.getAsJsonArray("gaps").any { it.asJsonObject.get("reason").asString == "chat-stream-unavailable" })
                                }
                            }
                        }
                    }
                }
                failure.get()?.let { throw AssertionError("Owned incoming Chat failed", it) }
            }
        }
    }
}

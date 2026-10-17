package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.GroupChat
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
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
import java.nio.file.Files
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GatewayGroupChatDeliveryTest {
    @get:Rule val directory = TemporaryFolder()

    @Test(timeout = 30_000) fun aConsentingGroupInterestReceivesAnOpaqueWakeAndRetrievesTheSignedGroupOriginal() = groupJourney()

    @Test(timeout = 30_000) fun malformedStoredGroupInterestsReportUnavailableRatherThanInvalidRequest() =
        groupJourney(corruptStoredGroups = true)

    private fun groupJourney(corruptStoredGroups: Boolean = false) {
        // Published unsafe fixture identities. Never fund or use outside owned fixtures.
        val recipient = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "9")
        val serverWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val origin = "https://push.fixture.invalid"
        val groupName = "An explicitly joined background group"
        val text = "An ordinary unencrypted group message"
        for (backend in SaitoBackend.entries) {
            val now = System.currentTimeMillis()
            val group = GroupChat.readCreation(GroupChat.create(author, groupKey.publicKey, groupName,
                listOf(recipient.publicKey), now - 1).envelope())
            val original = GroupChat.message(author, group, text, now)
            assertTrue(group.memberRoles.getValue(recipient.publicKey).active)
            assertFalse("Group delivery must not depend on a direct wallet recipient",
                recipient.publicKey in SaitoEnvelope.decode(original.envelope()).recipients)
            val identity = SaitoWallet.create()
            assertNotEquals(recipient.publicKey, identity.publicKey)
            val failure = AtomicReference<Throwable?>()
            MockWebServer().use { peer ->
                val counter = ByteArray(32) { 7 }
                peer.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            val frame = bytes.toByteArray()
                            when (frame[0].toInt()) {
                                1 -> webSocket.send(SaitoWire.handshakeReply(serverWallet,
                                    frame.copyOfRange(1, 33), counter).toByteString())
                                2 -> {
                                    require(SaitoWire.verifyHandshake(frame, counter) == identity.publicKey)
                                    webSocket.send(original.broadcast(author, identity.publicKey).toByteString())
                                }
                                20 -> webSocket.send(byteArrayOf(9).toByteString())
                                16 -> Unit
                                else -> error("The group observer must only authenticate and listen")
                            }
                        } catch (caught: Throwable) { failure.set(caught); webSocket.close(1008, "Owned group mismatch") }
                    }
                }).build())
                peer.start(InetAddress.getByName("127.0.0.1"), 0)
                MockWebServer().use { fcm ->
                    fcm.enqueue(MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/group\"}").build())
                    fcm.start(InetAddress.getByName("127.0.0.1"), 0)
                    FcmSender("owned-fixture", GoogleCredentials.create(AccessToken("synthetic-owned-oauth", Date(now + 3_600_000))),
                        "http://127.0.0.1:${fcm.port}").use { sender ->
                        val stateDirectory = directory.newFolder(backend.id).toPath()
                        PushGateway(stateDirectory, origin).use { gateway ->
                            gateway.start()
                            val connection = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()
                            fun post(path: String, body: JsonObject, signature: String? = null, status: Int = 200): JsonObject {
                                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}$path"))
                                    .timeout(java.time.Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                                    .header("X-Saito-Owner", recipient.publicKey)
                                signature?.let { request.header("X-Saito-Signature", it) }
                                val response = connection.send(request.build(), HttpResponse.BodyHandlers.ofString())
                                assertEquals("HTTP $path", status, response.statusCode())
                                return JsonParser.parseString(response.body()).asJsonObject
                            }
                            val nonce = post("/v1/challenges", JsonObject().apply {
                                addProperty("version", 1); addProperty("owner", recipient.publicKey)
                            }, status = 201).get("nonce").asString
                            fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                                .joinToString("") { "%02x".format(it.toInt() and 255) }
                            val subscription = "27".repeat(32)
                            val installation = UUID.randomUUID().toString()
                            val expiry = now + 86_400_000
                            val revocation = sha("29".repeat(32))
                            val fid = "owned-group-fid"
                            val categories = "group-chats"
                            val binding = JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", nonce); addProperty("subscription", subscription)
                                addProperty("installation", installation); addProperty("fid", fid); addProperty("owner", recipient.publicKey)
                                addProperty("backend", backend.id); addProperty("categories", categories)
                                addProperty("expiresAt", expiry); addProperty("revocationHash", revocation)
                                add("groups", JsonArray().apply { add(group.id) })
                            }
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$subscription\n$installation\n" +
                                "${sha(fid)}\n${recipient.publicKey}\n${backend.id}\n$categories\n$expiry\n$revocation\n${group.id}"
                            post("/v1/subscriptions/bind", binding, recipient.sign(intent.toByteArray()), 201)
                            GatewayChatObserver(gateway, identity, backend, sender, "http://127.0.0.1:${peer.port}").use { observer ->
                                observer.start()
                                val sent = fcm.takeRequest(5, TimeUnit.SECONDS)
                                failure.get()?.let { throw AssertionError("Owned incoming group failed", it) }
                                assertNotNull("A group interest must receive the ordinarily propagated message", sent)
                                val message = JsonParser.parseString(requireNotNull(sent?.body).utf8()).asJsonObject.getAsJsonObject("message")
                                assertEquals(setOf("fid", "data", "android"), message.keySet())
                                assertEquals(fid, message.get("fid").asString)
                                val data = message.getAsJsonObject("data")
                                assertEquals(setOf("version", "wake"), data.keySet())
                                assertEquals("1", data.get("version").asString)
                                val wake = data.get("wake").asString
                                assertTrue(wake.matches(Regex("[0-9a-f]{64}")))
                                fun resolve(operation: String, status: Int = 200) = post("/v1/wakes/$operation", JsonObject().apply {
                                    addProperty("version", 1); addProperty("wake", wake)
                                }, recipient.sign("Saito push $operation v1\n$origin/v1/wakes/$operation\n$wake".toByteArray()), status)
                                val resolved = resolve("resolve")
                                assertEquals(setOf("version", "wallet", "backend", "signature", "kind"), resolved.keySet())
                                assertEquals("group-chat", resolved.get("kind").asString)
                                assertEquals(recipient.publicKey, resolved.get("wallet").asString)
                                assertEquals(backend.id, resolved.get("backend").asString)
                                assertEquals(original.id, resolved.get("signature").asString)
                                val retained = resolve("original")
                                assertEquals(setOf("version", "envelope"), retained.keySet())
                                val received = GroupChat.readMessage(group, retained.get("envelope").asString)
                                assertEquals(original.id, received.id)
                                assertEquals(author.publicKey, received.author)
                                assertEquals(group.id, received.groupId)
                                assertEquals(text, received.text)
                                assertEquals(1, fcm.requestCount)
                                if (corruptStoredGroups) {
                                    // Owned disk-fault injection only; observe the public HTTP error contract.
                                    val stateFile = stateDirectory.resolve("subscriptions.v1.json")
                                    val state = JsonParser.parseString(Files.readString(stateFile)).asJsonObject
                                    state.getAsJsonObject("subscriptions").getAsJsonObject(subscription)
                                        .addProperty("groups", "unreadable-group-interests")
                                    Files.writeString(stateFile, state.toString())
                                    resolve("resolve", 503)
                                    resolve("original", 503)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

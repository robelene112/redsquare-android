package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceDestination
import nl.davidengelen.redsquare.spaces.SpacePublicReference
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Serves the frozen fixture and independently witnesses timed native writes. */
object SpacesScaleHost {
    @JvmStatic fun main(args: Array<String>) = runBlocking<Unit> {
        require(args.size == 1)
        val root = Path.of(args.single())
        val manifest = JsonParser.parseString(Files.readString(root.resolve("fixture.json"))).asJsonObject
        val ready = root.resolve("host-ready.json")
        check(!Files.exists(ready))
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val independent = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        check(manifest.get("authority").asString == service.publicKey)
        val nativeOwner = manifest.get("owner").asString
        val node = manifest.get("node").asString
        val unrelatedSpace = manifest.get("unrelatedSpace").asString
        val unrelatedChannel = manifest.get("unrelatedChannel").asString
        val expectedHotSpace = manifest.get("hotSpace").asString
        val expectedHotChannel = manifest.get("hotChannel").asString
        val clock = Clock.systemUTC()
        val reactions = AtomicInteger()
        val stopping = AtomicBoolean()
        SpacesServiceStore(root.resolve("service"), service, clock).use { remote ->
            SqliteClientFixture(root.resolve("independent-scale.sqlite").toString()).use { local ->
                val actor = SpaceClient(independent, node, service.publicKey, local, clock) { remote.exchange(it.envelope()) }
                actor.openPublic(unrelatedSpace)
                actor.joinPublic(unrelatedSpace)
                actor.flush(unrelatedSpace)
                actor.refresh(unrelatedSpace, unrelatedChannel)
                check(actor.community(unrelatedSpace, unrelatedChannel).canSend)
                val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;spaces-v1||;spaces-unread-v1||",
                    peer = service, archiveResponse = { "[]" })
                peer.spacesResponse = { request ->
                    val original = SaitoEnvelope.decode(request.envelope())
                    remote.exchange(request.envelope()).also {
                        val observed = SaitoEnvelope.decode(it)
                        if (original.author == nativeOwner && original.body.get("content_kind")?.asString == "message-reaction" &&
                            original.body.get("channel_id")?.asString == expectedHotChannel &&
                            observed.body.get("stream")?.asString == "content") reactions.incrementAndGet()
                    }
                }
                peer.start().use { socket ->
                    val control = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
                    var arrivals = 0
                    control.createContext("/") { request ->
                        try {
                            require(request.requestMethod == "POST")
                            request.requestBody.use { require(it.readNBytes(9).size <= 8) }
                            val result = when (request.requestURI.path) {
                                "/arrival" -> runBlocking {
                                    val body = "Independent benchmark arrival ${++arrivals}"
                                    val event = actor.send(unrelatedSpace, body, unrelatedChannel)
                                    actor.flush(unrelatedSpace)
                                    val row = requireNotNull(actor.message(unrelatedSpace, event, unrelatedChannel))
                                    check(row.position != null && !row.pending && !row.rejected)
                                    JsonObject().apply {
                                        addProperty("accepted", event)
                                        addProperty("channel", unrelatedChannel)
                                        addProperty("text", body)
                                    }
                                }
                                "/status" -> JsonObject().apply {
                                    addProperty("acceptedReactions", reactions.get())
                                    addProperty("readRequests", peer.spacesRequests.size)
                                    addProperty("independentArrivals", arrivals)
                                }
                                "/stop" -> JsonObject().apply { stopping.set(true) }
                                else -> error("Unknown owned scale action")
                            }
                            val bytes = result.toString().toByteArray()
                            request.responseHeaders.set("Content-Type", "application/json")
                            request.sendResponseHeaders(200, bytes.size.toLong())
                            request.responseBody.use { it.write(bytes) }
                        } catch (_: Exception) {
                            request.sendResponseHeaders(400, -1)
                        } finally { request.close() }
                    }
                    control.start()
                    try {
                        fun channelLink(space: String, channel: String) = SpaceDestination(
                            SpacePublicReference(node, service.publicKey, space), channel, SpaceDestination.Kind.CHANNEL
                        ).link()
                        val config = JsonObject().apply {
                            addProperty("peerPort", socket.port)
                            addProperty("controlPort", control.address.port)
                            addProperty("hotLink", channelLink(expectedHotSpace, expectedHotChannel))
                            addProperty("unrelatedLink", channelLink(unrelatedSpace, unrelatedChannel))
                            addProperty("libraryLink", channelLink(expectedHotSpace, manifest.get("hotLibrary").asString))
                        }
                        Files.writeString(ready, config.toString())
                        val deadline = System.nanoTime() + Duration.ofHours(2).toNanos()
                        while (!stopping.get()) {
                            check(System.nanoTime() < deadline) { "Owned scale host exceeded its bounded lifetime" }
                            delay(100)
                        }
                        peer.assertHealthy()
                    } finally { control.stop(0) }
                }
            }
        }
    }
}

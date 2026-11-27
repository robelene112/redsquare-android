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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Serves the frozen fixture and independently witnesses timed native writes. */
object SpacesScaleHost {
    private data class Arrival(val id: String, val readRequest: String? = null)

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
        val reads = AtomicInteger()
        val watchedReply = AtomicReference<Arrival>()
        val scrollArrivalGate = AtomicReference<CountDownLatch?>()
        val stopping = AtomicBoolean()
        SpacesServiceStore(root.resolve("service"), service, clock).use { remote ->
            SqliteClientFixture(root.resolve("independent-scale.sqlite").toString()).use { local ->
                val actor = SpaceClient(independent, node, service.publicKey, local, clock) { remote.exchange(it.envelope()) }
                actor.openPublic(unrelatedSpace)
                actor.joinPublic(unrelatedSpace)
                actor.flush(unrelatedSpace)
                actor.refresh(unrelatedSpace, unrelatedChannel)
                check(actor.community(unrelatedSpace, unrelatedChannel).canSend)
                val hotRoot = if (manifest.get("qualifiesForTimedRuns").asBoolean) {
                    actor.openPublic(expectedHotSpace)
                    actor.joinPublic(expectedHotSpace)
                    actor.flush(expectedHotSpace)
                    actor.refresh(expectedHotSpace, expectedHotChannel)
                    val record = manifest.get("hotChannelMessages").asInt
                    actor.timeline(expectedHotSpace, channel = expectedHotChannel).single {
                        it.text?.startsWith("General • Benchmark Space 001 • record $record.") == true && it.position != null && !it.pending
                    }
                } else null
                val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;spaces-v1||;spaces-unread-v1||",
                    peer = service, archiveResponse = { "[]" })
                peer.spacesResponse = { request ->
                    val original = SaitoEnvelope.decode(request.envelope())
                    remote.exchange(request.envelope()).also {
                        val observed = SaitoEnvelope.decode(it)
                        if (original.author == nativeOwner && original.body.get("content_kind")?.asString == "message-reaction" &&
                            original.body.get("channel_id")?.asString == expectedHotChannel &&
                            observed.body.get("stream")?.asString == "content") reactions.incrementAndGet()
                        if (original.author == nativeOwner && original.body.get("request")?.asString == "read") {
                            reads.incrementAndGet()
                            val scope = original.body.getAsJsonObject("scope")
                            val arrival = watchedReply.get()
                            if (arrival != null && arrival.readRequest == null && scope?.get("stream")?.asString == "content" &&
                                scope.get("channel_id")?.asString == expectedHotChannel &&
                                observed.body.getAsJsonArray("items")?.any { item ->
                                    val receipt = SaitoEnvelope.decode(item.asString)
                                    SaitoEnvelope.decode(receipt.body.get("event").asString).id == arrival.id
                                } == true && watchedReply.compareAndSet(arrival, arrival.copy(readRequest = original.id))) {
                                scrollArrivalGate.get()?.let { check(it.await(8, TimeUnit.SECONDS)) }
                            }
                        }
                    }
                }
                peer.start().use { socket ->
                    val control = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
                    var arrivals = 0
                    var crossing: String? = null
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
                                    val reply = hotRoot?.let { parent ->
                                        val id = actor.reply(expectedHotSpace, parent, "Independent offscreen thread activity $arrivals", expectedHotChannel)
                                        watchedReply.set(Arrival(id))
                                        actor.flush(expectedHotSpace)
                                        check(actor.thread(expectedHotSpace, parent, channel = expectedHotChannel).any {
                                            it.id == id && it.position != null && !it.pending && !it.rejected
                                        })
                                        id
                                    }
                                    JsonObject().apply {
                                        addProperty("accepted", event)
                                        addProperty("channel", unrelatedChannel)
                                        addProperty("text", body)
                                        reply?.let { addProperty("offscreenReply", it) }
                                    }
                                }
                                "/prepare-scroll-arrival" -> runBlocking {
                                    check(crossing == null)
                                    check(scrollArrivalGate.compareAndSet(null, CountDownLatch(1)))
                                    val parent = requireNotNull(hotRoot)
                                    val id = actor.reply(expectedHotSpace, parent, "Independent reply during scroll", expectedHotChannel)
                                    watchedReply.set(Arrival(id))
                                    crossing = id
                                    JsonObject().apply { addProperty("offscreenReply", id) }
                                }
                                "/commit-scroll-arrival" -> runBlocking {
                                    val id = requireNotNull(crossing)
                                    val parent = requireNotNull(hotRoot)
                                    actor.flush(expectedHotSpace)
                                    check(actor.thread(expectedHotSpace, parent, channel = expectedHotChannel).any {
                                        it.id == id && it.position != null && !it.pending && !it.rejected
                                    })
                                    crossing = null
                                    JsonObject().apply { addProperty("offscreenReply", id) }
                                }
                                "/release-scroll-arrival" -> {
                                    requireNotNull(scrollArrivalGate.getAndSet(null)).countDown()
                                    JsonObject().apply { addProperty("released", true) }
                                }
                                "/status" -> JsonObject().apply {
                                    addProperty("acceptedReactions", reactions.get())
                                    addProperty("readRequests", reads.get())
                                    addProperty("independentArrivals", arrivals)
                                    watchedReply.get()?.readRequest?.let { addProperty("offscreenReplyRead", it) }
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
                            hotRoot?.let { message ->
                                val reference = SpacePublicReference(node, service.publicKey, expectedHotSpace)
                                addProperty("hotMessageLink", SpaceDestination(
                                    reference, expectedHotChannel, SpaceDestination.Kind.MESSAGE, message.id
                                ).link())
                            }
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

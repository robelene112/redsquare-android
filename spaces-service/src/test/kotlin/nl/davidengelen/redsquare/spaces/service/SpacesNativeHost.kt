package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Disposable host peer survives an Android process kill. Public unsafe keys,
 * real service/SQLite/MLS and independent received effects; no public network. */
object SpacesNativeHost {
    @JvmStatic fun main(args: Array<String>): Unit = runBlocking {
        require(args.size == 1)
        val root = Path.of(args.single())
        require(Files.isDirectory(root) && !Files.exists(root.resolve("ready.json")))
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val clock = Clock.systemUTC()
        val stopping = AtomicBoolean()
        val lock = Mutex()
        val workers = Executors.newFixedThreadPool(2)
        val started = System.nanoTime()
        try { SpacesServiceStore(root.resolve("service"), service, clock).use { remote ->
            SqliteClientFixture(root.resolve("independent-client.sqlite").toString()).use { local ->
                val client = SpaceClient(guest, "https://saito.davidengelen.nl", service.publicKey, local, clock) { remote.exchange(it.envelope()) }
                val sent = mutableSetOf<String>()
                val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;spaces-v1||", peer = service,
                    archiveResponse = { "[]" })
                peer.spacesResponse = { remote.exchange(it.envelope()) }
                peer.start().use { socket ->
                    val control = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
                    control.executor = workers
                    control.createContext("/") { request ->
                        try {
                            require(request.requestMethod == "POST")
                            val input = request.requestBody.use { it.readNBytes(32_769) }.also { require(it.size <= 32_768) }
                            val body = JsonParser.parseString(input.toString(Charsets.UTF_8)).asJsonObject
                            val result = runBlocking { lock.withLock {
                                when (request.requestURI.path) {
                                    "/join" -> {
                                        val link = body.get("link").asString
                                        val preview = client.preview(link)
                                        check(client.communities().none { it.id == preview.spaceId })
                                        val space = client.join(link)
                                        client.flush(space)
                                        JsonObject().apply { addProperty("space", space); addProperty("previewDidNotJoin", true) }
                                    }
                                    "/invitation" -> {
                                        val space = client.create("Builders on another device")
                                        client.flush(space)
                                        val link = client.invite(space)
                                        client.flush(space)
                                        JsonObject().apply { addProperty("link", link); addProperty("space", space) }
                                    }
                                    "/populate" -> {
                                        val space = body.get("space").asString
                                        check(client.community(space).joined)
                                        val from = body.get("from")?.asInt ?: 0
                                        val count = body.get("count")?.asInt ?: 20
                                        require(from in 0..100 && count in 1..60)
                                        repeat(count) { client.send(space, "Fixture line ${from + it}") }
                                        client.flush(space); client.flush(space)
                                        JsonObject()
                                    }
                                    "/hold" -> { peer.withholdSpacesReply = true; JsonObject() }
                                    "/release" -> { peer.withholdSpacesReply = false; JsonObject() }
                                    "/stop" -> { stopping.set(true); JsonObject() }
                                    "/status" -> status(client, peer)
                                    else -> error("Unknown owned fixture command")
                                }
                            } }
                            val bytes = result.toString().toByteArray(Charsets.UTF_8)
                            request.sendResponseHeaders(200, bytes.size.toLong())
                            request.responseBody.use { it.write(bytes) }
                        } catch (_: Exception) {
                            val bytes = "{\"fixture_error\":true}".toByteArray()
                            request.sendResponseHeaders(409, bytes.size.toLong()); request.responseBody.use { it.write(bytes) }
                        } finally { request.close() }
                    }
                    control.start()
                    try {
                        Files.writeString(root.resolve("ready.json"), JsonObject().apply {
                            addProperty("peerPort", socket.port); addProperty("controlPort", control.address.port)
                            addProperty("service", service.publicKey); addProperty("guest", guest.publicKey)
                        }.toString())
                        while (!stopping.get()) {
                            check(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(15)) { "Owned host lifetime exceeded" }
                            lock.withLock {
                                for (community in client.communities()) {
                                    client.flush(community.id)
                                    client.refresh(community.id)
                                    if (client.community(community.id).joined) {
                                        client.admitPending(community.id)
                                        client.flush(community.id)
                                        if (community.id !in sent) {
                                            client.send(community.id, "Reply from independent member")
                                            sent += community.id
                                        }
                                        client.flush(community.id)
                                    }
                                }
                            }
                            delay(200)
                        }
                        peer.assertHealthy()
                        Files.writeString(root.resolve("result.json"), lock.withLock { status(client, peer).toString() })
                    } finally { control.stop(0) }
                }
            }
        }
        } finally {
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun status(client: SpaceClient, peer: ControlledFeedPeer) = JsonObject().apply {
        add("communities", com.google.gson.JsonArray().apply {
            client.communities().forEach { community -> add(JsonObject().apply {
                addProperty("id", community.id); addProperty("joined", community.joined)
                add("received", com.google.gson.JsonArray().apply { client.messages(community.id).forEach(::add) })
            }) }
        })
        add("originals", com.google.gson.JsonArray().apply {
            peer.spacesRequests.filter { SaitoEnvelope.decode(it.envelope()).body.get("request").asString == "event" }
                .forEach { original -> add(JsonObject().apply {
                    addProperty("id", original.id)
                    addProperty("kind", SaitoEnvelope.decode(original.envelope()).body.get("kind").asString)
                }) }
        })
    }
}

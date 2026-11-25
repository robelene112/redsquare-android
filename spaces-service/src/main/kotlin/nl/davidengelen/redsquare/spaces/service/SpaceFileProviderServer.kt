package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceFileAccessCheck
import java.net.InetSocketAddress
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Independent loopback provider behind an explicitly configured HTTPS origin. */
class SpaceFileProviderServer(directory: Path, private val wallet: SaitoWallet, origin: String, private val authority: String,
    private val clock: Clock, requestedPort: Int, private val capacityBytes: Long, private val termMillis: Long,
    private val authorize: (String) -> String) : AutoCloseable {
    init {
        require(capacityBytes in 1..(1L shl 40))
        require(termMillis in 600_000L..31_536_000_000L)
    }

    private val store = SpaceFileProviderStore(directory, wallet, origin, authority, clock)
    private val server = try { HttpServer.create(InetSocketAddress("127.0.0.1", requestedPort), 16) }
        catch (failure: Throwable) {
            try { store.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    private val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(16))
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    val port: Int get() = server.address.port

    init {
        server.executor = executor
        server.createContext("/v1/spaces-storage") { exchange ->
            try {
                require(exchange.requestURI.rawQuery == null)
                val path = exchange.requestURI.rawPath.removePrefix("/v1/spaces-storage")
                when (exchange.requestMethod to path) {
                    "POST" to "/offer" -> {
                        val request = JsonParser.parseString(exchange.textBody()).asJsonObject
                        require(request.keySet() == setOf("space", "owner"))
                        val offer = store.offer(request.get("space").asString, request.get("owner").asString,
                            capacityBytes, Math.addExact(clock.millis(), termMillis))
                        exchange.reply(200, offer.envelope)
                    }
                    "POST" to "/lease" -> exchange.reply(200, store.commit(exchange.textBody()).envelope)
                    "GET" to "/status" -> exchange.reply(200, store.usage(exchange.authorization()))
                    "DELETE" to "/object" -> {
                        store.remove(exchange.authorization())
                        exchange.reply(200, "{\"state\":\"removed\"}")
                    }
                    "POST" to "/upload" -> {
                        val authorization = exchange.authorization()
                        val grant = SpaceFileGrant.read(authorization, authority, wallet.publicKey)
                        require(grant.action == SpaceFileGrant.Action.PUT)
                        require(exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull() == grant.bytes)
                        requireLiveAccess(grant)
                        val receipt = exchange.requestBody.use { store.put(authorization, it) { requireLiveAccess(grant) } }
                        exchange.reply(200, receipt)
                    }
                    "GET" to "/download" -> {
                        val authorization = exchange.authorization()
                        val grant = SpaceFileGrant.read(authorization, authority, wallet.publicKey)
                        require(grant.action == SpaceFileGrant.Action.GET)
                        requireLiveAccess(grant)
                        store.open(authorization).use { ciphertext ->
                            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                            exchange.responseHeaders.set("Cache-Control", "no-store")
                            exchange.sendResponseHeaders(200, grant.bytes)
                            exchange.responseBody.use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var recheckAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                                var sinceCheck = 0
                                var sent = 0L
                                while (true) {
                                    val count = ciphertext.read(buffer)
                                    if (count < 0) break
                                    if (sent + count == grant.bytes || sinceCheck >= 1024 * 1024 || System.nanoTime() >= recheckAt) {
                                        requireLiveAccess(grant)
                                        sinceCheck = 0
                                        recheckAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                                    }
                                    output.write(buffer, 0, count)
                                    sent += count
                                    sinceCheck += count
                                }
                            }
                        }
                    }
                    else -> exchange.reply(404, "{}")
                }
            } catch (_: IllegalArgumentException) {
                if (exchange.responseCode < 0) exchange.reply(409, "{\"state\":\"not-accepted\"}")
            } catch (_: Exception) {
                if (exchange.responseCode < 0) exchange.reply(503, "{\"state\":\"unavailable\"}")
            } finally { exchange.close() }
        }
    }

    private fun requireLiveAccess(grant: SpaceFileGrant) {
        val original = SpaceFileAccessCheck.request(wallet, authority, grant, clock.millis()).envelope()
        val check = SpaceFileAccessCheck.read(original, authority, clock.millis())
        check.requireAllowed(authorize(original), authority, clock.millis())
    }

    fun start() {
        check(!closed.get() && started.compareAndSet(false, true))
        server.start()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            server.stop(0)
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Storage provider workers did not stop" }
        } finally { store.close() }
    }

    private fun HttpExchange.authorization(): String {
        val header = requireNotNull(requestHeaders.getFirst("Authorization"))
        require(header.startsWith("Saito ") && header.length in 7..8198)
        return header.removePrefix("Saito ")
    }

    private fun HttpExchange.textBody(): String = requestBody.use {
        val bytes = it.readNBytes(64 * 1024 + 1)
        require(bytes.size in 1..64 * 1024)
        bytes.decodeToString(throwOnInvalidSequence = true)
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        responseHeaders.set("Cache-Control", "no-store")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

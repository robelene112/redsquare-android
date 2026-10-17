package nl.davidengelen.redsquare.gateway

import com.sun.net.httpserver.HttpServer
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Isolated gateway runtime. Production hosting is a separate operation. */
class PushGateway(stateDirectory: Path, publicOrigin: String, port: Int = 0, clock: java.time.Clock = java.time.Clock.systemUTC()) : Closeable {
    private val subscriptions = GatewaySubscriptions(stateDirectory, publicOrigin, clock)
    private val server = try { HttpServer.create(InetSocketAddress("127.0.0.1", port), 16) }
        catch (failure: Exception) { subscriptions.close(); throw failure }
    private val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32))
    private val started = java.util.concurrent.atomic.AtomicBoolean()
    private val maintenance = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "gateway-retention").apply { isDaemon = true }
    }
    val port: Int get() = server.address.port

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            val reply = try {
                if (exchange.requestMethod != "POST") throw GatewayProblem(405)
                val uri = URI(exchange.requestURI.toString())
                if (uri.rawQuery != null || uri.rawPath != uri.path) throw GatewayProblem(404)
                val bytes = exchange.requestBody.use { it.readNBytes(8193) }
                if (bytes.size !in 1..8192) throw GatewayProblem(400)
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                val body = requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create()
                    .fromJson(text, JsonObject::class.java))
                subscriptions.handle(uri.path, body, exchange.requestHeaders.getFirst("X-Saito-Signature"),
                    exchange.requestHeaders.getFirst("X-Saito-Owner"))
            } catch (failure: GatewayProblem) { GatewayReply(failure.status, JsonObject()) }
            catch (_: JsonParseException) { GatewayReply(400, JsonObject()) }
            catch (_: java.nio.charset.CharacterCodingException) { GatewayReply(400, JsonObject()) }
            catch (_: IllegalArgumentException) { GatewayReply(400, JsonObject()) }
            catch (_: Exception) { GatewayReply(503, JsonObject()) }
            try {
                val bytes = reply.body.toString().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Cache-Control", "no-store")
                exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally { exchange.close() }
        }
    }

    /** One bounded read/queue/delivery pass for this explicitly selected backend. */
    @Synchronized fun poll(archive: GatewayArchive, sender: FcmSender) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val candidates = try { archive.verifiedSocial() } catch (failure: Exception) {
            unavailable(archive.backend, "archive-unavailable", failure)
        }
        subscriptions.enqueue(archive.backend, candidates)
        deliverPending(archive.backend, sender)
    }

    /** Process one selected chain range before advancing its durable cursor. */
    @Synchronized fun observe(chain: GatewayChain, archive: GatewayArchive, sender: FcmSender): Boolean {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        require(chain.backend == archive.backend)
        val backend = chain.backend
        var cursor = subscriptions.cursor(backend)
        val page = try { chain.range(cursor) } catch (failure: Exception) {
            unavailable(backend, "chain-unavailable", failure)
        }
        if (cursor != null && (page.ancestorHeight != cursor.height || page.ancestorHash != cursor.hash)) {
            cursor = subscriptions.rewind(backend, page.ancestorHeight, page.ancestorHash, page.tip.forkId)
            if (cursor == null) { subscriptions.restartObservation(backend, "chain-ancestry-unavailable"); return false }
        }
        if (page.references.isEmpty() && (cursor == null || page.tip.height != cursor.height || page.tip.hash != cursor.hash)) {
            subscriptions.restartObservation(backend, "chain-history-unavailable"); return false
        }
        var references = page.references
        if (cursor == null) {
            val gap = references.zipWithNext().indexOfLast { (first, next) -> next.height != first.height + java.math.BigInteger.ONE }
            if (gap >= 0) {
                subscriptions.gap(backend, "chain-history-unavailable")
                references = references.drop(gap + 1) // Only the latest available consecutive suffix can be verified end to end.
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        for (reference in references.take(8)) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (System.nanoTime() >= deadline) break
            if (cursor != null && reference.height != cursor.height + java.math.BigInteger.ONE) {
                subscriptions.restartObservation(backend, "chain-history-unavailable"); return false
            }
            val block = try { chain.block(reference) } catch (failure: Exception) {
                unavailable(backend, "full-block-unavailable", failure)
            }
            if (cursor != null && block.previousHash != cursor.hash) {
                subscriptions.restartObservation(backend, "chain-ancestry-unavailable"); return false
            }
            if (cursor == null && block.height > java.math.BigInteger.ONE) subscriptions.gap(backend, "bootstrap-history-unavailable")
            val signatures = block.applicationMessages().filter { message ->
                message.body.get("module")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "RedSquare" } == true &&
                    message.body.get("request")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "create tweet" } == true
            }.map { it.id }
            val candidates = try { archive.verifiedSocial(signatures) } catch (failure: Exception) {
                unavailable(backend, "archive-unavailable", failure)
            }
            if (candidates.size != signatures.distinct().size) subscriptions.gap(backend, "archive-candidate-unavailable")
            subscriptions.processed(backend, block, page.tip.forkId, candidates)
            cursor = GatewayChain.Cursor(block.height, block.hash, page.tip.forkId)
        }
        deliverPending(backend, sender)
        return true
    }

    @Synchronized fun chatCandidate(backend: nl.davidengelen.redsquare.saito.SaitoBackend,
        wire: ByteArray?, sender: FcmSender) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (wire != null) {
            subscriptions.enqueuePrivate(backend, wire)
            subscriptions.enqueueGroup(backend, wire)
        }
        deliverPending(backend, sender)
    }

    @Synchronized fun chatStreamGap(backend: nl.davidengelen.redsquare.saito.SaitoBackend, reason: String) =
        subscriptions.gap(backend, reason)

    private fun deliverPending(backend: nl.davidengelen.redsquare.saito.SaitoBackend, sender: FcmSender) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        for (queued in subscriptions.pending(backend).take(32)) {
            if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) break
            val delivery = subscriptions.current(queued) ?: continue
            subscriptions.complete(delivery, sender.send(delivery.fid, delivery.wake, delivery.expiresAt))
        }
    }

    private fun unavailable(backend: nl.davidengelen.redsquare.saito.SaitoBackend, reason: String, failure: Exception): Nothing {
        try { subscriptions.gap(backend, reason) } catch (storage: Exception) { failure.addSuppressed(storage) }
        throw failure
    }

    fun start() {
        check(started.compareAndSet(false, true))
        server.start()
        maintenance.scheduleWithFixedDelay({
            try { subscriptions.cleanup() }
            catch (_: Exception) { System.err.println("gateway retention: cleanup unavailable, retry scheduled") }
        }, 0, 60, TimeUnit.SECONDS)
    }
    override fun close() = finishCleanup(
        { maintenance.shutdownNow() },
        { check(maintenance.awaitTermination(5, TimeUnit.SECONDS)) { "Gateway cleanup still active" } },
        { server.stop(1) },
        { executor.shutdownNow() },
        { check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Gateway handlers still active" } },
        { subscriptions.close() }
    )
}

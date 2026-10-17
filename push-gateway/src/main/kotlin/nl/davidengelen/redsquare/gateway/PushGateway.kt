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
class PushGateway(stateDirectory: Path, publicOrigin: String, port: Int = 0) : Closeable {
    private val subscriptions = GatewaySubscriptions(stateDirectory, publicOrigin)
    private val server = try { HttpServer.create(InetSocketAddress("127.0.0.1", port), 16) }
        catch (failure: Exception) { subscriptions.close(); throw failure }
    private val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32))
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
        subscriptions.enqueue(archive.backend, archive.verifiedSocial())
        deliverPending(archive.backend, sender)
    }

    /** Process one selected chain range before advancing its durable cursor. */
    @Synchronized fun observe(chain: GatewayChain, archive: GatewayArchive, sender: FcmSender) {
        require(chain.backend == archive.backend)
        val backend = chain.backend
        var cursor = subscriptions.cursor(backend)
        val page = try { chain.range(cursor) } catch (failure: Exception) {
            subscriptions.gap(backend, "chain-unavailable"); throw failure
        }
        if (cursor != null && (page.ancestorHeight != cursor.height || page.ancestorHash != cursor.hash)) {
            cursor = subscriptions.rewind(backend, page.ancestorHeight, page.ancestorHash, page.tip.forkId)
            if (cursor == null) { subscriptions.gap(backend, "chain-ancestry-unavailable"); return }
        }
        if (page.references.isEmpty() && (cursor == null || page.tip.height != cursor.height || page.tip.hash != cursor.hash)) {
            subscriptions.gap(backend, "chain-history-unavailable")
        }
        for (reference in page.references) {
            if (cursor != null && reference.height != cursor.height + java.math.BigInteger.ONE) {
                subscriptions.gap(backend, "chain-history-unavailable"); return
            }
            val block = try { chain.block(reference) } catch (failure: Exception) {
                subscriptions.gap(backend, "full-block-unavailable"); throw failure
            }
            if (cursor != null && block.previousHash != cursor.hash) {
                subscriptions.gap(backend, "chain-ancestry-unavailable"); return
            }
            if (cursor == null && block.height > java.math.BigInteger.ONE) subscriptions.gap(backend, "bootstrap-history-unavailable")
            val signatures = block.applicationMessages().filter { message ->
                message.body.get("module")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "RedSquare" } == true &&
                    message.body.get("request")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "create tweet" } == true
            }.map { it.id }
            val candidates = archive.verifiedSocial(signatures)
            if (candidates.size != signatures.distinct().size) subscriptions.gap(backend, "archive-candidate-unavailable")
            subscriptions.processed(backend, block, page.tip.forkId, candidates)
            cursor = GatewayChain.Cursor(block.height, block.hash, page.tip.forkId)
        }
        deliverPending(backend, sender)
    }

    private fun deliverPending(backend: nl.davidengelen.redsquare.saito.SaitoBackend, sender: FcmSender) {
        for (queued in subscriptions.pending(backend)) {
            val delivery = subscriptions.current(queued) ?: continue
            subscriptions.complete(delivery, sender.send(delivery.fid, delivery.wake, delivery.expiresAt))
        }
    }

    fun start() = server.start()
    override fun close() {
        server.stop(1)
        executor.shutdown()
        check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Gateway handlers still active" }
        subscriptions.close()
    }
}

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
class PushGateway(stateDirectory: Path, publicOrigin: String) : Closeable {
    private val subscriptions = GatewaySubscriptions(stateDirectory, publicOrigin)
    private val server = try { HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16) }
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
                subscriptions.handle(uri.path, body, exchange.requestHeaders.getFirst("X-Saito-Signature"))
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

    fun start() = server.start()
    override fun close() {
        server.stop(1)
        executor.shutdown()
        check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Gateway handlers still active" }
        subscriptions.close()
    }
}

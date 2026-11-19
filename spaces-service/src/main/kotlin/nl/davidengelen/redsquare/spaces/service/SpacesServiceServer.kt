package nl.davidengelen.redsquare.spaces.service

import nl.davidengelen.redsquare.saito.SaitoWallet
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Loopback adapter only. A live Saito node must opt in separately. */
class SpacesServiceServer(directory: Path, wallet: SaitoWallet, clock: Clock, requestedPort: Int = 0) : AutoCloseable {
    private val store = SpacesServiceStore(directory, wallet, clock)
    private val server = try { HttpServer.create(InetSocketAddress("127.0.0.1", requestedPort), 16) }
        catch (failure: Throwable) {
            try { store.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    private val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(32))
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    val port: Int get() = server.address.port

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            var status = 200
            val response = try {
                require(exchange.requestURI.rawQuery == null)
                when {
                    exchange.requestMethod == "GET" && exchange.requestURI.rawPath == "/v1/readiness" -> store.readiness()
                    exchange.requestMethod == "POST" && exchange.requestURI.rawPath == "/v1/exchange" -> {
                        val bytes = exchange.requestBody.use { it.readNBytes(MAX_INPUT + 1) }
                        require(bytes.size in 1..MAX_INPUT)
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                        store.exchange(text)
                    }
                    else -> { status = 404; "{}" }
                }
            } catch (_: IllegalArgumentException) { status = 409; "{\"state\":\"not-accepted\"}" }
            catch (_: java.nio.charset.CharacterCodingException) { status = 400; "{}" }
            catch (_: Exception) { status = 503; "{\"state\":\"unavailable\"}" }
            try {
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Cache-Control", "no-store")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally { exchange.close() }
        }
    }

    fun start() {
        check(!closed.get() && started.compareAndSet(false, true))
        server.start()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null
        for (action in listOf<() -> Unit>({ server.stop(0) }, {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS)) { "Spaces request workers did not stop" }
        }, { store.close() })) try { action() } catch (cleanup: Throwable) {
            if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
        }
        failure?.let { throw it }
    }

    private companion object { const val MAX_INPUT = 8 * 1024 * 1024 }
}

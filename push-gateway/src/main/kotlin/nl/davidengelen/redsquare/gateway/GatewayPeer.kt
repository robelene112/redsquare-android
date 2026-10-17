package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoWire
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Shared authenticated transport; request identities remain local to one owned connection. */
internal class GatewayPeer(private val identity: SaitoWallet, val backend: SaitoBackend,
    localOrigin: String? = null, private val onTransaction: ((ByteArray) -> Unit)? = null) : Closeable {
    private val ready = CompletableFuture<Unit>()
    private val services = CompletableFuture<Set<String>>()
    private val disconnected = AtomicBoolean(false)
    private val chainReply = java.util.concurrent.atomic.AtomicReference<CompletableFuture<ByteArray>?>()
    private val nextIndex = AtomicInteger()
    private data class Pending(val response: CompletableFuture<ByteArray>, val registrarCallback: Boolean)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val challenges = mutableListOf(nonce())
    private var identifiedKey: String? = null
    private val socket = client.newWebSocket(Request.Builder().url(endpoint(backend, localOrigin)).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (disconnected.get()) { webSocket.cancel(); return }
                send(webSocket, byteArrayOf(1) + challenges.first())
                send(webSocket, byteArrayOf(20))
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    require(!disconnected.get() && bytes.size in 1..MAX_RESPONSE_BYTES)
                    val frame = bytes.toByteArray()
                    when (frame[0].toInt() and 0xff) {
                        1 -> {
                            require(frame.size == 33 && challenges.size < 8)
                            val counter = nonce()
                            challenges.add(counter)
                            send(webSocket, SaitoWire.handshakeReply(identity, frame.copyOfRange(1, 33), counter))
                        }
                        2 -> {
                            require(frame.size == 130)
                            val matched = challenges.firstOrNull { SaitoWire.verifyHandshake(frame, it) != null }
                                ?: throw IOException("Invalid peer handshake")
                            val key = requireNotNull(SaitoWire.verifyHandshake(frame, matched))
                            require(identifiedKey == null || identifiedKey == key)
                            identifiedKey = key
                            challenges.remove(matched)
                            val counter = frame.copyOfRange(98, 130)
                            if (counter.any { it != 0.toByte() }) {
                                send(webSocket, SaitoWire.handshakeReply(identity, counter, ByteArray(32)))
                            }
                            ready.complete(Unit)
                        }
                        4 -> if (ready.isDone && !ready.isCompletedExceptionally && frame.size > 1)
                            onTransaction?.invoke(frame.copyOfRange(1, frame.size))
                        7 -> send(webSocket, byteArrayOf(16))
                        20 -> send(webSocket, byteArrayOf(9))
                        9 -> {
                            require(frame.size <= 65_537)
                            val records = utf8(frame.copyOfRange(1, frame.size)).split(';').filter { it.isNotEmpty() }
                            require(records.size <= 256)
                            services.complete(records.map { record ->
                                val fields = record.split('|')
                                require(fields.size == 3 && fields[0].length in 1..128)
                                fields[0]
                            }.toSet())
                        }
                        22 -> {
                            require(frame.size in 197..6981 && ready.isDone && !ready.isCompletedExceptionally)
                            requireNotNull(chainReply.getAndSet(null)) { "Unsolicited chain response" }
                                .complete(frame.copyOfRange(1, frame.size))
                        }
                        13, 14 -> {
                            require(frame.size >= 5 && ready.isDone && !ready.isCompletedExceptionally)
                            val index = ByteBuffer.wrap(frame, 1, 4).int
                            val request = pending[index] ?: return
                            if (request.registrarCallback && frame[0] == 13.toByte() &&
                                frame.copyOfRange(5, frame.size).contentEquals(REGISTRY_NO_RESPONSE)) return
                            if (!pending.remove(index, request)) return
                            if (frame[0] == 14.toByte()) request.response.completeExceptionally(IOException("Peer request rejected"))
                            else request.response.complete(frame.copyOfRange(5, frame.size))
                        }
                    }
                } catch (_: Exception) {
                    fail(); webSocket.cancel()
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) { fail(); webSocket.cancel() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { fail() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail(); webSocket.close(code, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail() }
        })

    internal val isDisconnected: Boolean get() = disconnected.get()
    internal fun awaitAuthentication() = await(ready, System.nanoTime() + TimeUnit.SECONDS.toNanos(12))

    fun request(message: String, service: String, registrarCallback: Boolean = false): String {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        await(ready, deadline)
        if (service !in await(services, deadline)) throw IOException("Selected peer does not advertise the required service")
        val index = nextIndex.incrementAndGet()
        if (index <= 0) throw IOException("Peer request identifiers exhausted")
        val response = CompletableFuture<ByteArray>()
        pending[index] = Pending(response, registrarCallback)
        try {
            if (disconnected.get()) throw IOException("Peer disconnected")
            send(socket, SaitoWire.applicationRequest(identity.publicKey, message.toByteArray(Charsets.UTF_8),
                index, System.currentTimeMillis()))
            return utf8(await(response, deadline))
        } catch (failure: IllegalArgumentException) {
            throw IOException("Invalid Archive response", failure)
        } finally { pending.remove(index) }
    }

    /** Chain replies have no request ID. Any uncertain outcome retires this connection. */
    fun chainRequest(frame: ByteArray): ByteArray {
        require(frame.size == 111 && frame[0] == 21.toByte())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        await(ready, deadline)
        val response = CompletableFuture<ByteArray>()
        if (!chainReply.compareAndSet(null, response)) throw IOException("Chain request already pending")
        try {
            if (disconnected.get()) throw IOException("Peer disconnected")
            send(socket, frame)
            return await(response, deadline)
        } catch (failure: Exception) {
            fail(); socket.cancel(); throw failure
        } finally { chainReply.compareAndSet(response, null) }
    }

    private fun send(socket: WebSocket, bytes: ByteArray) {
        if (!socket.send(bytes.toByteString())) { fail(); throw IOException("Peer disconnected") }
    }
    private fun fail() {
        disconnected.set(true)
        val failure = IOException("Peer disconnected")
        ready.completeExceptionally(failure)
        services.completeExceptionally(failure)
        chainReply.getAndSet(null)?.completeExceptionally(failure)
        pending.values.forEach { it.response.completeExceptionally(failure) }
        pending.clear()
    }
    override fun close() { fail(); socket.cancel() }

    companion object {
        private val REGISTRY_NO_RESPONSE = "{\"err\":\"no response\"}".toByteArray(Charsets.UTF_8)
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS).build()
        private fun nonce() = ByteArray(32).also(SecureRandom()::nextBytes)
        internal fun endpoint(backend: SaitoBackend, local: String?): String {
            if (local == null) return backend.peerUrl
            val uri = URI(local)
            require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath in setOf("", "/"))
            return "ws://127.0.0.1:${uri.port}/wsopen"
        }
        private fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        private fun <T> await(future: CompletableFuture<T>, deadline: Long): T = try {
            future.get(maxOf(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt(); throw IOException("Archive read interrupted", failure)
        } catch (failure: TimeoutException) {
            throw IOException("Archive read timed out", failure)
        } catch (failure: ExecutionException) {
            throw IOException("Archive read failed", failure.cause)
        }
    }
}

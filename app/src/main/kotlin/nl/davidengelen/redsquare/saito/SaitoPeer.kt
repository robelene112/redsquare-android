package nl.davidengelen.redsquare.saito

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import nl.davidengelen.redsquare.BuildConfig
import android.util.Log
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Independently disposable feature ownership of the wallet's authenticated node connection.
 * Pinned Saito routes callbacks by public key, so all features at an exact endpoint
 * must share both the physical socket and its request-index namespace.
 */
class SaitoPeer(private val wallet: SaitoWallet, private val endpoint: String,
    private val onIncoming: ((ByteArray) -> Boolean)? = null) : Closeable {
    class RequestError : IOException("Peer returned a request error")
    private val active = AtomicBoolean(true)
    private val released = CompletableDeferred<Unit>()
    private val leaseId = nextLease.incrementAndGet()
    private val acceptedIncomingIndex = AtomicInteger()
    private var lastWriteOffset = 0L // Guarded by poolLock.
    private val connection = synchronized(poolLock) {
        val key = Address(wallet.publicKey, endpoint)
        val existing = connections[key]
        if (existing != null && !existing.disconnected.get()) {
            existing.also { it.leases.add(this) }
        } else {
            Connection(wallet, key).also { shared ->
                shared.leases.add(this)
                connections[key] = shared
                try { shared.start() } catch (failure: Exception) {
                    connections.remove(key, shared)
                    throw failure
                }
            }
        }
    }

    private fun requireActive() {
        if (!active.get() || connection.disconnected.get()) throw IOException("Peer disconnected")
    }

    private suspend fun <T> await(value: CompletableDeferred<T>): T = select {
        released.onAwait { throw IOException("Peer disconnected") }
        value.onAwait { it }
    }

    suspend fun request(message: ByteArray, beforeSend: (() -> Unit)? = null): ByteArray = requestFrame(beforeSend) { index ->
        SaitoWire.applicationRequest(wallet.publicKey, message, index, System.currentTimeMillis())
    }

    /** A Registry namecheck can receive one dispatcher fallback before its actual callback. */
    internal suspend fun requestRegistryNamecheck(message: ByteArray): ByteArray = requestFrame(awaitRegistryCallback = true) { index ->
        SaitoWire.applicationRequest(wallet.publicKey, message, index, System.currentTimeMillis())
    }

    /** Build mutable routing metadata after its selection guard, under the same enqueue lock. */
    suspend fun request(message: () -> ByteArray, beforeSend: () -> Unit): ByteArray = requestFrame(beforeSend) { index ->
        SaitoWire.applicationRequest(wallet.publicKey, message(), index, System.currentTimeMillis())
    }

    /** Exact signed application transaction, never wrapped in an unsigned request. */
    suspend fun request(transaction: SaitoTransaction, beforeSend: () -> Unit): ByteArray {
        require(SaitoEnvelope.decode(transaction.envelope()).author == wallet.publicKey)
        return requestFrame(beforeSend, frame = transaction::applicationRequest)
    }

    private suspend fun requestFrame(beforeSend: (() -> Unit)? = null, awaitRegistryCallback: Boolean = false,
        frame: (Int) -> ByteArray): ByteArray = withTimeout(12_000) {
        authenticate()
        val index = connection.nextIndex.incrementAndGet()
        require(index > 0)
        val response = CompletableDeferred<ByteArray>()
        val request = Connection.PendingRequest(this@SaitoPeer, response, awaitRegistryCallback)
        try {
            val job = currentCoroutineContext().job
            // Authority mutations precede pool operations everywhere. Never call a feature
            // callback while holding poolLock: receivers may need SaitoLocalState.lock.
            synchronized(SaitoLocalState.lock) {
                job.ensureActive()
                beforeSend?.invoke()
                val bytes = frame(index)
                job.ensureActive()
                synchronized(poolLock) {
                    requireActive()
                    connection.pending[index] = request
                    lastWriteOffset = connection.send(connection.socket, bytes)
                }
            }
            await(response)
        } finally { connection.pending.remove(index, request) }
    }

    /** Local socket enqueue only. This protocol provides no per-post acknowledgement. */
    suspend fun submit(transaction: SaitoTransaction, beforeSend: (() -> Unit)? = null) = withTimeout(12_000) {
        authenticate()
        val job = currentCoroutineContext().job
        synchronized(SaitoLocalState.lock) {
            job.ensureActive()
            beforeSend?.invoke()
            val bytes = transaction.broadcast(wallet, requireNotNull(connection.identifiedKey))
            job.ensureActive()
            synchronized(poolLock) {
                requireActive()
                lastWriteOffset = connection.send(connection.socket, bytes)
            }
        }
    }

    /** Establish readiness before making a durable operation non-cancellable. */
    suspend fun authenticate() = withTimeout(12_000) {
        await(connection.ready)
        requireActive()
    }

    /** Service announcements belong to the same authenticated physical connection. */
    suspend fun services(): Set<String> = withTimeout(12_000) {
        authenticate()
        await(connection.advertised).also { requireActive() }
    }

    /** Suspend until this feature's ownership ends or its physical connection fails. */
    suspend fun awaitDisconnection() { released.await() }

    /** Drain local writes, never close another feature's live transport or claim a peer ACK.
     * A sole owner retains the original graceful-close/close-reply bound.
     */
    suspend fun finishBroadcast() = withTimeout(12_000) {
        var prefix = 0L
        val exclusive = synchronized(poolLock) {
            requireActive()
            prefix = lastWriteOffset
            if (connection.leases.size == 1) {
                connection.disconnected.set(true)
                connections.remove(connection.address, connection)
                if (!connection.socket.close(1000, null)) throw IOException("Peer disconnected")
                true
            } else false
        }
        if (exclusive) connection.closed.await()
        else {
            while (true) {
                requireActive()
                if (connection.hasWritten(prefix)) break
                delay(10)
            }
        }
    }

    override fun close() {
        synchronized(poolLock) {
            if (!active.getAndSet(false)) return
            released.complete(Unit)
            connection.leases.remove(this)
            connection.pending.entries.forEach { (index, request) ->
                if (request.owner === this && connection.pending.remove(index, request)) {
                    request.response.completeExceptionally(IOException("Peer disconnected"))
                }
            }
            if (connection.leases.isEmpty()) {
                connections.remove(connection.address, connection)
                connection.fail()
                connection.socket.cancel()
            }
        }
    }

    private data class Address(val owner: String, val endpoint: String)
    private class Connection(private val wallet: SaitoWallet, val address: Address) {
        val ready = CompletableDeferred<Unit>()
        val advertised = CompletableDeferred<Set<String>>()
        val closed = CompletableDeferred<Unit>()
        val disconnected = AtomicBoolean(false)
        val leases = java.util.concurrent.CopyOnWriteArrayList<SaitoPeer>()
        class PendingRequest(val owner: SaitoPeer, val response: CompletableDeferred<ByteArray>, val awaitRegistryCallback: Boolean)
        val pending = ConcurrentHashMap<Int, PendingRequest>()
        val nextIndex = AtomicInteger(0)
        private val challenges = mutableListOf(randomNonce())
        var identifiedKey: String? = null
        lateinit var socket: WebSocket
            private set
        fun start() { socket = client.newWebSocket(Request.Builder().url(address.endpoint).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                send(webSocket, byteArrayOf(1) + challenges.first())
                send(webSocket, byteArrayOf(20))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    require(bytes.size in 1..MAX_RESPONSE_BYTES)
                    val frame = bytes.toByteArray()
                    when (frame[0].toInt() and 0xff) {
                        1 -> {
                            require(frame.size == 33)
                            val counter = randomNonce()
                            challenges.add(counter)
                            send(webSocket, SaitoWire.handshakeReply(wallet, frame.copyOfRange(1, 33), counter))
                        }
                        2 -> {
                            require(frame.size == 130)
                            val matched = challenges.firstOrNull { SaitoWire.verifyHandshake(frame, it) != null }
                                ?: throw IOException("Unsolicited or invalid peer handshake")
                            challenges.remove(matched)
                            val peerKey = SaitoWire.verifyHandshake(frame, matched)!!
                            require(identifiedKey == null || identifiedKey == peerKey)
                            identifiedKey = peerKey
                            val counter = frame.copyOfRange(98, 130)
                            if (counter.any { it != 0.toByte() }) {
                                send(webSocket, SaitoWire.handshakeReply(wallet, counter, ByteArray(32)))
                            }
                            ready.complete(Unit)
                        }
                        7 -> send(webSocket, byteArrayOf(16))
                        20 -> send(webSocket, byteArrayOf(9)) // This reader advertises no server services.
                        9 -> {
                            require(frame.size <= 65_537)
                            val text = Charsets.UTF_8.newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(frame, 1, frame.size - 1)).toString()
                            val services = text.split(';').filter { it.isNotEmpty() }.map { record ->
                                val fields = record.split('|')
                                require(fields.size == 3 && fields[0].length in 1..128)
                                fields[0]
                            }
                            require(services.size <= 256)
                            advertised.complete(services.toSet())
                        }
                        12 -> {
                            require(frame.size >= 5)
                            if (!ready.isCompleted || ready.isCancelled || disconnected.get() || !hasIncomingReceiver()) return
                            val accepted = acceptIncoming(frame.copyOfRange(5, frame.size), ByteBuffer.wrap(frame, 1, 4).int)
                            val acknowledgement = if (accepted) "{\"err\":\"\",\"success\":1}" else
                                "{\"err\":\"Message not accepted\",\"success\":0}"
                            send(webSocket, byteArrayOf(if (accepted) 13.toByte() else 14.toByte()) +
                                frame.copyOfRange(1, 5) + acknowledgement.toByteArray(Charsets.UTF_8))
                        }
                        4 -> {
                            if (!ready.isCompleted || ready.isCancelled || disconnected.get() || !hasIncomingReceiver()) return
                            acceptIncoming(frame.copyOfRange(1, frame.size))
                        }
                        13, 14 -> {
                            require(frame.size >= 5)
                            if (!ready.isCompleted || ready.isCancelled) return
                            val index = ByteBuffer.wrap(frame, 1, 4).int
                            val request = pending[index] ?: return
                            // The Registry relay may send its dispatcher fallback before the registrar callback.
                            // Only this exact type13 body is provisional; type14 and all other responses are final.
                            if (request.awaitRegistryCallback && frame[0] == 13.toByte() &&
                                frame.size == REGISTRY_NO_RESPONSE.size + 5 &&
                                REGISTRY_NO_RESPONSE.indices.all { frame[it + 5] == REGISTRY_NO_RESPONSE[it] }) return
                            if (!pending.remove(index, request)) return
                            if (frame[0] == 14.toByte()) request.response.completeExceptionally(RequestError())
                            else request.response.complete(frame.copyOfRange(5, frame.size))
                        }
                    }
                } catch (failure: Exception) {
                    if (BuildConfig.DEBUG) Log.w("RedSquarePeer", "Rejected peer frame (${failure.javaClass.simpleName})")
                    fail()
                    webSocket.cancel()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) { fail(); webSocket.cancel() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (BuildConfig.DEBUG) Log.w("RedSquarePeer", "Connection failed (${t.javaClass.simpleName}, HTTP ${response?.code ?: 0})")
                fail()
                closed.completeExceptionally(IOException("Peer disconnected"))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail(); webSocket.close(code, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail(); closed.complete(Unit) }
        }) }


        private fun hasIncomingReceiver() = leases.any { it.active.get() && it.onIncoming != null } ||
            RealtimeIncoming.hasReceiver(wallet.publicKey, address.endpoint)

        private fun acceptIncoming(transaction: ByteArray, requestIndex: Int? = null): Boolean {
            try {
                RealtimeIncoming.receive(wallet.publicKey, address.endpoint, transaction)?.let { return it }
            } catch (_: Exception) { return false }
            // Ordinary Chat receivers also own independent UI/error observation. Retention is
            // idempotent, so notify all live owners but acknowledge the wire request only once.
            var accepted = false
            for (lease in leases) {
                if (!lease.active.get()) continue
                val retained = try { lease.onIncoming?.invoke(transaction) == true } catch (_: Exception) { false }
                if (retained && lease.active.get() && requestIndex != null) lease.acceptedIncomingIndex.set(requestIndex)
                accepted = retained || accepted
            }
            return accepted
        }

        // OkHttp5.4.0 queueSize counts untransmitted message bytes and decreases
        // after each FIFO message write. Serialize enqueue accounting with snapshots
        // so a feature can wait for its own prefix without waiting for later traffic.
        private val writeLock = Any()
        private var enqueuedBytes = 0L
        fun hasWritten(prefix: Long): Boolean = synchronized(writeLock) {
            enqueuedBytes - socket.queueSize() >= prefix
        }

        fun send(socket: WebSocket, bytes: ByteArray): Long {
            val end = synchronized(writeLock) {
                if (socket.send(bytes.toByteString())) {
                    enqueuedBytes = Math.addExact(enqueuedBytes, bytes.size.toLong())
                    enqueuedBytes
                } else null
            }
            // Never acquire poolLock under writeLock; enqueuers use the other order.
            if (end == null) { fail(); throw IOException("Peer disconnected") }
            return end
        }

        fun fail() {
            synchronized(poolLock) {
                disconnected.set(true)
                connections.remove(address, this)
                ready.completeExceptionally(IOException("Peer disconnected"))
                advertised.completeExceptionally(IOException("Peer disconnected"))
                pending.values.forEach { it.response.completeExceptionally(IOException("Peer disconnected")) }
                pending.clear()
                leases.forEach { it.released.complete(Unit) }
            }
        }
    }

    companion object {
        private val poolLock = Any()
        private val connections = mutableMapOf<Address, Connection>()
        private val nextLease = AtomicInteger()

        /** Ownership diagnostic for native resource-handover proofs, never a transport selector. */
        internal fun incomingLeaseIds(owner: String, endpoint: String): Set<Int> = synchronized(poolLock) {
            connections[Address(owner, endpoint)]?.leases.orEmpty()
                .filter { it.active.get() && it.onIncoming != null }.map { it.leaseId }.toSet()
        }

        /** Exact successful callback observation, distinguishing owners on one physical socket. */
        internal fun acceptedIncomingLeaseIds(owner: String, endpoint: String, requestIndex: Int): Set<Int> = synchronized(poolLock) {
            connections[Address(owner, endpoint)]?.leases.orEmpty()
                .filter { it.active.get() && it.onIncoming != null && it.acceptedIncomingIndex.get() == requestIndex }
                .map { it.leaseId }.toSet()
        }

        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private val REGISTRY_NO_RESPONSE = "{\"err\":\"no response\"}".toByteArray(Charsets.UTF_8)
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS).build()
        private fun randomNonce(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)
    }
}

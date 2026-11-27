package nl.davidengelen.redsquare.saito

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Independently disposable feature ownership of the wallet's authenticated node connection.
 * Pinned Saito routes callbacks by public key, so all features at an exact endpoint
 * must share both the physical socket and its request-index namespace.
 */
class SaitoPeer(private val wallet: SaitoWallet, private val endpoint: String,
    private val onIncoming: ((ByteArray) -> Boolean)? = null) : Closeable {
    class RequestError : IOException("Peer returned a request error")
    internal data class ArchiveHint(val id: Long, val createdAt: Long)
    internal class ResponseTooLarge(val archiveHint: ArchiveHint?) : IOException("Peer response exceeds the read bound")
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

    /** Only Archive readers may use untrusted routing hints from a rejected oversized callback. */
    internal suspend fun requestArchive(message: ByteArray): ByteArray = requestFrame(archiveRead = true) { index ->
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

    /** One explicitly prepared Vault upload. Other feature callback budgets stay unchanged. */
    suspend fun requestVaultUpload(plan: SaitoVaultMint, beforeSend: () -> Unit): ByteArray =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(plan.author == wallet.publicKey)
            val transaction = plan.uploadRequest(wallet, System.currentTimeMillis())
            requestFrame(beforeSend, timeoutMillis = 90_000, frame = transaction::applicationRequest)
        }

    private suspend fun requestFrame(beforeSend: (() -> Unit)? = null, awaitRegistryCallback: Boolean = false,
        timeoutMillis: Long = 12_000, archiveRead: Boolean = false, frame: (Int) -> ByteArray): ByteArray = withTimeout(timeoutMillis) {
        authenticate()
        val index = nextRequestIndex.updateAndGet { previous ->
            if (previous == Int.MAX_VALUE) throw IOException("Peer request identities exhausted")
            previous + 1
        }
        val response = CompletableDeferred<ByteArray>()
        val request = Connection.PendingRequest(this@SaitoPeer, response, awaitRegistryCallback, archiveRead)
        var cancelled = false
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
        } catch (failure: CancellationException) {
            cancelled = failure !is TimeoutCancellationException
            throw failure
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            throw (request.oversized ?: failure)
        } finally {
            if (connection.retirePending(index, request) && cancelled && BuildConfig.DEBUG) {
                connection.observeProofCallback(index) { it.copy(cancelled = true) }
            }
        }
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

    /** Verify the peer and write our proof before making a durable operation non-cancellable. */
    suspend fun authenticate() = withTimeout(12_000) {
        val requiredWriteOffset = await(connection.handshakeWriteTarget)
        // A policy check can close its only lease immediately after authentication.
        // Do not let that close cancel the still-queued signed response.
        while (!connection.hasWritten(requiredWriteOffset)) {
            requireActive()
            delay(10)
        }
        requireActive()
    }

    /** Store uses the key of the service-advertising authenticated connection. */
    suspend fun servicePublicKey(service: String): String {
        require(service in services())
        return requireNotNull(connection.identifiedKey).also { requireActive() }
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
                if (request.owner === this && connection.retirePending(index, request)) {
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
        val handshakeWriteTarget = CompletableDeferred<Long>()
        val advertised = CompletableDeferred<Set<String>>()
        val closed = CompletableDeferred<Unit>()
        val disconnected = AtomicBoolean(false)
        val leases = java.util.concurrent.CopyOnWriteArrayList<SaitoPeer>()
        class PendingRequest(val owner: SaitoPeer, val response: CompletableDeferred<ByteArray>,
            val awaitRegistryCallback: Boolean, val archiveRead: Boolean) {
            @Volatile var oversized: ResponseTooLarge? = null
        }
        val pending = ConcurrentHashMap<Int, PendingRequest>()
        private val retiredReplies = LinkedHashSet<Int>()
        /** Keep only bounded request identities, never retired response bodies. */
        fun retirePending(index: Int, request: PendingRequest): Boolean = synchronized(retiredReplies) {
            if (!pending.remove(index, request)) return@synchronized false
            retiredReplies.add(index)
            if (retiredReplies.size > 128) retiredReplies.iterator().run { next(); remove() }
            true
        }
        fun observeProofCallback(index: Int, observe: (ProofCallback) -> ProofCallback) {
            if (BuildConfig.DEBUG && address.endpoint.startsWith("ws://127.0.0.1:")) {
                ownedProofCallback.get()?.takeIf { it.index == index }?.let { watch ->
                    ownedProofCallback.compareAndSet(watch, observe(watch))
                }
            }
        }
        private val challenges = mutableListOf(randomNonce())
        private var handshakeWriteOffset = 0L
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
                    if (bytes.size >= 5 && (bytes[0] == 13.toByte() || bytes[0] == 14.toByte()) &&
                        ready.isCompleted && !ready.isCancelled) {
                        val index = ByteBuffer.wrap(bytes.substring(1, 5).toByteArray()).int
                        // A cancelled owner cannot use its late body. Discard that one
                        // known callback before it can interrupt another shared reader.
                        // Active and unknown responses retain the original receive bound.
                        if (synchronized(retiredReplies) { retiredReplies.remove(index) }) {
                            if (BuildConfig.DEBUG) observeProofCallback(index) { it.copy(discarded = true) }
                            return
                        }
                        if (bytes.size > MAX_RESPONSE_BYTES && bytes[0] == 13.toByte()) {
                            pending[index]?.takeIf { it.archiveRead }?.let { request ->
                                request.oversized = ResponseTooLarge(archiveHint(bytes))
                            }
                        }
                    }
                    require(bytes.size in 1..MAX_RESPONSE_BYTES)
                    val frame = bytes.toByteArray()
                    when (frame[0].toInt() and 0xff) {
                        1 -> {
                            require(frame.size == 33)
                            val counter = randomNonce()
                            challenges.add(counter)
                            handshakeWriteOffset = send(webSocket, SaitoWire.handshakeReply(wallet, frame.copyOfRange(1, 33), counter))
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
                                handshakeWriteOffset = send(webSocket, SaitoWire.handshakeReply(wallet, counter, ByteArray(32)))
                            }
                            handshakeWriteTarget.complete(handshakeWriteOffset)
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
                            enqueueIncoming(webSocket, frame)
                        }
                        4 -> {
                            if (!ready.isCompleted || ready.isCancelled || disconnected.get() || !hasIncomingReceiver()) return
                            enqueueIncoming(webSocket, frame)
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

        // Retention can perform signature verification and protected disk writes.
        // Keep it FIFO on an owned worker so callbacks and WebSocket heartbeats
        // remain readable. Positive acknowledgement still follows actual retention.
        private val incomingBytes = AtomicInteger()
        private val incomingDeliveries = ConcurrentHashMap<IncomingFrameIdentity, IncomingDelivery>()
        private class IncomingFrameIdentity(private val bytes: ByteArray) {
            private val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            override fun hashCode() = digest.contentHashCode()
            override fun equals(other: Any?) = other is IncomingFrameIdentity &&
                digest.contentEquals(other.digest) && bytes.contentEquals(other.bytes)
        }
        private val incomingWorker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue(32), { work -> Thread(work, "RedSquareIncoming").apply { isDaemon = true } })
            .apply { allowCoreThreadTimeOut(true) }

        private inner class IncomingDelivery(private val deliverySocket: WebSocket, private val frame: ByteArray,
            private val identity: IncomingFrameIdentity) : Runnable {
            private val owned = AtomicBoolean(true)
            fun release() {
                if (owned.getAndSet(false)) {
                    // Free the budget before admitting a late same-frame retry.
                    incomingBytes.addAndGet(-frame.size)
                    incomingDeliveries.remove(identity, this)
                }
            }
            override fun run() {
                try {
                    if (disconnected.get() || !hasIncomingReceiver()) return
                    if (frame[0] == 4.toByte()) {
                        acceptIncoming(frame.copyOfRange(1, frame.size))
                    } else {
                        val accepted = acceptIncoming(frame.copyOfRange(5, frame.size), ByteBuffer.wrap(frame, 1, 4).int)
                        val acknowledgement = if (accepted) "{\"err\":\"\",\"success\":1}" else
                            "{\"err\":\"Message not accepted\",\"success\":0}"
                        synchronized(poolLock) {
                            if (!disconnected.get()) send(deliverySocket,
                                byteArrayOf(if (accepted) 13.toByte() else 14.toByte()) +
                                    frame.copyOfRange(1, 5) + acknowledgement.toByteArray(Charsets.UTF_8))
                        }
                    }
                } catch (failure: Exception) {
                    if (BuildConfig.DEBUG) Log.w("RedSquarePeer", "Incoming delivery failed (${failure.javaClass.simpleName})")
                    fail()
                    deliverySocket.cancel()
                } finally { release() }
            }
        }

        private fun enqueueIncoming(deliverySocket: WebSocket, frame: ByteArray) {
            // Bound all retained original bytes, including the active delivery.
            // Overload retires this connection without acknowledging lost work.
            val identity = IncomingFrameIdentity(frame)
            val delivery = IncomingDelivery(deliverySocket, frame, identity)
            // An exact in-flight retransmission shares the original's eventual
            // response. Changed request indexes or payloads still dispatch.
            // Equality compares every byte; a digest alone never admits content.
            if (incomingDeliveries.putIfAbsent(identity, delivery) != null) return
            if (incomingBytes.addAndGet(frame.size) > MAX_RESPONSE_BYTES) {
                delivery.release()
                fail(); deliverySocket.cancel()
                return
            }
            try { incomingWorker.execute(delivery) }
            catch (_: RejectedExecutionException) {
                delivery.release()
                fail(); deliverySocket.cancel()
            }
        }

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

        // This prefix never enters a content decoder/cache. It selects an exact bounded
        // read only; the returned full envelope still passes the existing signature reader.
        private fun archiveHint(bytes: ByteString): ArchiveHint? = try {
            val prefix = bytes.substring(5, minOf(bytes.size, MAX_RESPONSE_BYTES)).utf8()
            com.google.gson.stream.JsonReader(java.io.StringReader(prefix)).use { reader ->
                reader.beginArray(); reader.beginObject()
                var id: Long? = null
                var timestamp: Long? = null
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = reader.nextLong()
                        "created_at" -> timestamp = reader.nextLong()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (id != null && id!! >= 0 && timestamp != null && timestamp!! >= 0) ArchiveHint(id!!, timestamp!!) else null
            }
        } catch (_: Exception) { null }

        fun fail() {
            synchronized(poolLock) {
                disconnected.set(true)
                incomingWorker.shutdownNow().forEach { (it as IncomingDelivery).release() }
                connections.remove(address, this)
                handshakeWriteTarget.completeExceptionally(IOException("Peer disconnected"))
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
        // Node callbacks address the latest socket by public key even after reconnect.
        // Never reuse an index during this process; a random seed also reduces, but
        // cannot eliminate, stale-callback collisions across separate process starts.
        private val nextRequestIndex = AtomicInteger(SecureRandom().nextInt(1 shl 30))
        private data class ProofCallback(val index: Int, val cancelled: Boolean = false, val discarded: Boolean = false)
        private val ownedProofCallback = java.util.concurrent.atomic.AtomicReference<ProofCallback?>(null)

        /** Observe the discard of one exact loopback reply whose waiter was cancelled. */
        internal fun watchOwnedProofCallback(index: Int) {
            check(BuildConfig.DEBUG && index > 0)
            ownedProofCallback.set(ProofCallback(index))
        }

        internal fun cancelledOwnedProofCallback(index: Int): Boolean =
            ownedProofCallback.get()?.let { it.index == index && it.cancelled } == true

        internal fun discardedOwnedProofCallback(index: Int): Boolean =
            ownedProofCallback.get()?.let { it.index == index && it.discarded } == true

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

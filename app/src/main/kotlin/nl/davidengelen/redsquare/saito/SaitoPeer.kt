package nl.davidengelen.redsquare.saito

import kotlinx.coroutines.CompletableDeferred
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

/** One authenticated peer session. Request identities never cross a connection. */
class SaitoPeer(private val wallet: SaitoWallet, endpoint: String) : Closeable {
    private val ready = CompletableDeferred<Unit>()
    private val disconnected = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<ByteArray>>()
    private val nextIndex = AtomicInteger(0)
    private val challenges = mutableListOf(randomNonce())
    private var identifiedKey: String? = null
    private val socket = client.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
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
                    13, 14 -> {
                        require(frame.size >= 5)
                        if (!ready.isCompleted || ready.isCancelled) return
                        val index = ByteBuffer.wrap(frame, 1, 4).int
                        val result = pending.remove(index) ?: return
                        if (frame[0] == 14.toByte()) result.completeExceptionally(IOException("Peer request failed"))
                        else result.complete(frame.copyOfRange(5, frame.size))
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
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail(); webSocket.close(code, null) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail() }
    })

    suspend fun request(message: ByteArray): ByteArray = requestFrame { index ->
        SaitoWire.applicationRequest(wallet.publicKey, message, index, System.currentTimeMillis())
    }

    /** Exact signed application transaction, never wrapped in an unsigned request. */
    suspend fun request(transaction: SaitoTransaction, beforeSend: () -> Unit): ByteArray {
        require(SaitoEnvelope.decode(transaction.envelope()).author == wallet.publicKey)
        return requestFrame(beforeSend, transaction::applicationRequest)
    }

    private suspend fun requestFrame(beforeSend: (() -> Unit)? = null, frame: (Int) -> ByteArray): ByteArray = withTimeout(12_000) {
        ready.await()
        if (disconnected.get()) throw IOException("Peer disconnected")
        val index = nextIndex.incrementAndGet()
        require(index > 0)
        val response = CompletableDeferred<ByteArray>()
        pending[index] = response
        try {
            if (disconnected.get()) throw IOException("Peer disconnected")
            val bytes = frame(index)
            val job = currentCoroutineContext().job
            if (beforeSend == null) send(socket, bytes)
            else synchronized(SaitoLocalState.lock) {
                job.ensureActive()
                beforeSend()
                job.ensureActive()
                send(socket, bytes)
            }
            response.await()
        } finally { pending.remove(index) }
    }

    /** Local socket enqueue only. This protocol provides no per-post acknowledgement. */
    suspend fun submit(transaction: SaitoTransaction) = withTimeout(12_000) {
        authenticate()
        send(socket, transaction.broadcast(wallet, requireNotNull(identifiedKey)))
    }

    /** Establish readiness before making a durable operation non-cancellable. */
    suspend fun authenticate() = withTimeout(12_000) {
        ready.await()
        if (disconnected.get()) throw IOException("Peer disconnected")
    }

    private fun send(socket: WebSocket, bytes: ByteArray) {
        if (!socket.send(bytes.toByteString())) { fail(); throw IOException("Peer disconnected") }
    }

    private fun fail() {
        disconnected.set(true)
        ready.completeExceptionally(IOException("Peer disconnected"))
        pending.values.forEach { it.completeExceptionally(IOException("Peer disconnected")) }
        pending.clear()
    }

    override fun close() { fail(); socket.cancel() }

    companion object {
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS).build()
        private fun randomNonce(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)
    }
}

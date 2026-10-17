package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoTransaction
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** An owned persistent peer observes ordinary propagation, never recipient-authenticated Relay fan-out. */
class GatewayChatObserver(private val gateway: PushGateway, private val identity: SaitoWallet,
    private val backend: SaitoBackend, private val sender: FcmSender, private val localOrigin: String? = null) : Closeable {
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val peer = AtomicReference<GatewayPeer?>()
    private val inbox = ArrayBlockingQueue<ByteArray>(64)
    private var queuedBytes = 0
    private val overflow = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gateway-private-${backend.id}").apply { isDaemon = true }
    }

    @Synchronized fun start() {
        check(!closed.get())
        if (started.compareAndSet(false, true)) executor.execute(::observe)
    }

    private fun receive(bytes: ByteArray) {
        synchronized(inbox) {
            if (!closed.get() && bytes.size <= 4 * SaitoTransaction.MAX_MESSAGE_BYTES + 200_000 &&
                queuedBytes + bytes.size <= 8 * 1024 * 1024 && inbox.offer(bytes)) queuedBytes += bytes.size
            else if (!closed.get()) overflow.set(true)
        }
    }

    private fun observe() {
        var pending: ByteArray? = null
        while (!closed.get() && !Thread.currentThread().isInterrupted) {
            try {
                GatewayPeer(identity, backend, localOrigin, ::receive).use { connection ->
                    peer.set(connection)
                    if (closed.get()) return
                    connection.awaitAuthentication()
                    while (!closed.get()) {
                        if (connection.isDisconnected) throw IOException("Chat stream unavailable")
                        if (overflow.getAndSet(false)) gateway.chatStreamGap(backend, "chat-stream-capacity")
                        if (pending == null) {
                            pending = inbox.poll(1, TimeUnit.SECONDS)
                            pending?.let { synchronized(inbox) { queuedBytes -= it.size } }
                        }
                        gateway.chatCandidate(backend, pending, sender)
                        pending = null
                    }
                }
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            catch (_: Exception) {
                if (!closed.get()) {
                    try { gateway.chatStreamGap(backend, "chat-stream-unavailable") } catch (_: Exception) { }
                    System.err.println("private observer ${backend.id}: unavailable, retry scheduled")
                    try { Thread.sleep(5_000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                }
            } finally { peer.set(null) }
        }
    }

    @Synchronized fun stop() {
        if (!closed.compareAndSet(false, true)) return
        finishCleanup({ executor.shutdownNow() }, { peer.get()?.close() })
    }
    override fun close() = finishCleanup({ stop() }, {
        check(executor.awaitTermination(25, TimeUnit.SECONDS)) { "Private observer still stopping" }
    })
}

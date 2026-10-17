package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Completion-based passes never overlap for this selected backend. Hosting is separate. */
class GatewayObserver(private val gateway: PushGateway, private val identity: SaitoWallet,
    private val backend: SaitoBackend, private val sender: FcmSender, private val intervalMillis: Long = 30_000,
    private val localChainOrigin: String? = null, private val localArchiveOrigin: String? = null) : Closeable {
    init { require(intervalMillis in 100..300_000) }
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val chain = AtomicReference<GatewayChain?>()
    private val archive = AtomicReference<GatewayArchive?>()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "gateway-observer-${backend.id}").apply { isDaemon = true }
    }

    @Synchronized fun start() {
        check(!closed.get())
        if (started.compareAndSet(false, true)) executor.scheduleWithFixedDelay(::pass, 0, intervalMillis, TimeUnit.MILLISECONDS)
    }

    private fun pass() {
        if (closed.get()) return
        try {
            GatewayChain(identity, backend, localChainOrigin).use { selectedChain ->
                chain.set(selectedChain)
                // Deployed chain and Archive requests use the same authenticated socket.
                // Distinct loopback origins preserve the existing split-service fixture only.
                val selectedArchive = if (localChainOrigin?.removeSuffix("/") == localArchiveOrigin?.removeSuffix("/"))
                    GatewayArchive(backend, selectedChain.peer)
                    else GatewayArchive(identity, backend, localArchiveOrigin)
                selectedArchive.use {
                    archive.set(selectedArchive)
                    if (closed.get()) return
                    val reconciled = gateway.observe(selectedChain, selectedArchive, sender)
                    if (reconciled && !closed.get() && !Thread.currentThread().isInterrupted) gateway.poll(selectedArchive, sender)
                }
            }
        } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        catch (_: Exception) {
            if (!closed.get()) System.err.println("observer ${backend.id}: pass unavailable, retry scheduled")
        } finally { archive.set(null); chain.set(null) }
    }

    /** Signal every backend before awaiting any, since gateway processing is serialized. */
    @Synchronized fun stop() {
        if (!closed.compareAndSet(false, true)) return
        finishCleanup({ executor.shutdownNow() }, { chain.get()?.close() }, { archive.get()?.close() })
    }

    override fun close() = finishCleanup({ stop() }, {
        check(executor.awaitTermination(25, TimeUnit.SECONDS)) { "Observer still stopping" }
    })
}

package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Local process only. HTTPS termination and supervised deployment are separate operations. */
object GatewayMain {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size in listOf(6, 9) && args[0] == "--state" && args[2] == "--origin" && args[4] == "--port" &&
            (args.size == 6 || (args[6] == "--observe" && args[7] == "--project"))) {
            "Usage: gateway --state DIRECTORY --origin HTTPS_ORIGIN --port PORT [--observe --project PROJECT_ID]"
        }
        val port = args[5].toInt().also { require(it in 0..65535) }
        val gateway = PushGateway(Path.of(args[1]), args[3], port)
        val observers = mutableListOf<GatewayObserver>()
        val chatObservers = mutableListOf<GatewayChatObserver>()
        var sender: FcmSender? = null
        val closed = AtomicBoolean()
        val stopped = CountDownLatch(1)
        fun close() {
            if (!closed.compareAndSet(false, true)) return
            val actions = observers.map { observer -> { observer.stop() } } + chatObservers.map { observer -> { observer.stop() } } +
                listOf({ sender?.close(); Unit }) + observers.map { observer -> { observer.close() } } +
                chatObservers.map { observer -> { observer.close() } } + listOf({ gateway.close() })
            finishCleanup(*actions.toTypedArray())
        }
        val hook = Thread({ try { close() } finally { stopped.countDown() } }, "gateway-shutdown")
        try {
            if (args.size == 9) {
                val configured = FcmSender.applicationDefault(args[8])
                sender = configured
                val identity = SaitoWallet.create() // Ephemeral gateway identity, never a subscriber's signing key.
                SaitoBackend.entries.forEach { observers.add(GatewayObserver(gateway, identity, it, configured)) }
                // Private streams must not compete with social or each other for a same-key socket mapping.
                SaitoBackend.entries.forEach { chatObservers.add(GatewayChatObserver(gateway, SaitoWallet.create(), it, configured)) }
            }
            Runtime.getRuntime().addShutdownHook(hook)
            gateway.start()
            observers.forEach(GatewayObserver::start)
            chatObservers.forEach(GatewayChatObserver::start)
            println("listening=127.0.0.1:${gateway.port}")
            stopped.await()
        } finally {
            close()
            try { Runtime.getRuntime().removeShutdownHook(hook) }
            catch (_: IllegalStateException) { /* JVM shutdown is already in progress. */ }
        }
    }
}

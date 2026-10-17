package nl.davidengelen.redsquare.gateway

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Local process only. HTTPS termination and supervised deployment are separate operations. */
object GatewayMain {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 6 && args[0] == "--state" && args[2] == "--origin" && args[4] == "--port") {
            "Usage: gateway --state DIRECTORY --origin HTTPS_ORIGIN --port PORT"
        }
        val port = args[5].toInt().also { require(it in 0..65535) }
        val gateway = PushGateway(Path.of(args[1]), args[3], port)
        val closed = AtomicBoolean()
        val stopped = CountDownLatch(1)
        fun close() { if (closed.compareAndSet(false, true)) gateway.close() }
        val hook = Thread({ try { close() } finally { stopped.countDown() } }, "gateway-shutdown")
        try {
            Runtime.getRuntime().addShutdownHook(hook)
            gateway.start()
            println("listening=127.0.0.1:${gateway.port}")
            stopped.await()
        } finally {
            close()
            try { Runtime.getRuntime().removeShutdownHook(hook) }
            catch (_: IllegalStateException) { /* JVM shutdown is already in progress. */ }
        }
    }
}

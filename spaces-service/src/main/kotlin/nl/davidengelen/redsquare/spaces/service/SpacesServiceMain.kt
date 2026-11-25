package nl.davidengelen.redsquare.spaces.service

import java.nio.file.Path
import java.time.Clock
import kotlin.system.exitProcess

object SpacesServiceMain {
    /** Explicit local configuration only. Never create/export a wallet on failure. */
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.isEmpty()) { "Use local configuration files rather than command-line credentials" }
            val directory = Path.of(requireNotNull(System.getenv("SPACES_STATE_DIR")))
            val wallet = readSpacesServiceIdentity(Path.of(requireNotNull(System.getenv("SPACES_IDENTITY_FILE"))))
            val port = requireNotNull(System.getenv("SPACES_LISTEN_PORT")).toInt().also { require(it in 0..65535) }
            val server = SpacesServiceServer(directory, wallet, Clock.systemUTC(), port)
            try {
                Runtime.getRuntime().addShutdownHook(Thread({
                    try { server.close() } catch (_: Exception) { System.err.println("Spaces shutdown needs local inspection") }
                }, "spaces-shutdown"))
                server.start()
                println("SPACES_READY ${server.port} ${wallet.publicKey}")
            } catch (failure: Throwable) {
                try { server.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        } catch (failure: Exception) {
            // Configuration, JSON and SQL exception values can contain local data.
            System.err.println("Spaces service could not start (${failure.javaClass.simpleName}); preserve state and inspect local configuration")
            exitProcess(1)
        }
    }
}

package nl.davidengelen.redsquare.spaces.service

import java.nio.file.Path
import java.time.Clock
import kotlin.system.exitProcess

/** Explicit provider operator entry point. SpacesServiceMain never launches this process. */
object SpaceFileProviderMain {
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.isEmpty())
            fun setting(name: String): String = requireNotNull(System.getenv("SPACES_FILE_PROVIDER_$name"))
            val wallet = readSpacesServiceIdentity(Path.of(setting("IDENTITY_FILE")))
            val server = SpaceFileProviderServer(Path.of(setting("STATE_DIR")), wallet, setting("ORIGIN"), setting("AUTHORITY"),
                Clock.systemUTC(), setting("LISTEN_PORT").toInt(), setting("CAPACITY_BYTES").toLong(), setting("TERM_MILLIS").toLong())
            try {
                Runtime.getRuntime().addShutdownHook(Thread({
                    try { server.close() } catch (_: Exception) {
                        System.err.println("Spaces storage shutdown needs local inspection")
                    }
                }, "spaces-file-provider-shutdown"))
                server.start()
                println("SPACES_FILE_PROVIDER_READY ${server.port} ${wallet.publicKey}")
            } catch (failure: Throwable) {
                try { server.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        } catch (failure: Exception) {
            System.err.println("Spaces storage could not start (${failure.javaClass.simpleName}); inspect local configuration")
            exitProcess(1)
        }
    }
}

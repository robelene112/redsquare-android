package nl.davidengelen.redsquare.spaces.service

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import kotlin.system.exitProcess

object SpacesServiceMain {
    /** Explicit local configuration only. Never create/export a wallet on failure. */
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.isEmpty()) { "Use local configuration files rather than command-line credentials" }
            val directory = Path.of(requireNotNull(System.getenv("SPACES_STATE_DIR")))
            val identity = Path.of(requireNotNull(System.getenv("SPACES_IDENTITY_FILE"))).toRealPath()
            val permissions = Files.getPosixFilePermissions(identity)
            require(permissions.none { it in setOf(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE) }) { "Spaces identity file must be owner-only" }
            require(Files.size(identity) in 1..4096)
            val bytes = Files.newInputStream(identity).use { it.readNBytes(4097) }
            val wallet = try {
                require(bytes.size in 1..4096)
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                val data = GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonObject::class.java)
                require(data.keySet() == setOf("version", "publicKey", "privateKey") && data.get("version").toString() == "1")
                val key = data.get("privateKey").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
                val restored = SaitoWallet.fromPrivateKey(key)
                require(data.get("publicKey").isJsonPrimitive && data.get("publicKey").asJsonPrimitive.isString &&
                    data.get("publicKey").asString == restored.publicKey)
                restored
            } finally { bytes.fill(0) }
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

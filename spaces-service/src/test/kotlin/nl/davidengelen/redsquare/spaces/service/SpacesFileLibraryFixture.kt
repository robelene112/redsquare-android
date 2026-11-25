package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileDescriptor
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceStorageProviderClient
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

internal class SpacesFileLibraryFixture(private val root: Path, private val authority: SaitoWallet,
    private val owner: SpaceClient, private val remote: SpacesServiceStore) : AutoCloseable {
    private val operator = SaitoWallet.fromPrivateKey("0".repeat(63) + "6")
    private val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "5")
    private val local = SqliteClientFixture(root.resolve("file-reader.sqlite").toString())
    private val clock = Clock.systemUTC()
    private val independent = SpaceClient(guest, "https://saito.davidengelen.nl", authority.publicKey, local, clock) {
        remote.exchange(it.envelope())
    }
    private val bridge = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 8)
    private val executor = Executors.newFixedThreadPool(3)
    private val accessAuthority = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
    private val accessWorkers = Executors.newFixedThreadPool(2)
    private val downloads = AtomicInteger()
    @Volatile private var providerPort = 0
    private var child: Process? = null
    private val identity = root.resolve("provider-identity.json")
    val port get() = bridge.address.port
    private val origin = "https://127.0.0.1:$port"
    private lateinit var space: String
    private lateinit var channel: String

    init {
        Files.createFile(identity, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.writeString(identity, JsonObject().apply {
            addProperty("version", 1)
            addProperty("publicKey", operator.publicKey)
            addProperty("privateKey", "0".repeat(63) + "6")
        }.toString())
        val keys = KeyStore.getInstance("PKCS12")
        val password = "owned-fixture".toCharArray()
        Files.newInputStream(root.resolve("files-fixture.p12")).use { keys.load(it, password) }
        val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
        val ssl = SSLContext.getInstance("TLS").apply { init(keyManager.keyManagers, null, null) }
        bridge.httpsConfigurator = HttpsConfigurator(ssl)
        bridge.executor = executor
        bridge.createContext("/v1/spaces-storage", ::relay)
        accessAuthority.executor = accessWorkers
        accessAuthority.createContext("/v1/file-permission") { exchange ->
            try {
                require(exchange.requestMethod == "POST" && exchange.requestURI.path == "/v1/file-permission")
                val source = exchange.requestBody.use { it.readNBytes(16 * 1024 + 1) }
                require(source.size in 1..16 * 1024)
                val reply = remote.checkFileAccess(source.decodeToString(throwOnInvalidSequence = true)).encodeToByteArray()
                exchange.sendResponseHeaders(200, reply.size.toLong())
                exchange.responseBody.use { it.write(reply) }
            } catch (_: Exception) {
                if (exchange.responseCode < 0) exchange.sendResponseHeaders(409, -1)
            } finally { exchange.close() }
        }
    }

    suspend fun seed() {
        bridge.start()
        accessAuthority.start()
        startProvider()
        space = owner.create(SpaceProfile("Files proof library", visibility = SpaceVisibility.PUBLIC),
            listOf(SpaceChannelProfile("Shared resources", SpaceChannelKind.FILE)))
        owner.flush(space); owner.refresh(space)
        channel = owner.channels(space).single().id
        independent.openPublic(space); independent.joinPublic(space); independent.flush(space); independent.refresh(space)
    }

    fun configuration() = JsonObject().apply {
        addProperty("space", space)
        addProperty("channel", channel)
        addProperty("origin", origin)
        addProperty("link", owner.publicLink(space))
    }

    fun downloadCount() = JsonObject().apply { addProperty("nativeDownloads", downloads.get()) }

    suspend fun received(): JsonObject {
        independent.refresh(space, channel)
        val entries = independent.timeline(space, channel = channel).filter { it.isFile && !it.pending && !it.deleted }
        val lease = requireNotNull(independent.fileStorage(space))
        val status = independent.fileStorageUsagePermission(space, channel)
        return JsonObject().apply {
            addProperty("used", provider().usage(lease, status, clock::millis).used)
            addProperty("files", entries.size)
            val observedDownloadCount = downloads.get()
            addProperty("nativeDownloads", observedDownloadCount)
            val accepted = entries.singleOrNull() ?: return@apply
            val file = SpaceFileDescriptor.read(requireNotNull(accepted.text))
            val permission = independent.fileDownloadPermission(space, channel, accepted.id)
            val received = ByteArrayOutputStream()
            provider().download(permission.grant) { source ->
                SpaceFileCipher.open(SpaceFileCipher.Scope(authority.publicKey, space, channel), file.sealed, source, received)
            }
            independent.confirmFileDownload(space, channel, accepted.id, permission)
            val data = received.toByteArray()
            addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
            addProperty("record", accepted.id)
            addProperty("fileName", file.name)
            addProperty("size", data.size)
            addProperty("lease", lease.id)
            addProperty("providerPid", requireNotNull(child).pid())
        }
    }

    fun stopProvider() {
        providerPort = 0
        child?.let {
            it.destroy()
            if (!it.waitFor(8, TimeUnit.SECONDS)) {
                it.destroyForcibly()
                check(it.waitFor(5, TimeUnit.SECONDS))
                error("Storage fixture needed forced shutdown")
            }
        }
        child = null
    }

    fun startProvider() {
        check(child == null)
        val output = Files.createTempFile(root, "file-provider-", ".log")
        val arguments = listOf(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("java.class.path"), SpaceFileProviderMain::class.java.name)
        val process = ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(output.toFile())
            .apply {
                mapOf("IDENTITY_FILE" to identity.toString(), "STATE_DIR" to root.resolve("file-storage").toString(),
                    "ORIGIN" to origin, "AUTHORITY" to authority.publicKey, "LISTEN_PORT" to "0",
                    "AUTHORITY_URL" to "http://127.0.0.1:${accessAuthority.address.port}",
                    "CAPACITY_BYTES" to "1048576", "TERM_MILLIS" to "600000").forEach { (key, value) ->
                    environment()["SPACES_FILE_PROVIDER_$key"] = value
                }
            }.start()
        child = process
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (providerPort == 0) {
            check(process.isAlive && System.nanoTime() < deadline) { "Storage fixture failed to start" }
            val ready = Files.readAllLines(output).firstOrNull { it.startsWith("SPACES_FILE_PROVIDER_READY ") }
            if (ready != null) providerPort = ready.split(' ')[1].toInt() else Thread.sleep(20)
        }
    }

    private fun provider() = SpaceStorageProviderClient(origin) { url ->
        URL("http://127.0.0.1:$providerPort${url.file}").openConnection() as HttpURLConnection
    }

    private fun relay(exchange: HttpExchange) {
        val port = providerPort
        try {
            if (port == 0) {
                exchange.sendResponseHeaders(503, -1)
                return
            }
            val path = exchange.requestURI.rawPath
            if (path == "/v1/spaces-storage/download") downloads.incrementAndGet()
            val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = exchange.requestMethod
                connection.connectTimeout = 3000
                connection.readTimeout = 5000
                exchange.requestHeaders.getFirst("Authorization")?.let { connection.setRequestProperty("Authorization", it) }
                exchange.requestHeaders.getFirst("Content-Type")?.let { connection.setRequestProperty("Content-Type", it) }
                val length = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
                if (length != null && length > 0) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(length)
                    exchange.requestBody.use { input -> connection.outputStream.use { input.copyTo(it) } }
                }
                val status = connection.responseCode
                val body = if (status < 400) connection.inputStream else connection.errorStream
                val responseLength = connection.getHeaderFieldLong("Content-Length", 0L)
                connection.getHeaderField("Content-Type")?.let { exchange.responseHeaders.set("Content-Type", it) }
                exchange.sendResponseHeaders(status, if (body == null) -1 else responseLength)
                body?.use { input -> exchange.responseBody.use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            if (exchange.responseCode < 0) exchange.sendResponseHeaders(503, -1)
        } finally { exchange.close() }
    }

    override fun close() {
        try { stopProvider() } finally {
            bridge.stop(0)
            accessAuthority.stop(0)
            executor.shutdownNow()
            accessWorkers.shutdownNow()
            local.close()
        }
    }
}

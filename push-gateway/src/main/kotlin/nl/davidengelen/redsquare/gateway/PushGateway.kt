package nl.davidengelen.redsquare.gateway

import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.file.Path

/** Isolated gateway runtime. Production hosting is a separate operation. */
class PushGateway(stateDirectory: Path, publicOrigin: String) : Closeable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 16)
    val port: Int get() = server.address.port

    init {
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(501, -1)
            exchange.close()
        }
    }

    fun start() = server.start()
    override fun close() = server.stop(0)
}

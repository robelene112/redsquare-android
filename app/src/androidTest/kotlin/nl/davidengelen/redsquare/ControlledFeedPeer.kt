package nl.davidengelen.redsquare

import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.SaitoWallet
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual binary socket boundary; signed posts are independent WASM fixtures. */
internal class ControlledFeedPeer(@Volatile var rows: String,
    private val onTransaction: ((ByteArray) -> Unit)? = null,
    private val quoteArchiveResponse: ((com.google.gson.JsonObject) -> String)? = null,
    private val profileArchiveResponse: ((com.google.gson.JsonObject) -> String)? = null,
    private val registryResponse: ((com.google.gson.JsonObject) -> String)? = null,
    private val reportResponse: ((ByteArray) -> String)? = null,
    private val advertisedServices: String? = null,
    private val archiveResponse: ((com.google.gson.JsonObject) -> String)? = null) {
    @Volatile var disconnectAfterTransaction = false
    @Volatile var disconnectAfterReport = false
    @Volatile var withholdReportReply = false
    @Volatile var reportReplyIsError = false
    private val peer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public test material only.
    private val counter = ByteArray(32) { 7 }
    private val received = CountDownLatch(1)
    val requests = java.util.concurrent.atomic.AtomicInteger()
    val servedChallenges = java.util.concurrent.atomic.AtomicInteger()
    val clientKeys = java.util.concurrent.CopyOnWriteArrayList<String>()
    val transactions = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    val reports = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    val registryQueries = java.util.concurrent.CopyOnWriteArrayList<com.google.gson.JsonObject>()
    @Volatile private var failure: Throwable? = null

    fun start(port: Int = 0): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = response()
        }
        server.start(java.net.InetAddress.getByName("127.0.0.1"), port)
        return server
    }

    private fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
        private var authenticated = false
        private var interrupted = false
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            try {
                val frame = bytes.toByteArray()
                when (frame[0].toInt()) {
                    20 -> advertisedServices?.let {
                        require(frame.size == 1)
                        webSocket.send((byteArrayOf(9) + it.toByteArray(Charsets.UTF_8)).toByteString())
                    }
                    1 -> {
                        require(frame.size == 33)
                        val key = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        val signature = peer.sign(frame.copyOfRange(1, 33)).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        if (webSocket.send((byteArrayOf(2) + key + signature + counter).toByteString())) {
                            servedChallenges.incrementAndGet()
                        }
                    }
                    2 -> {
                        require(frame.size == 130)
                        require(frame.copyOfRange(98, 130).all { it == 0.toByte() })
                        var keyNumber = java.math.BigInteger(1, frame.copyOfRange(1, 34))
                        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
                        var clientKey = ""
                        while (keyNumber.signum() > 0) {
                            val parts = keyNumber.divideAndRemainder(java.math.BigInteger.valueOf(58))
                            clientKey = alphabet[parts[1].toInt()] + clientKey
                            keyNumber = parts[0]
                        }
                        val answer = frame.copyOfRange(34, 98).joinToString("") { "%02x".format(it) }
                        require(SaitoWallet.verify(counter, answer, clientKey))
                        clientKeys.add(clientKey)
                        authenticated = true
                    }
                    4 -> {
                        require(authenticated)
                        transactions.add(frame)
                        onTransaction?.invoke(frame)
                        if (disconnectAfterTransaction) {
                            interrupted = true
                            webSocket.close(1001, "Controlled post interruption")
                        }
                    }
                    12 -> {
                        if (interrupted) return
                        require(authenticated)
                        val bodyLength = ByteBuffer.wrap(frame, 13, 4).int
                        val inputs = ByteBuffer.wrap(frame, 5, 4).int
                        val outputs = ByteBuffer.wrap(frame, 9, 4).int
                        val offset = 5 + 93 + 59 * (inputs + outputs)
                        val body = JsonParser.parseString(String(frame, offset, bodyLength, Charsets.UTF_8)).asJsonObject
                        val criteria = body.getAsJsonObject("data")
                        if (body.get("request").asString == "flag tweet") {
                            require(body.get("module").asString == "RedSquare")
                            require(criteria.keySet() == setOf("signature"))
                            require(criteria.get("signature").asString.matches(Regex("[0-9a-f]{128}")))
                            reports.add(frame)
                            val response = reportResponse?.invoke(frame) ?: "{}"
                            if (disconnectAfterReport) {
                                webSocket.close(1001, "Controlled report interruption")
                                return
                            }
                            if (withholdReportReply) return
                            webSocket.send((byteArrayOf(if (reportReplyIsError) 14.toByte() else 13.toByte()) +
                                frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                            return
                        }
                        if (body.get("request").asString == "registry query") {
                            when (criteria.get("request").asString) {
                                "registry namecheck" -> require(criteria.get("identifier").asString.matches(Regex("[A-Za-z0-9]{1,45}@saito")))
                                "registry query" -> {
                                    val keys = criteria.getAsJsonArray("keys")
                                    require(keys.size() in 1..50)
                                    require(keys.all { it.asString.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")) })
                                }
                                else -> error("Unexpected Registry request")
                            }
                            registryQueries.add(criteria.deepCopy())
                            val response = registryResponse?.invoke(criteria) ?:
                                if (criteria.get("request").asString == "registry query") "{}" else "{\"err\":\"no response\"}"
                            webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                            return
                        }
                        require(body.get("request").asString == "archive")
                        require(criteria.get("request").asString == "load")
                        val response = if (criteria.get("field1").asString == "Profile") {
                            require(criteria.get("field2").asString.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))
                            require(criteria.get("limit").asInt in 1..100)
                            profileArchiveResponse?.invoke(criteria) ?: "[]"
                        } else if (criteria.get("field1").asString == "RedSquareRetweet" && !criteria.has("sig")) {
                            require(criteria.get("flagged_ne").asInt == 1)
                            require(criteria.get("limit").asInt in 1..100)
                            quoteArchiveResponse?.invoke(criteria) ?: "[]"
                        } else archiveResponse?.invoke(criteria) ?: run {
                            require(criteria.get("field1").asString == "RedSquare")
                            require(criteria.get("field4").asString == "")
                            require(criteria.get("flagged_ne").asInt == 1)
                            rows
                        }
                        webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                        requests.incrementAndGet()
                        received.countDown()
                    }
                }
            } catch (problem: Throwable) { failure = problem; received.countDown() }
        }
    }).build()

    fun assertRequestObserved() { assertTrue("Archive request was not observed", received.await(5, TimeUnit.SECONDS)); assertHealthy() }
    fun assertHealthy() { failure?.let { throw AssertionError("Controlled peer rejected the request", it) } }
}

package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.saito.SaitoEnvelope

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
    private val relayResponse: ((com.google.gson.JsonObject) -> String)? = null,
    private val advertisedServices: String? = null,
    private val archiveResponse: ((com.google.gson.JsonObject) -> String)? = null) {
    @Volatile var registryFallbackBeforeNamecheck = false
    val registryFallbackPairsSent = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var disconnectAfterTransaction = false
    @Volatile var withholdCloseReplies = false
    private val closeRepliesLock = Any()
    private val withheldClosingSockets = java.util.concurrent.ConcurrentHashMap<WebSocket, Pair<Int, String>>()

    /** Release sockets deliberately held past the client's transport deadline. */
    fun releaseWithheldCloseReplies() {
        val pending = synchronized(closeRepliesLock) {
            withholdCloseReplies = false
            withheldClosingSockets.entries.map { it.key to it.value }.also { withheldClosingSockets.clear() }
        }
        pending.forEach { (socket, response) ->
            // Server-side upgraded sockets have no client Call to cancel.
            // Answer their retained close frame so MockWebServer can drain.
            socket.close(response.first, response.second)
        }
    }
    @Volatile var disconnectAfterReport = false
    @Volatile var withholdReportReply = false
    @Volatile var withholdArchiveReply = false
    @Volatile var rejectedArchiveKinds: Set<String> = emptySet()
    @Volatile var reportReplyIsError = false
    @Volatile var rejectNewConnections = false
    @Volatile var rejectionDelayMillis = 0L
    @Volatile var withholdHandshakeReplies = false
    private val releaseHandshakeReplies = CountDownLatch(1)
    private val peer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public test material only.
    private val counter = ByteArray(32) { 7 }
    private val received = CountDownLatch(1)
    val requests = java.util.concurrent.atomic.AtomicInteger()
    val connectionAttempts = java.util.concurrent.atomic.AtomicInteger()
    val servedChallenges = java.util.concurrent.atomic.AtomicInteger()
    val clientKeys = java.util.concurrent.CopyOnWriteArrayList<String>()
    val transactions = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    val reports = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    val relayRecipients = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
    val relayedMessages = java.util.concurrent.CopyOnWriteArrayList<com.google.gson.JsonObject>()
    val registryQueries = java.util.concurrent.CopyOnWriteArrayList<com.google.gson.JsonObject>()
    @Volatile var incomingTransaction: ByteArray? = null
    val incomingAcknowledgements = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    private val authenticatedSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()
    private val connectionSequence=java.util.concurrent.atomic.AtomicInteger()
    private val connectionIds=java.util.concurrent.ConcurrentHashMap<WebSocket,Int>()
    private val incomingRequestIds=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>().apply{add(777)}
    val authenticatedConnectionIds:Set<Int> get()=authenticatedSockets.mapNotNull{connectionIds[it]}.toSet()
    @Volatile private var failure: Throwable? = null

    /** Deliver after history loading, through actual still-connected authenticated sockets. */
    fun sendIncoming(transaction: ByteArray,requestId:Int=777) {
        require(requestId>0)
        incomingRequestIds.add(requestId)
        val frame = (byteArrayOf(12) + ByteBuffer.allocate(4).putInt(requestId).array() + transaction).toByteString()
        authenticatedSockets.forEach { it.send(frame) }
    }

    /** Interrupt only this isolated server's authenticated sockets. */
    fun disconnectAuthenticatedSockets():Set<Int> {
        val sockets=authenticatedSockets.toList()
        val interrupted=sockets.mapNotNull{connectionIds[it]}.toSet()
        sockets.forEach{it.close(1001,"Controlled reception interruption")}
        return interrupted
    }

    fun releaseWithheldHandshakeReplies() { releaseHandshakeReplies.countDown() }

    fun start(port: Int = 0): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                connectionAttempts.incrementAndGet()
                return if (rejectNewConnections) MockResponse.Builder().code(503)
                    .headersDelay(rejectionDelayMillis, TimeUnit.MILLISECONDS).build() else response()
            }
        }
        server.start(java.net.InetAddress.getByName("127.0.0.1"), port)
        return server
    }

    private fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
        private var authenticated = false
        private var interrupted = false
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            authenticatedSockets.remove(webSocket)
            connectionIds.remove(webSocket)
            val withheld = synchronized(closeRepliesLock) {
                if (withholdCloseReplies) {
                    withheldClosingSockets[webSocket] = code to reason
                    true
                } else false
            }
            if (!withheld) webSocket.close(code, reason)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            withheldClosingSockets.remove(webSocket)
            authenticatedSockets.remove(webSocket)
            connectionIds.remove(webSocket)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            withheldClosingSockets.remove(webSocket)
            authenticatedSockets.remove(webSocket)
            connectionIds.remove(webSocket)
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
                        if (withholdHandshakeReplies) check(releaseHandshakeReplies.await(15, TimeUnit.SECONDS))
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
                        connectionIds.computeIfAbsent(webSocket){connectionSequence.incrementAndGet()}
                        authenticatedSockets.addIfAbsent(webSocket)
                        incomingTransaction?.let { transaction ->
                            webSocket.send((byteArrayOf(12) + ByteBuffer.allocate(4).putInt(777).array() + transaction).toByteString())
                        }
                    }
                    13, 14 -> {
                        require(authenticated && frame.size >= 5 && ByteBuffer.wrap(frame, 1, 4).int in incomingRequestIds)
                        incomingAcknowledgements.add(frame)
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
                        if (body.get("request").asString == "relay peer message") {
                            val relay = JsonParser.parseString(String(java.util.Base64.getDecoder().decode(
                                criteria.get("buffer").asString), Charsets.UTF_8)).asJsonObject
                            val original = if (relay.get("module").asString == "Chat" && relay.get("request").asString == "chat group") {
                                SaitoEnvelope.decode(SaitoEnvelope.fromSignedJson(criteria))
                                criteria
                            } else {
                                require(relay.get("module").asString == "Relay" && relay.get("request").asString == "chat relay")
                                relay.getAsJsonObject("data")
                            }
                            relayRecipients.add(criteria.getAsJsonArray("to").map{it.asJsonObject.get("publicKey").asString})
                            relayedMessages.add(original.deepCopy())
                            val response = relayResponse?.invoke(original) ?: "{\"err\":\"\",\"success\":1}"
                            webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                            return
                        }
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
                            val prefix = byteArrayOf(13) + frame.copyOfRange(1, 5)
                            if (registryFallbackBeforeNamecheck && criteria.get("request").asString == "registry namecheck") {
                                // Same callback index and order observed on the public registrar relay.
                                check(webSocket.send((prefix + "{\"err\":\"no response\"}".toByteArray()).toByteString()))
                                check(webSocket.send((prefix + response.toByteArray()).toByteString()))
                                registryFallbackPairsSent.incrementAndGet()
                            } else webSocket.send((prefix + response.toByteArray()).toByteString())
                            return
                        }
                        require(body.get("request").asString == "archive")
                        require(criteria.get("request").asString == "load")
                        val response = if (criteria.get("field1")?.asString == "Profile") {
                            require(criteria.get("field2").asString.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))
                            require(criteria.get("limit").asInt in 1..100)
                            profileArchiveResponse?.invoke(criteria) ?: "[]"
                        } else if (criteria.get("field1")?.asString == "RedSquareRetweet" && !criteria.has("sig")) {
                            require(criteria.get("flagged_ne").asInt == 1)
                            require(criteria.get("limit").asInt in 1..100)
                            quoteArchiveResponse?.invoke(criteria) ?: "[]"
                        } else if (criteria.get("field1")?.asString == "RedSquareLike") {
                            if (criteria.has("sig")) {
                                // Receipt queries intentionally contain no history limit/cursor.
                                require(criteria.get("sig").asString.matches(Regex("[0-9a-f]{128}")))
                            } else {
                                require(criteria.has("field2") xor criteria.has("field3"))
                                val identity = criteria.get(if (criteria.has("field2")) "field2" else "field3").asString
                                require(identity.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))
                                require(criteria.get("limit").asInt in 1..100)
                                require(criteria.get("created_earlier_than").asLong >= 0)
                                if (criteria.has("field3") || criteria.has("flagged_ne")) require(criteria.get("flagged_ne").asInt == 1)
                            }
                            archiveResponse?.invoke(criteria) ?: "[]"
                        } else archiveResponse?.invoke(criteria) ?: run {
                            require(criteria.get("field1").asString == "RedSquare") {
                                "Unexpected archive request: $criteria"
                            }
                            require(criteria.get("field4").asString == "")
                            require(criteria.get("flagged_ne").asInt == 1)
                            rows
                        }
                        if (!withholdArchiveReply) {
                            webSocket.send((byteArrayOf(if (criteria.get("field1")?.asString in rejectedArchiveKinds) 14.toByte() else 13.toByte()) +
                                frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                        }
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

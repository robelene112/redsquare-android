package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction

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
    private val onArchiveResponse: ((com.google.gson.JsonObject, String, Boolean) -> Unit)? = null,
    private val relayResponseExecutor: java.util.concurrent.Executor? = null,
    private val peer: SaitoWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1"),
    private val archiveResponse: ((com.google.gson.JsonObject) -> String)? = null) {
    @Volatile var vaultUploadResponseExecutor: java.util.concurrent.Executor? = null
    @Volatile var vaultUploadResponse: ((nl.davidengelen.redsquare.saito.SignedSaitoMessage) -> String)? = null
    val vaultUploads = java.util.concurrent.CopyOnWriteArrayList<nl.davidengelen.redsquare.saito.SignedSaitoMessage>()
    private val expectedClosedVaultUploadReplies = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val observedClosedVaultUploadReplies = java.util.concurrent.CopyOnWriteArrayList<String>()
    fun expectClosedVaultUploadReply(requestId: String) {
        require(requestId.matches(Regex("[0-9a-f]{128}")))
        check(expectedClosedVaultUploadReplies.add(requestId))
    }
    @Volatile var withholdStoreReply = false
    @Volatile var storeResponse: ((com.google.gson.JsonObject) -> String)? = null
    val storeRequests = java.util.concurrent.CopyOnWriteArrayList<com.google.gson.JsonObject>()
    @Volatile var vaultResponse: ((nl.davidengelen.redsquare.saito.SignedSaitoMessage) -> String)? = null
    val vaultRequests = java.util.concurrent.CopyOnWriteArrayList<nl.davidengelen.redsquare.saito.SignedSaitoMessage>()
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
    @Volatile private var offline = false
    @Volatile var rejectNewConnections = false
        set(value) {
            if (!value) offline = false
            field = value
        }
    @Volatile var rejectionDelayMillis = 0L
    @Volatile var withholdHandshakeReplies = false
    private val releaseHandshakeReplies = CountDownLatch(1)
    val heldHandshakeEntries = java.util.concurrent.atomic.AtomicInteger()
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
    data class AuthenticatedRegistryQuery(val authenticatedKey: String, val query: com.google.gson.JsonObject)
    private val registryRequests = java.util.concurrent.CopyOnWriteArrayList<AuthenticatedRegistryQuery>()
    val authenticatedRegistryQueries get() = registryRequests.toList()
    val registryQueries get() = registryRequests.map { it.query }
    @Volatile var incomingTransaction: ByteArray? = null
    val incomingAcknowledgements = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()
    private val openSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()
    private val authenticatedSockets = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()
    private val connectionSequence=java.util.concurrent.atomic.AtomicInteger()
    private val connectionIds=java.util.concurrent.ConcurrentHashMap<WebSocket,Int>()
    private val incomingRequestIds=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>().apply{add(777)}
    val admittedConnectionCount: Int get() = openSockets.size
    val authenticatedConnectionIds:Set<Int> get()=authenticatedSockets.mapNotNull{connectionIds[it]}.toSet()
    @Volatile private var failure: Throwable? = null

    /** Deliver after history loading, through actual still-connected authenticated sockets. */
    fun sendIncoming(transaction: ByteArray,requestId:Int=777) {
        require(requestId>0)
        incomingRequestIds.add(requestId)
        val frame = (byteArrayOf(12) + ByteBuffer.allocate(4).putInt(requestId).array() + transaction).toByteString()
        authenticatedSockets.forEach { it.send(frame) }
    }

    /** Deliver an unchanged wire frame only to the positively identified test sockets. */
    fun sendIncomingTo(ids: Set<Int>, transaction: ByteArray, requestId: Int): Set<Int> {
        require(requestId > 0)
        incomingRequestIds.add(requestId)
        val frame = (byteArrayOf(12) + ByteBuffer.allocate(4).putInt(requestId).array() + transaction).toByteString()
        return authenticatedSockets.mapNotNull { socket ->
            connectionIds[socket]?.takeIf { it in ids && socket.send(frame) }
        }.toSet()
    }

    /** Actual owned outage: reject new upgrades and stop already admitted transports.
     * New-upgrade-only rejection remains separately available. Setting it false
     * restores service for the existing recovery controls in native fixtures.
     */
    fun goOffline() {
        offline = true
        rejectNewConnections = true
        openSockets.toList().forEach { it.close(1001, "Controlled node outage") }
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

    /** Independent strict zero-fund RPC decoding for the bounded large Vault test route. */
    private fun verifyVaultUpload(frame: ByteArray, author: String): nl.davidengelen.redsquare.saito.SignedSaitoMessage {
        val wire = frame.copyOfRange(5, frame.size)
        require(wire.size in 211..(8 * 1024 * 1024 + 211))
        val header = ByteBuffer.wrap(wire)
        require(header.getInt(0) == 1 && header.getInt(4) == 1 && header.getInt(12) == 0 &&
            header.getInt(88) == 1 && wire[92] == 0.toByte() && header.getLong(80) >= 0)
        val length = header.getInt(8)
        require(length > 0 && wire.size == 211 + length)
        val message = wire.copyOfRange(211, wire.size)
        val body = JsonParser.parseString(message.decodeToString()).asJsonObject
        require(body.keySet() == setOf("request", "data") && body.get("request").asString == "vault add file")
        val nested = body.get("data").asString
        val file = SaitoEnvelope.decode(nested)
        require(file.author == author && file.body.get("request").asString == "vault add file")
        val nestedHeader = java.util.Base64.getDecoder().decode(JsonParser.parseString(nested).asJsonObject.get("t").asString)
        val point = nestedHeader.copyOfRange(93, 126)
        val preimage = ByteBuffer.allocate(102 + length).putLong(header.getLong(80))
        repeat(2) { index ->
            val offset = 93 + 59 * index
            require(wire.copyOfRange(offset, offset + 33).contentEquals(point))
            require(wire.copyOfRange(offset + 33, offset + 59).all { it == 0.toByte() })
            preimage.put(wire, offset, 41).put(wire, offset + 57, 2)
        }
        preimage.putInt(1).putInt(0).put(message)
        val signature = wire.copyOfRange(16, 80).joinToString("") { "%02x".format(it) }
        require(SaitoWallet.verify(preimage.array(), signature, author))
        return nl.davidengelen.redsquare.saito.SignedSaitoMessage(signature, author, header.getLong(80), body, listOf(author))
    }

    private fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
        private var authenticated = false
        private var authenticatedKey = ""
        private var interrupted = false
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            openSockets.addIfAbsent(webSocket)
            if (offline) webSocket.close(1001, "Controlled node outage")
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            openSockets.remove(webSocket)
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
            openSockets.remove(webSocket)
            withheldClosingSockets.remove(webSocket)
            authenticatedSockets.remove(webSocket)
            connectionIds.remove(webSocket)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            openSockets.remove(webSocket)
            withheldClosingSockets.remove(webSocket)
            authenticatedSockets.remove(webSocket)
            connectionIds.remove(webSocket)
        }
        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            openSockets.addIfAbsent(webSocket)
            if (offline) { webSocket.close(1001, "Controlled node outage"); return }
            try {
                val frame = bytes.toByteArray()
                when (frame[0].toInt()) {
                    20 -> advertisedServices?.let {
                        require(frame.size == 1)
                        webSocket.send((byteArrayOf(9) + it.toByteArray(Charsets.UTF_8)).toByteString())
                    }
                    1 -> {
                        require(frame.size == 33)
                        if (withholdHandshakeReplies && releaseHandshakeReplies.count > 0L) {
                            heldHandshakeEntries.incrementAndGet()
                            check(releaseHandshakeReplies.await(15, TimeUnit.SECONDS))
                        }
                        if (offline) { webSocket.close(1001, "Controlled node outage"); return }
                        var number = java.math.BigInteger.ZERO
                        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
                        peer.publicKey.forEach { digit -> number = number * java.math.BigInteger.valueOf(58) + java.math.BigInteger.valueOf(alphabet.indexOf(digit).toLong()) }
                        val key = number.toByteArray().also { require(it.size == 33) }
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
                        authenticatedKey = clientKey
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
                        if (body.get("request").asString == "vault add file") {
                            val signed = verifyVaultUpload(frame, requireNotNull(authenticatedKey))
                            vaultUploads.add(signed)
                            val respond = Runnable {
                                try {
                                    val response = requireNotNull(vaultUploadResponse)(signed)
                                    val queued = webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                                    if (expectedClosedVaultUploadReplies.remove(signed.id)) {
                                        check(!queued) { "The declared closed upload reply was unexpectedly queued" }
                                        observedClosedVaultUploadReplies.add(signed.id)
                                    } else check(queued)
                                } catch (problem: Throwable) { recordFailure(problem) }
                            }
                            val executor = vaultUploadResponseExecutor
                            if (executor == null) respond.run() else executor.execute(respond)
                            return
                        }
                        val criteria = body.getAsJsonObject("data")
                        if (body.get("request").asString in setOf("load-listings", "load-listing", "load-seller-inventory", "load-listing-spend", "submit-listing") &&
                            criteria.get("module")?.asString == "Store") {
                            if (body.get("request").asString in setOf("load-listing-spend", "submit-listing")) {
                                val signed = SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(frame.copyOfRange(5, frame.size)))
                                require(SaitoEnvelope.decode(signed.envelope()).author == authenticatedKey)
                            }
                            storeRequests.add(body.deepCopy())
                            if (withholdStoreReply) return
                            val response = requireNotNull(storeResponse)(body)
                            check(webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString()))
                            return
                        }
                        if (body.get("request").asString == "vault access file") {
                            val original = SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(frame.copyOfRange(5, frame.size)))
                            val signed = SaitoEnvelope.decode(original.envelope())
                            require(signed.author == authenticatedKey)
                            vaultRequests.add(signed)
                            val response = requireNotNull(vaultResponse)(signed)
                            check(webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString()))
                            return
                        }
                        if (body.get("request").asString == "relay peer message") {
                            val relay = JsonParser.parseString(String(java.util.Base64.getDecoder().decode(
                                criteria.get("buffer").asString), Charsets.UTF_8)).asJsonObject
                            val original = if (relay.get("module").asString in setOf("Stun", "Videocall") ||
                                (relay.get("module").asString == "Relay" && relay.get("request").asString in setOf(
                                    "stun-connection-request", "stun-connection-request-cancel", "stun-connection-accepted",
                                    "stun-connection-rejected", "stun-connection-ping")) ||
                                (relay.get("module").asString == "Relay" && relay.get("request").asString in setOf(
                                    "query file permission", "grant file permission", "deny file permission", "stop file transfer")) ||
                                (relay.get("module").asString == "Chat" && relay.get("request").asString == "chat group")) {
                                SaitoEnvelope.decode(SaitoEnvelope.fromSignedJson(criteria))
                                criteria
                            } else {
                                require(relay.get("module").asString == "Relay" && relay.get("request").asString == "chat relay")
                                relay.getAsJsonObject("data")
                            }
                            relayRecipients.add(criteria.getAsJsonArray("to").map{it.asJsonObject.get("publicKey").asString})
                            relayedMessages.add(original.deepCopy())
                            // A held RPC reply must not hold the reader needed for incoming ACKs.
                            // The opting-in fixture owns and terminates its response executor.
                            val respond = Runnable {
                                try {
                                    val response = relayResponse?.invoke(original) ?: "{\"err\":\"\",\"success\":1}"
                                    webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                                } catch (problem: Throwable) { recordFailure(problem) }
                            }
                            if (relayResponseExecutor == null) respond.run() else relayResponseExecutor.execute(respond)
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
                            registryRequests.add(AuthenticatedRegistryQuery(authenticatedKey, criteria.deepCopy()))
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
                        val queued = !withholdArchiveReply && webSocket.send(
                            (byteArrayOf(if (criteria.get("field1")?.asString in rejectedArchiveKinds) 14.toByte() else 13.toByte()) +
                                frame.copyOfRange(1, 5) + response.toByteArray()).toByteString())
                        onArchiveResponse?.invoke(criteria.deepCopy(), response, queued)
                        requests.incrementAndGet()
                        received.countDown()
                    }
                }
            } catch (problem: Throwable) { recordFailure(problem) }
        }
    }).build()

    private fun recordFailure(problem: Throwable) { failure = problem; received.countDown() }
    fun assertRequestObserved() { assertTrue("Archive request was not observed", received.await(5, TimeUnit.SECONDS)); assertHealthy() }
    fun assertHealthy() {
        failure?.let { throw AssertionError("Controlled peer rejected the request", it) }
        check(expectedClosedVaultUploadReplies.isEmpty()) { "A declared closed Vault reply was never observed" }
    }
}

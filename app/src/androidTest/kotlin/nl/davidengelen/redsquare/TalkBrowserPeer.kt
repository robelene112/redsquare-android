package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import mockwebserver3.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Exact official module bodies in a separate real Chromium, with owned Relay and controlled inputs. */
internal class TalkBrowserPeer(val wallet: SaitoWallet, private val owner: SaitoWallet,
    private val colour: String, private val frequency: Int, private val send: (String) -> Unit) : Closeable {
    val errors = CopyOnWriteArrayList<String>()
    val captures = AtomicInteger()
    data class Media(val width: Int, val height: Int, val nonBlack: Int, val rms: Double)
    val media = ConcurrentHashMap<String, Media>()
    private val ready = CountDownLatch(1)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val id = java.util.UUID.randomUUID().toString()
    private val bridge = Bridge()
    private lateinit var devtools: TalkDevTools
    private val html: String
    init {
        fun body(name: String, className: String): String {
            val source = instrumentation.context.assets.open("talk-$name").bufferedReader().use { it.readText() }
            val constants = if (className == "Relay") source.substring(source.indexOf("const RELAY_REPEAT_WINDOW_MS"),
                source.indexOf("class Relay")) else ""
            return constants + source.substring(source.indexOf("class $className"), source.lastIndexOf("module.exports"))
        }
        val classes = body("stun.js", "Stun") + body("dialer.js", "Dialer") + "\nconst DialingInterface = Dialer;\n" +
            body("StreamManager.js", "StreamManager") + body("videocall.js", "Videocall") + body("relay.js", "Relay")
        val source = instrumentation.context.assets.open("talk-browser-peer.js").bufferedReader().use { it.readText() }
            .replace("// INSERT_PINNED_CLASSES", classes)
        val host = """
            const Host = new Proxy({}, { get(target, name) { return (...args) => {
              const request = new XMLHttpRequest(); request.open('POST', '/p/$id/rpc', false);
              request.setRequestHeader('Content-Type', 'application/json');
              request.send(JSON.stringify({method:name,args}));
              if (request.status !== 200) throw Error('Owned fixture RPC failed: ' + name);
              return JSON.parse(request.responseText).value;
            }; }});
        """.trimIndent()
        html = "<html><body><div id='dialer'></div><script>$host\n$source</script></body></html>"
        TalkBrowserServer.add(id, this)
        try { devtools = TalkDevTools("http://127.0.0.1:19004/p/$id") { errors.add(it) } }
        catch (failure: Exception) { TalkBrowserServer.remove(id); throw failure }
        try { check(ready.await(15, TimeUnit.SECONDS)) { "Talk browser initialization: $errors" } }
        catch (failure: Exception) { devtools.close(); TalkBrowserServer.remove(id); throw failure }
    }
    fun receive(envelope: String) = evaluate("nativeEnvelope(${JsonPrimitive(envelope)})")
    fun join(room: TalkRoom, video: Boolean = true) = evaluate("joinRoom(${room.json()},$video)")
    fun dial(room: TalkRoom) = evaluate("dialNative(${room.json()})")
    fun answer() = evaluate("answer()")
    fun decline() = evaluate("decline()")
    fun leave() = evaluate("leave()")
    fun toggleAudio() = evaluate("toggleAudio()")
    fun toggleVideo() = evaluate("toggleVideo()")
    private fun evaluate(script: String) = devtools.evaluate(script)
    fun response(request: RecordedRequest): MockResponse {
        if (request.url.encodedPath == "/p/$id") return MockResponse.Builder().code(200)
            .addHeader("Content-Type", "text/html; charset=utf-8").body(html).build()
        require(request.url.encodedPath == "/p/$id/rpc" && request.bodySize <= 512 * 1024)
        val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
        val args = body.getAsJsonArray("args")
        val value: Any? = when (body.get("method").asString) {
            "publicKey" -> bridge.publicKey()
            "nativeKey" -> bridge.nativeKey()
            "relayKey" -> bridge.relayKey()
            "colour" -> bridge.colour()
            "frequency" -> bridge.frequency()
            "ready" -> { bridge.ready(); null }
            "captured" -> { bridge.captured(); null }
            "failure" -> { bridge.failure(args[0].asString); null }
            "media" -> { bridge.media(args[0].asString,args[1].asInt,args[2].asInt,args[3].asInt,args[4].asDouble); null }
            "sign" -> bridge.sign(args[0].asString,args[1].asString,args[2].asLong)
            "inspect" -> bridge.inspect(args[0].asString)
            "unsigned" -> bridge.unsigned(args[0].asString,args[1].asString,args[2].asLong)
            "relay" -> { bridge.relay(args[0].asString); null }
            else -> error("Unknown owned fixture method")
        }
        val json = JsonObject().apply { add("value", Gson().toJsonTree(value)) }
        return MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(json.toString()).build()
    }
    inner class Bridge {
        fun publicKey() = wallet.publicKey
        fun nativeKey() = owner.publicKey
        fun relayKey() = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        fun colour() = colour
        fun frequency() = frequency
        fun ready() { ready.countDown() }
        fun captured() { captures.incrementAndGet() }
        fun failure(message: String) { errors.add(message) }
        fun media(peer: String, width: Int, height: Int, nonBlack: Int, rms: Double) {
            media[peer] = Media(width, height, nonBlack, rms)
        }
        fun sign(body: String, recipients: String, timestamp: Long): String =
            SaitoTransaction.create(wallet, JsonParser.parseString(body).asJsonObject, timestamp,
                JsonParser.parseString(recipients).asJsonArray.map { it.asString }.distinct()).envelope()
        fun inspect(envelope: String): String {
            val value = SaitoEnvelope.decode(envelope)
            check(wallet.publicKey in value.recipients || value.author == wallet.publicKey)
            return JsonObject().apply {
                addProperty("author", value.author); add("body", value.body); addProperty("signature", value.id)
                addProperty("timestamp", value.createdAt)
                add("recipients", JsonArray().apply { value.recipients.forEach { add(it) } })
            }.toString()
        }
        fun unsigned(body: String, recipients: String, timestamp: Long): String {
            val value = JsonParser.parseString(sign(body, recipients, timestamp)).asJsonObject
            val header = java.util.Base64.getDecoder().decode(value.get("t").asString)
            val data = java.nio.ByteBuffer.wrap(header)
            val ownOutput = 93 + 59 * data.getInt(0)
            // Relay.createRelayTransaction adds only its addressed peers, without a wallet change output.
            val detached = header.copyOfRange(0, ownOutput) + header.copyOfRange(ownOutput + 59, header.size)
            java.nio.ByteBuffer.wrap(detached).putInt(4, data.getInt(4) - 1)
            detached.fill(0, 16, 80)
            value.addProperty("t", java.util.Base64.getEncoder().encodeToString(detached))
            return value.toString()
        }
        fun relay(envelope: String) { send(envelope) }
    }
    override fun close() { devtools.close(); TalkBrowserServer.remove(id) }
}

/** App-local framework adapter. adb forwards only this loopback test port to the owned host browser. */
private object TalkBrowserServer {
    private val peers = ConcurrentHashMap<String, TalkBrowserPeer>()
    private var server: MockWebServer? = null
    @Synchronized fun add(id: String, peer: TalkBrowserPeer) {
        if (server == null) server = MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return try {
                        val key = request.url.pathSegments.getOrNull(1)
                        peers[key]?.response(request) ?: MockResponse.Builder().code(404).build()
                    } catch (failure: Exception) { MockResponse.Builder().code(500).body(failure.javaClass.simpleName).build() }
                }
            }
            start(java.net.InetAddress.getByName("127.0.0.1"), 19004)
        }
        peers[id] = peer
    }
    fun remove(id: String) { peers.remove(id) }
}

/** Only browser/HTTP/CDP system boundaries are adapted. No call or RTC method is replaced. */
private class TalkDevTools(url: String, private val failure: (String) -> Unit) : Closeable {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val sequence = AtomicInteger()
    private val replies = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private val opened = CountDownLatch(1)
    private val socket: WebSocket
    private val target: String
    init {
        val response = client.newCall(Request.Builder().url("http://127.0.0.1:9222/json/new?about%3Ablank")
            .put(ByteArray(0).toRequestBody()).build()).execute().use {
            check(it.isSuccessful); JsonParser.parseString(requireNotNull(it.body).string()).asJsonObject
        }
        target = response.get("id").asString
        socket = client.newWebSocket(Request.Builder().url(response.get("webSocketDebuggerUrl").asString).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.countDown() }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JsonParser.parseString(text).asJsonObject
                if (message.has("id")) {
                    replies.remove(message.get("id").asInt)?.complete(message)
                    message.getAsJsonObject("result")?.getAsJsonObject("exceptionDetails")?.let { failure(it.toString().take(1000)) }
                }
                else if (message.get("method")?.asString == "Runtime.consoleAPICalled" &&
                    message.getAsJsonObject("params").get("type").asString == "error") {
                    failure(message.getAsJsonObject("params").getAsJsonArray("args").toString().take(1000))
                } else if (message.get("method")?.asString == "Fetch.requestPaused") {
                    val params = message.getAsJsonObject("params")
                    val address = params.getAsJsonObject("request").get("url").asString
                    val allowed = address.startsWith("http://127.0.0.1:19004/")
                    send(if (allowed) "Fetch.continueRequest" else "Fetch.failRequest", JsonObject().apply {
                        addProperty("requestId", params.get("requestId").asString)
                        if (!allowed) addProperty("errorReason", "BlockedByClient")
                    })
                }
            }
        })
        check(opened.await(5, TimeUnit.SECONDS))
        command("Runtime.enable", JsonObject())
        command("Fetch.enable", JsonObject().apply { add("patterns", JsonArray().apply { add(JsonObject().apply { addProperty("urlPattern", "*") }) }) })
        command("Page.navigate", JsonObject().apply { addProperty("url", url) })
    }
    private fun send(method: String, params: JsonObject, reply: CompletableFuture<JsonObject>? = null): Int {
        val id = sequence.incrementAndGet()
        if (reply != null) replies[id] = reply
        check(socket.send(JsonObject().apply { addProperty("id", id); addProperty("method", method); add("params", params) }.toString()))
        return id
    }
    private fun command(method: String, params: JsonObject) {
        val reply = CompletableFuture<JsonObject>(); val id = send(method, params, reply)
        try { check(!reply.get(5, TimeUnit.SECONDS).has("error")) }
        finally { replies.remove(id) }
    }
    fun evaluate(script: String) {
        // Do not wait inside a synchronous Host RPC routed from another browser peer.
        send("Runtime.evaluate", JsonObject().apply { addProperty("expression", script) })
    }
    override fun close() {
        runCatching { client.newCall(Request.Builder().url("http://127.0.0.1:9222/json/close/$target").build()).execute().close() }
        socket.close(1000, "Owned proof completed")
        client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
}

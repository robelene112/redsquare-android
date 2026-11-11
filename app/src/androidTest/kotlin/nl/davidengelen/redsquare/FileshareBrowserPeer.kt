package nl.davidengelen.redsquare

import android.annotation.SuppressLint
import android.webkit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Real WebView RTC and actual pinned Fileshare bodies. No production app WebView or public connection. */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
internal class FileshareBrowserPeer(private val owner: SaitoWallet, private val relay: ControlledFeedPeer,
    pin: String) : Closeable {
    val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture, never fund.
    val errors = CopyOnWriteArrayList<String>()
    val received = AtomicReference<ByteArray?>()
    val open = CountDownLatch(1)
    val chunks = AtomicInteger()
    val signedChunks = AtomicInteger()
    val acknowledgedChunk = AtomicLong()
    val uploadReadFinished = AtomicBoolean()
    private val ready = CountDownLatch(1)
    private val sequence = AtomicInteger(1000)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var view: WebView

    init {
        val source = instrumentation.context.assets.open("fileshare-$pin.js").bufferedReader().use { it.readText() }
        val body = source.substring(source.indexOf("class Fileshare"), source.lastIndexOf("module.exports"))
        val adapter = instrumentation.context.assets.open("fileshare-browser-peer.js").bufferedReader().use { it.readText() }
            .replace("const fss = Object.create", "$body\nconst fss = Object.create")
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                addJavascriptInterface(Bridge(), "Host")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) errors.add("${message.lineNumber()}: ${message.message()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                }
                loadDataWithBaseURL("https://fileshare.fixture.invalid/", "<html><body><script>$adapter</script></body></html>", "text/html", "utf-8", null)
            }
        }
        check(ready.await(15, TimeUnit.SECONDS)) { "Browser fixture did not initialize: $errors" }
    }

    fun relay(inner: JsonObject): String {
        val message = SaitoEnvelope.decode(SaitoEnvelope.fromSignedJson(inner))
        check(message.author == owner.publicKey && wallet.publicKey in message.recipients)
        evaluate("nativeSignal(${message.body})")
        return "{\"err\":\"\",\"success\":1}"
    }
    fun send(bytes: ByteArray, name: String, id: String) {
        evaluate("sendFile(${com.google.gson.JsonPrimitive(Base64.getEncoder().encodeToString(bytes))},${com.google.gson.JsonPrimitive(name)},${com.google.gson.JsonPrimitive(id)})")
    }
    fun sendGenerated(size: Int, name: String, id: String) {
        evaluate("sendGeneratedFile($size,${com.google.gson.JsonPrimitive(name)},${com.google.gson.JsonPrimitive(id)})")
    }
    fun disconnect() = evaluate("closePeer()")
    fun hold(receipts: Boolean, chunks: Boolean) = evaluate("holdTransfer($receipts,$chunks)")
    fun release() = evaluate("releaseTransfer()")
    private fun evaluate(script: String) {
        instrumentation.runOnMainSync { view.evaluateJavascript(script, null) }
    }

    inner class Bridge {
        @JavascriptInterface fun publicKey() = wallet.publicKey
        @JavascriptInterface fun nativeKey() = owner.publicKey
        @JavascriptInterface fun ready() { ready.countDown() }
        @JavascriptInterface fun opened() { open.countDown() }
        @JavascriptInterface fun failure(message: String) { errors.add(message) }
        @JavascriptInterface fun sent() { uploadReadFinished.set(true) }
        @JavascriptInterface fun chunkReceived() { chunks.incrementAndGet() }
        @JavascriptInterface fun complete(base64: String) { received.set(Base64.getDecoder().decode(base64)) }
        @JavascriptInterface fun sign(body: String, unsigned: Boolean): String {
            val message = JsonParser.parseString(body).asJsonObject
            if (message.get("module")?.asString == "Fileshare" && message.get("request")?.asString == "share file") signedChunks.incrementAndGet()
            val transaction = SaitoTransaction.create(wallet, message,
                System.currentTimeMillis(), listOf(owner.publicKey))
            if (!unsigned) return transaction.envelope()
            val envelope = JsonParser.parseString(transaction.envelope()).asJsonObject
            val binary = Base64.getDecoder().decode(envelope.get("t").asString).apply { fill(0, 16, 80) }
            envelope.addProperty("t", Base64.getEncoder().encodeToString(binary))
            return envelope.toString()
        }
        @JavascriptInterface fun inspect(envelope: String): String {
            val signed = SaitoEnvelope.decode(envelope)
            check(signed.author == owner.publicKey && wallet.publicKey in signed.recipients)
            if (signed.body.get("request")?.asString == "read receipt")
                acknowledgedChunk.accumulateAndGet(signed.body.getAsJsonObject("data").get("chunk_id").asLong, ::maxOf)
            return JsonObject().apply {
                addProperty("author", signed.author); add("body", signed.body)
                add("recipients", com.google.gson.JsonArray().apply { signed.recipients.forEach { add(it) } })
            }.toString()
        }
        @JavascriptInterface fun signal(body: String) {
            val data = JsonParser.parseString(body).asJsonObject
            val tx = Fileshare.signal(wallet, owner.publicKey, data.get("request").asString, data, System.currentTimeMillis())
            relay.sendIncoming(tx.applicationRequest(1).copyOfRange(5, tx.applicationRequest(1).size), sequence.incrementAndGet())
        }
    }
    override fun close() { instrumentation.runOnMainSync {
        view.evaluateJavascript("closePeer()", null); view.removeJavascriptInterface("Host"); view.destroy()
    } }
}

package nl.davidengelen.redsquare

import android.annotation.SuppressLint
import android.webkit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.*

/** Exact official module bodies in a real WebView, with owned Relay and controlled device inputs. */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
internal class TalkBrowserPeer(val wallet: SaitoWallet, private val owner: SaitoWallet,
    private val colour: String, private val frequency: Int, private val send: (String) -> Unit) : Closeable {
    val errors = CopyOnWriteArrayList<String>()
    val captures = AtomicInteger()
    data class Media(val width: Int, val height: Int, val nonBlack: Int, val rms: Double)
    val media = ConcurrentHashMap<String, Media>()
    private val ready = CountDownLatch(1)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var view: WebView
    init {
        fun body(name: String, className: String): String {
            val source = instrumentation.context.assets.open("talk-$name").bufferedReader().use { it.readText() }
            return source.substring(source.indexOf("class $className"), source.lastIndexOf("module.exports"))
        }
        val classes = body("stun.js", "Stun") + body("dialer.js", "Dialer") + "\nconst DialingInterface = Dialer;\n" +
            body("StreamManager.js", "StreamManager") + body("videocall.js", "Videocall")
        val source = instrumentation.context.assets.open("talk-browser-peer.js").bufferedReader().use { it.readText() }
            .replace("// INSERT_PINNED_CLASSES", classes)
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false; settings.allowContentAccess = false
                addJavascriptInterface(Bridge(), "Host")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) errors.add("${message.lineNumber()}: ${message.message()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                }
                loadDataWithBaseURL("https://talk.fixture.invalid/", "<html><body><div id='dialer'></div><script>$source</script></body></html>", "text/html", "utf-8", null)
            }
        }
        check(ready.await(15, TimeUnit.SECONDS)) { "Talk browser initialization: $errors" }
    }
    fun receive(envelope: String) = evaluate("nativeEnvelope(${JsonPrimitive(envelope)})")
    fun join(room: TalkRoom, video: Boolean = true) = evaluate("joinRoom(${room.json()},$video)")
    fun dial(room: TalkRoom) = evaluate("dialNative(${room.json()})")
    fun answer() = evaluate("answer()")
    fun decline() = evaluate("decline()")
    fun leave() = evaluate("leave()")
    fun toggleAudio() = evaluate("toggleAudio()")
    fun toggleVideo() = evaluate("toggleVideo()")
    private fun evaluate(script: String) { instrumentation.runOnMainSync { view.evaluateJavascript(script, null) } }
    inner class Bridge {
        @JavascriptInterface fun publicKey() = wallet.publicKey
        @JavascriptInterface fun nativeKey() = owner.publicKey
        @JavascriptInterface fun colour() = colour
        @JavascriptInterface fun frequency() = frequency
        @JavascriptInterface fun ready() { ready.countDown() }
        @JavascriptInterface fun captured() { captures.incrementAndGet() }
        @JavascriptInterface fun failure(message: String) { errors.add(message) }
        @JavascriptInterface fun media(peer: String, width: Int, height: Int, nonBlack: Int, rms: Double) {
            media[peer] = Media(width, height, nonBlack, rms)
        }
        @JavascriptInterface fun sign(body: String, recipients: String, timestamp: Long): String =
            SaitoTransaction.create(wallet, JsonParser.parseString(body).asJsonObject, timestamp,
                JsonParser.parseString(recipients).asJsonArray.map { it.asString }.distinct()).envelope()
        @JavascriptInterface fun inspect(envelope: String): String {
            val value = SaitoEnvelope.decode(envelope)
            check(wallet.publicKey in value.recipients || value.author == wallet.publicKey)
            return JsonObject().apply {
                addProperty("author", value.author); add("body", value.body); addProperty("signature", value.id)
                addProperty("timestamp", value.createdAt)
                add("recipients", JsonArray().apply { value.recipients.forEach { add(it) } })
            }.toString()
        }
        @JavascriptInterface fun relay(envelope: String) { send(envelope) }
    }
    override fun close() { instrumentation.runOnMainSync {
        view.evaluateJavascript("closeFixture()", null); view.removeJavascriptInterface("Host"); view.destroy()
    } }
}

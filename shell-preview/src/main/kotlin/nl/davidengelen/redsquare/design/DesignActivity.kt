package nl.davidengelen.redsquare.design

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.shell.*
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DesignSessionModel : ViewModel() {
    var document by mutableStateOf<Design?>(null)
    var status by mutableStateOf("Connecting")
    var appearance by mutableStateOf("system")
    val session = ShellSession()
}

/** Isolated design package: no wallet, push transport or Saito networking is initialised. */
class DesignActivity : ComponentActivity() {
    private val endpoint = "https://davidengelen.nl/redsquare/design/design.json"
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val model by lazy { ViewModelProvider(this).get(DesignSessionModel::class.java) }
    private var document: Design?
        get() = model.document
        set(value) { model.document = value }
    private var status: String
        get() = model.status
        set(value) { model.status = value }
    private var appearance: String
        get() = model.appearance
        set(value) { model.appearance = value }
    private var refresh by mutableIntStateOf(0)
    private val session get() = model.session
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (document == null) document = Design(JSONObject(assets.open("design.json").bufferedReader().use { it.readText() }))
        if (BuildConfig.DEBUG && savedInstanceState == null) {
            intent.getStringExtra("appearance")?.takeIf { it in listOf("system", "dark", "light") }?.let { appearance = it }
            intent.getStringExtra("route")?.takeIf { document!!.screens.has(it) }?.let {
                session.route = it
                document!!.screen(it).optString("module").takeIf { module -> module in listOf("redsquare", "stack") }?.let { module -> session.module = module }
            }
        }
        val debugUrl = intent.getStringExtra("designUrl")
        val url = if (BuildConfig.DEBUG && (debugUrl?.startsWith("http://127.0.0.1:") == true || debugUrl?.startsWith("https://davidengelen.nl/redsquare/design/proof/") == true)) debugUrl else endpoint
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val start = SystemClock.elapsedRealtime()
                    try {
                        val next = withContext(Dispatchers.IO) {
                            client.newCall(Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()).execute().use { response ->
                                check(response.isSuccessful)
                                val body = response.body ?: error("Empty design")
                                require(body.contentLength() <= 2_000_000)
                                val bytes = body.byteStream().use { stream ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = stream.read(buffer)
                                        if (count < 0) break
                                        require(output.size() + count <= 2_000_000)
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                                require(bytes.size <= 2_000_000)
                                Design(JSONObject(bytes.toString(Charsets.UTF_8)))
                            }
                        }
                        status = "Live"
                        if (next.revision != document?.revision) {
                            document = next
                            Log.i("ShellPreview", "design_applied revision=${next.revision} fetchMs=${SystemClock.elapsedRealtime()-start}")
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { status = "Offline design" }
                    val observed = refresh
                    repeat(20) { if (refresh == observed) delay(100) }
                }
            }
        }
        setContent {
            val design = document ?: return@setContent
            val dark = appearance == "dark" || appearance == "system" && isSystemInDarkTheme()
            SideEffect { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }) }
            DesignTheme(design, appearance) {
                NativeShell(design, session, status, appearance, { appearance = it }, { refresh++ })
                LaunchedEffect(design.revision) {
                    withFrameNanos { }
                    withFrameNanos { Log.i("ShellPreview", "design_frame revision=${design.revision} elapsedMs=${SystemClock.elapsedRealtime()}") }
                }
            }
        }
    }
}

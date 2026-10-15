package nl.davidengelen.redsquare.feed

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.YouTubeVideo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal val LocalVideoFixture = staticCompositionLocalOf<String?> { null }

internal object VideoFixture {
    fun fromIntent(intent: Intent): String? {
        if (!BuildConfig.DEBUG) return null
        val source = intent.getStringExtra("youtubeFixtureUrl") ?: return null
        return try {
            val uri = URI(source)
            if (uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/iframe_api" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) source
            else "http://127.0.0.1:1/iframe_api"
        } catch (_: Exception) { "http://127.0.0.1:1/iframe_api" }
    }
}

internal enum class VideoStatus(val message: Int, val failed: Boolean = false) {
    LOADING(R.string.video_loading), READY(R.string.video_ready), PLAYING(R.string.video_playing),
    INVALID(R.string.video_invalid, true), UNAVAILABLE(R.string.video_unavailable, true),
    EMBED_DISABLED(R.string.video_embed_disabled, true), IDENTITY_FAILURE(R.string.video_identity_failure, true),
    PLAYBACK_FAILURE(R.string.video_playback_failure, true), NETWORK_FAILURE(R.string.video_network_failure, true);

    companion object {
        fun error(code: Int): VideoStatus = when (code) {
            2 -> INVALID; 100 -> UNAVAILABLE; 101, 150 -> EMBED_DISABLED; 153 -> IDENTITY_FAILURE
            -1 -> NETWORK_FAILURE; else -> PLAYBACK_FAILURE
        }
    }
}

/** A provider-only browser. No JavaScript bridge or wallet capability is exposed. */
@SuppressLint("SetJavaScriptEnabled")
internal class VideoBrowser(context: Context, video: YouTubeVideo, fixture: String?, private val diagnostics: VideoDiagnostics?, private val report: (VideoStatus) -> Unit) {
    var closed = false
        private set
    private val origin = "https://${context.packageName}"
    val view = WebView(context)
    private val diagnosticAttempt = diagnostics?.begin(video.id, origin, view.settings.userAgentString)

    init {
        // WebView's content-height mode collapses relative HTML/iframe heights.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.contentDescription = context.getString(R.string.video_player)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (BuildConfig.DEBUG && fixture != null) android.util.Log.w("RedSquareVideo", "Controlled player console ${message.messageLevel()} at line ${message.lineNumber()}")
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(web: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return request.url.scheme != "https"
                // A provider gesture can open its ordinary HTTPS link externally.
                if (request.hasGesture() && request.url.scheme == "https") {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: android.content.ActivityNotFoundException) { }
                }
                return true
            }
            override fun onReceivedError(web: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (BuildConfig.DEBUG && fixture != null) android.util.Log.w("RedSquareVideo", "Controlled player network error ${error.errorCode}, main=${request.isForMainFrame}")
                if (request.isForMainFrame && !closed) report(VideoStatus.NETWORK_FAILURE)
            }
            override fun onReceivedHttpError(web: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && !closed) report(VideoStatus.NETWORK_FAILURE)
            }
            override fun onRenderProcessGone(web: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (!closed) {
                    report(VideoStatus.PLAYBACK_FAILURE)
                    release()
                }
                return true
            }
            override fun shouldInterceptRequest(web: WebView, request: WebResourceRequest): WebResourceResponse? {
                diagnostics?.request(diagnosticAttempt, request)
                if (fixture == null || request.url.toString() != "https://www.youtube.com/iframe_api") return null
                // Debug-only replacement at the provider boundary. No Saito or wallet
                // data is forwarded, and release builds cannot configure this endpoint.
                return try {
                    val call = fixtureClient.newCall(Request.Builder().url(fixture).apply {
                        request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }
                            ?.let { header("Referer", it.value) }
                    }.build())
                    call.execute().use { response ->
                        check(response.isSuccessful)
                        val source = response.body.source()
                        check(!source.request(128 * 1024 + 1L))
                        val bytes = source.readByteArray()
                        WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(bytes))
                    }
                } catch (_: Exception) {
                    if (BuildConfig.DEBUG) android.util.Log.w("RedSquareVideo", "Controlled player API fetch failed")
                    WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream("window.rsVideo={state:'error',code:-1};".toByteArray()))
                }
            }
        }
        view.loadDataWithBaseURL("$origin/", """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>html,body,#player{margin:0;width:100%;height:100%;background:#000}iframe{border:0;display:block}</style></head>
            <body><div id="player"></div><script>
            window.rsVideo={state:'loading',code:0};
            function onYouTubeIframeAPIReady(){
              try { window.rsPlayer=new YT.Player('player',{width:'100%',height:'100%',videoId:'${video.id}',
                playerVars:{autoplay:0,playsinline:1,origin:'$origin'},events:{
                  onReady:function(e){
                    if(window.rsVideo.state==='error')return;
                    try{e.target.cueVideoById('${video.id}');
                      if(window.rsVideo.state!=='error'){window.rsVideo={state:'ready',code:0}}
                    }catch(err){window.rsVideo={state:'error',code:5}}
                  },
                  onStateChange:function(e){
                    if(window.rsVideo.state==='error')return;
                    if(e.data===1){window.rsVideo={state:'playing',code:0}}
                    else if(e.data===0||e.data===2||e.data===5){window.rsVideo={state:'ready',code:0}}
                  },
                  onError:function(e){window.rsVideo={state:'error',code:e.data}}
                }});
              } catch(err){window.rsVideo={state:'error',code:5}}
            }
            </script><script src="https://www.youtube.com/iframe_api" onerror="window.rsVideo={state:'error',code:-1}"></script></body></html>
        """.trimIndent(), "text/html", "UTF-8", null)
    }

    suspend fun readStatus(): VideoStatus? {
        if (closed) return null
        val result = withTimeoutOrNull(1000) {
            suspendCancellableCoroutine<String?> { continuation ->
                view.evaluateJavascript("JSON.stringify(window.rsVideo || {state:'loading',code:0})") { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        } ?: return null
        if (closed || result.length > 1024) return null
        return try {
            val status = JsonParser.parseString(JsonParser.parseString(result).asString).asJsonObject
            val state = status.get("state")?.asString.orEmpty()
            val code = status.get("code")?.asInt ?: 5
            diagnostics?.status(diagnosticAttempt, state, code)
            when (state) {
                "ready" -> VideoStatus.READY
                "playing" -> VideoStatus.PLAYING
                "error" -> VideoStatus.error(code)
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun pause() {
        if (closed) return
        view.evaluateJavascript("if(window.rsPlayer){window.rsPlayer.pauseVideo()}", null)
        view.onPause()
    }
    fun resume() { if (!closed) view.onResume() } // Never resumes playback automatically.
    fun close() {
        if (closed) return
        pause()
        closed = true
        view.stopLoading()
        release()
    }
    private fun release() {
        closed = true
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
    }

    companion object {
        private val fixtureClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}

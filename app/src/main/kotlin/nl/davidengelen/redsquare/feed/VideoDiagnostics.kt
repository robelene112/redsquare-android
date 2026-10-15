package nl.davidengelen.redsquare.feed

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.webkit.WebResourceRequest
import com.google.gson.Gson
import nl.davidengelen.redsquare.BuildConfig
import java.net.URI

internal val LocalVideoDiagnostics = androidx.compose.runtime.staticCompositionLocalOf<VideoDiagnostics?> { null }

/** Explicit emulator-only public-media observation. Never records opaque provider data. */
internal class VideoDiagnostics private constructor(private val packageName: String) {
    private data class Attempt(val number: Int, val videoId: String, val origin: String, val userAgent: String,
        val events: MutableList<Map<String, Any>> = mutableListOf(), var previousStatus: Pair<String, Int>? = null)
    private val attempts = mutableListOf<Attempt>()

    @Synchronized fun begin(videoId: String, origin: String, userAgent: String): Int? {
        if (videoId !in publicIds || attempts.size >= 4) return null
        val number = attempts.size
        attempts += Attempt(number, videoId, origin, userAgent.take(512))
        return number
    }

    fun request(number: Int?, request: WebResourceRequest) {
        try { recordRequest(number, request) } catch (_: Exception) { }
    }

    @Synchronized private fun recordRequest(number: Int?, request: WebResourceRequest) {
        val attempt = attempts.getOrNull(number ?: return) ?: return
        val url = request.url
        if (url.scheme != "https" || url.host != "www.youtube.com" || url.path != "/embed/${attempt.videoId}") return
        if (attempt.events.count { it["kind"] == "embed-request" } >= 2) return
        val raw = request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
        val referer = boundedOrigin(raw)
        val suppliedOrigin = boundedOrigin(try { url.getQueryParameter("origin") } catch (_: Exception) { null })
        attempt.events += mapOf("kind" to "embed-request", "elapsedRealtimeMs" to SystemClock.elapsedRealtime(),
            "method" to (if (request.method == "GET") "GET" else "other"), "mainFrame" to request.isForMainFrame,
            "refererPresent" to (raw != null), "refererOrigin" to referer,
            "originParameter" to suppliedOrigin)
    }

    @Synchronized fun status(number: Int?, state: String, code: Int) {
        val attempt = attempts.getOrNull(number ?: return) ?: return
        if (state !in setOf("loading", "ready", "playing", "error")) return
        val status = state to code
        if (attempt.previousStatus == status) return
        attempt.previousStatus = status
        // Reserve two request records; retain the most recent six status transitions.
        if (attempt.events.count { it["kind"] == "player-status" } >= 6) {
            attempt.events.removeAt(attempt.events.indexOfFirst { it["kind"] == "player-status" })
        }
        attempt.events += mapOf("kind" to "player-status", "elapsedRealtimeMs" to SystemClock.elapsedRealtime(),
            "state" to state, "code" to code)
    }

    @Synchronized fun snapshot(): String = Gson().toJson(mapOf("source" to "WebView-reported metadata, not wire capture",
        "packageName" to packageName, "attempts" to attempts.map { mapOf("number" to it.number,
            "videoId" to it.videoId, "wrapperOrigin" to it.origin, "userAgent" to it.userAgent, "events" to it.events.toList()) }))

    private fun boundedOrigin(raw: String?): String {
        if (raw == null) return "absent"
        if (raw.length > 2048) return "other"
        return try {
            val value = URI(raw)
            if (value.scheme == "https" && value.host in setOf(packageName, "www.youtube.com") &&
                value.port == -1 && value.rawUserInfo == null) "https://${value.host}" else "other"
        } catch (_: Exception) { "invalid" }
    }

    companion object {
        private val publicIds = setOf("M7lc1UVf-VE", "bHQqvYy5KYo")
        fun fromIntent(context: Context, intent: Intent): VideoDiagnostics? =
            if (BuildConfig.DEBUG && context.packageName == "nl.davidengelen.redsquare.dev" &&
                Build.HARDWARE in listOf("ranchu", "goldfish") && intent.getBooleanExtra("publicVideoDiagnostics", false))
                VideoDiagnostics(context.packageName) else null
    }
}

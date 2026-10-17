package nl.davidengelen.redsquare.media

import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal val LocalGiphyConfiguration = staticCompositionLocalOf { GiphyConfiguration.production() }

/** The app's public Android/Posts API option, never a Saito server's browser key. */
internal data class GiphyConfiguration(val apiKey: String, val apiBase: HttpUrl, val fixtureOrigin: HttpUrl? = null) {
    val configured get() = apiKey.isNotEmpty()

    fun mediaUrl(value: String): String? {
        if (value.any { it.isWhitespace() || it.code < 32 }) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null || value.length > 8192) return null
        val fixture = fixtureOrigin
        if (fixture != null && url.scheme == fixture.scheme && url.host == fixture.host && url.port == fixture.port &&
            url.encodedPath.startsWith("/media/")) return value
        return value.takeIf { url.scheme == "https" && url.port == 443 &&
            (url.host == "giphy.com" || url.host.endsWith(".giphy.com")) }
    }

    companion object {
        fun production() = GiphyConfiguration(BuildConfig.GIPHY_POSTS_API_KEY, "https://api.giphy.com/".toHttpUrl())
        fun fromIntent(intent: Intent?): GiphyConfiguration {
            if (!BuildConfig.DEBUG || android.os.Build.HARDWARE !in listOf("ranchu", "goldfish")) return production()
            val input = intent?.getStringExtra("giphyFixtureUrl") ?: return production()
            val url = input.toHttpUrlOrNull()
            return if (url != null && url.scheme == "http" && url.host == "127.0.0.1" && url.port in 1..65535 &&
                url.encodedPath == "/" && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
                GiphyConfiguration("owned-test-only", url, url)
            else GiphyConfiguration("", "https://api.giphy.com/".toHttpUrl())
        }
    }
}

private fun String.toHttpUrlOrNull(): HttpUrl? = try { toHttpUrl() } catch (_: IllegalArgumentException) { null }

internal data class GiphyItem(val id: String, val title: String, val originalUrl: String, val thumbnailUrl: String,
    val creator: String?, val creatorUrl: String?, val sourceUrl: String?)

internal class GiphyUnavailable(val rateLimited: Boolean = false) : IOException("GIF provider unavailable")

/** Direct ephemeral result page. No query logging, wallet inputs or result/media store. */
internal class GiphyProvider(private val configuration: GiphyConfiguration) {
    suspend fun search(query: String): List<GiphyItem> = withContext(Dispatchers.IO) {
        check(configuration.configured)
        require(query.codePointCount(0, query.length) <= 50)
        val url = configuration.apiBase.newBuilder()
            .addPathSegments(if (query.isBlank()) "v1/gifs/trending" else "v1/gifs/search")
            .addQueryParameter("api_key", configuration.apiKey).addQueryParameter("limit", "24")
            .addQueryParameter("offset", "0").addQueryParameter("rating", "pg-13")
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }.build()
        val bytes = GiphyHttp.read(url, 2 * 1024 * 1024)
        val response = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        val status = response.getAsJsonObject("meta").get("status").asInt
        if (status != 200) throw GiphyUnavailable(status == 429)
        val rows = response.getAsJsonArray("data")
        require(rows.size() <= 24)
        rows.map { value ->
            val row = value.asJsonObject
            val images = row.getAsJsonObject("images")
            val original = requireNotNull(configuration.mediaUrl(images.getAsJsonObject("original").text("url") ?: ""))
            val thumbnail = images.getAsJsonObject("fixed_width_small")?.text("url")
                ?.let(configuration::mediaUrl) ?: original
            val user = row.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
            val creator = user?.text("display_name")?.takeIf { it.isNotBlank() }
                ?: user?.text("username")?.takeIf { it.isNotBlank() } ?: row.text("username")?.takeIf { it.isNotBlank() }
            val id = requireNotNull(row.text("id")).also { require(it.isNotBlank() && it.length <= 256) }
            GiphyItem(id, row.text("alt_text")?.takeIf { it.isNotBlank() }
                ?: row.text("title")?.takeIf { it.isNotBlank() } ?: "GIF", original, thumbnail, creator,
                user?.text("profile_url")?.let(::sourceLink), row.text("source_post_url")?.let(::sourceLink)
                    ?: row.text("source")?.let(::sourceLink))
        }
    }

    private fun JsonObject.text(name: String): String? = get(name)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString?.also { require(it.length <= 8192) }

    private fun sourceLink(value: String): String? = value.toHttpUrlOrNull()?.takeIf {
        it.scheme in setOf("http", "https") && it.username.isEmpty() && it.password.isEmpty()
    }?.let { value }
}

internal object GiphyHttp {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun read(url: HttpUrl, maxBytes: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful) throw GiphyUnavailable(it.code == 429)
                        val body = it.body
                        require(body.contentLength() <= maxBytes)
                        body.byteStream().use { stream ->
                            val result = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(result.size() + count <= maxBytes)
                                result.write(buffer, 0, count)
                            }
                            result.toByteArray()
                        }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }
}

package nl.davidengelen.redsquare

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.HttpURLConnection
import java.net.URI

/** Development distribution only. No wallet data is part of the request. */
object PreviewUpdates {
    const val REVIEW_PAGE = "https://davidengelen.nl/redsquare/"
    const val MANIFEST = "${REVIEW_PAGE}latest.json"

    fun hasNewerBuild(endpoint: String, installedVersion: Long): Boolean {
        return try {
            val uri = URI(endpoint)
            val testEndpoint = BuildConfig.DEBUG && uri.scheme == "http" && uri.host == "127.0.0.1"
                && uri.port in 1..65535 && uri.path == "/latest.json" && uri.userInfo == null
            if (!BuildConfig.REVIEW_BUILD && !testEndpoint) return false
            if (endpoint != MANIFEST && !testEndpoint) return false
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            try {
                if (connection.responseCode != 200) return false
                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArray(16_385)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    if (count > 16_384) return false
                    buffer.copyOf(count)
                }
                val manifest = GsonBuilder().setStrictness(Strictness.STRICT).create()
                    .fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
                if (manifest.get("applicationId")?.asString != "nl.davidengelen.redsquare") return false
                val code = manifest.get("versionCode")?.asString ?: return false
                if (!code.matches(Regex("[0-9]{1,10}"))) return false
                val version = code.toLong()
                version in (installedVersion + 1)..2_100_000_000L
            } finally { connection.disconnect() }
        } catch (_: Exception) {
            // A review-server outage never blocks reading or wallet recovery.
            false
        }
    }
}

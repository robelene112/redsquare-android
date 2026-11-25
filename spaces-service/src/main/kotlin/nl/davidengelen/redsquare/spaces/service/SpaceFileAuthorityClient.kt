package nl.davidengelen.redsquare.spaces.service

import java.net.HttpURLConnection
import java.net.URI

/** Operator-supplied address of the pinned authority's permission endpoint. */
internal class SpaceFileAuthorityClient(origin: String) {
    private val endpoint = URI.create(origin.trimEnd('/') + "/v1/file-permission")
    init {
        val base = URI.create(origin)
        require(base.host != null && base.userInfo == null && base.rawQuery == null && base.rawFragment == null &&
            base.rawPath in setOf("", "/") && (base.scheme == "https" || base.scheme == "http" && base.host == "127.0.0.1"))
    }

    fun check(envelope: String): String {
        val request = envelope.toByteArray(Charsets.UTF_8)
        require(request.size in 1..16 * 1024)
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            check(connection.responseCode == 200) { "Storage authority did not answer" }
            val response = connection.inputStream.use { it.readNBytes(8193) }
            require(response.size in 1..8192)
            return response.decodeToString(throwOnInvalidSequence = true)
        } finally { connection.disconnect() }
    }
}

package nl.davidengelen.redsquare.saito

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Validated provider identity; arbitrary post URLs never become executable HTML. */
data class YouTubeVideo(val id: String) {
    init { require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) }
    val url: String get() = "https://www.youtube.com/watch?v=$id"
}

object RedSquareVideo {
    private val firstLink = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    /** Match vanilla's signed-link priority and first-link behavior. */
    fun youtube(text: String, link: String = ""): YouTubeVideo? {
        val source = link.trim().ifEmpty { firstLink.find(text)?.value?.trimEnd('.', ',', ';', '!', ')', ']', '}').orEmpty() }
        if (source.length > 8192) return null
        return try {
            val uri = URI(source)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme !in setOf("https", "http") || uri.rawUserInfo != null ||
                uri.port !in setOf(-1, if (scheme == "https") 443 else 80)) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val path = uri.path.orEmpty()
            val id = when {
                host == "youtu.be" && path.count { it == '/' } == 1 -> path.removePrefix("/")
                host in youtubeHosts && path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull {
                    it.substringBefore('=') == "v"
                }?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty()
                host in youtubeHosts && (path.startsWith("/shorts/") || path.startsWith("/live/")) ->
                    path.substringAfterLast('/').takeIf { path.count { it == '/' } == 2 }.orEmpty()
                else -> return null
            }
            if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) YouTubeVideo(id) else null
        } catch (_: Exception) { null }
    }
}

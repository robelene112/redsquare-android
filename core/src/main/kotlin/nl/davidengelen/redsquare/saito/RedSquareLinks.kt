package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.URI
import java.util.Base64

/** The target comes from the signed post; title/image hints are unsigned selected-node metadata. */
data class RedSquareLink(val url: String, val host: String, val caption: String, val stack: Boolean,
    val title: String?, val description: String?, val image: String?)

object RedSquareLinks {
    private val textUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun preview(post: FeedPost): RedSquareLink? {
        val target = if (post.link.isNotBlank()) web(post.link.trim()) else
            textUrl.findAll(post.text).mapNotNull { web(it.value.trimEnd('.', ',', ';', '!')) }.firstOrNull()
        target ?: return null
        val uri = URI(target)
        val properties = hints(post)?.get("link_properties")?.takeIf { it.isJsonObject }?.asJsonObject
        fun hint(vararg names: String, limit: Int): String? = names.firstNotNullOfOrNull { name ->
            properties?.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.trim()?.takeIf { it.isNotEmpty() && it.length <= limit }
        }
        val image = hint("og:image", limit = 128 * 1024)?.let { source ->
            val resolved = if (source.startsWith("data:", true)) source else runCatching { uri.resolve(source).toString() }.getOrNull()
            resolved?.let(RedSquareImages::safeSource)
        }
        // A represented standalone trailing URL belongs to the card; inline authored text stays verbatim.
        val lines = post.text.lines()
        val caption = if (lines.lastOrNull()?.trim() == target) lines.dropLast(1).joinToString("\n").trimEnd() else post.text
        return RedSquareLink(target, uri.host, caption,
            SaitoBackend.entries.any { it.domain.equals(uri.host, true) } && uri.normalize().path.startsWith("/stack/"),
            hint("saito:title", "og:title", limit = 512),
            hint("saito:description", "og:description", limit = 2048), image)
    }

    private fun web(value: String): String? = runCatching {
        require(value.length <= 4096 && value.none { it.isWhitespace() || it.isISOControl() })
        val uri = URI(value)
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        value
    }.getOrNull()

    private fun hints(post: FeedPost): JsonObject? = runCatching {
        val envelope = gson.fromJson(post.envelope, JsonObject::class.java)
        val encoded = envelope.get("opt").takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return null
        require(encoded.length <= 128 * 1024)
        gson.fromJson(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8), JsonObject::class.java)
    }.getOrNull()
}

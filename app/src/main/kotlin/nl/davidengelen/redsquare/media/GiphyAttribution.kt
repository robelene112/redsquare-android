package nl.davidengelen.redsquare.media

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Optional selected-message metadata, not a search catalogue or signed Saito payload. */
internal data class GiphyAttribution(val id: String, val title: String, val creator: String?,
    val creatorUrl: String?, val sourceUrl: String?) {
    fun toJson() = JsonObject().apply {
        addProperty("id", id); addProperty("title", title); addProperty("creator", creator)
        addProperty("creatorUrl", creatorUrl); addProperty("sourceUrl", sourceUrl)
    }

    companion object {
        /** Bad optional credits must not make authored text/images unreadable. */
        fun fromJson(value: JsonElement): GiphyAttribution? {
            if (!value.isJsonObject) return null
            val row = value.asJsonObject
            fun text(name: String) = row.get(name)?.takeIf {
                it.isJsonPrimitive && it.asJsonPrimitive.isString
            }?.asString?.takeIf { it.length <= 8192 && it.isNotBlank() }
            fun link(name: String): String? {
                val input = text(name) ?: return null
                val url = try { input.toHttpUrl() } catch (_: IllegalArgumentException) { return null }
                return input.takeIf { url.username.isEmpty() && url.password.isEmpty() }
            }
            val id = text("id")?.takeIf { it.length <= 256 } ?: return null
            val title = text("title") ?: return null
            return GiphyAttribution(id, title, text("creator"), link("creatorUrl"), link("sourceUrl"))
        }
    }
}

package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.URI
import java.util.Base64

/** An exact publication address. It carries no invitation, key or reading authority. */
data class SpaceArticleReference(val community: SpacePublicReference, val channel: String, val article: String) {
    init {
        require(channel.matches(Regex("[0-9a-f]{64}")) && article.matches(Regex("[0-9a-f]{128}")))
    }

    fun link(): String {
        val body = JsonObject().apply {
            addProperty("version", 1)
            addProperty("node", community.node)
            addProperty("service", community.serviceKey)
            addProperty("space", community.spaceId)
            addProperty("channel", channel)
            addProperty("article", article)
        }
        return "saito://spaces/article#" + Base64.getUrlEncoder().withoutPadding().encodeToString(body.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

        fun parse(link: String): SpaceArticleReference {
            require(link.length in 1..8192)
            val uri = URI(link)
            require(uri.scheme == "saito" && uri.host == "spaces" && uri.path == "/article" &&
                uri.rawQuery == null && uri.userInfo == null && uri.port == -1)
            val decoded = Base64.getUrlDecoder().decode(requireNotNull(uri.rawFragment))
            require(decoded.size <= 4096)
            val body = gson.fromJson(decoded.toString(Charsets.UTF_8), JsonObject::class.java)
            require(body.keySet() == setOf("version", "node", "service", "space", "channel", "article") && body.get("version").toString() == "1")
            fun text(key: String) = body.get(key).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            return SpaceArticleReference(SpacePublicReference(text("node"), text("service"), text("space")), text("channel"), text("article"))
        }
    }
}

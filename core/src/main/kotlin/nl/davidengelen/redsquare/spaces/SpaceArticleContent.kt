package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed interface SpaceArticleContent {
    data class Original(val title: String, val subtitle: String, val markdown: String) : SpaceArticleContent {
        init {
            require(title.isNotBlank() && title.length <= 250)
            require(subtitle.length <= 500 && markdown.isNotBlank())
            require(encode().toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Article exceeds the size limit" }
        }

        override fun toString() = "Space article"
    }

    fun encode(): String = when (this) {
        is Original -> JsonObject().apply {
            addProperty("version", 1)
            addProperty("type", "original")
            addProperty("title", title)
            addProperty("subtitle", subtitle)
            addProperty("markdown", markdown)
        }.toString()
    }

    companion object {
        const val MAX_BYTES = 64 * 1024

        fun decode(text: String): SpaceArticleContent {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val fields = requireNotNull(runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()) {
                "Invalid article"
            }
            fun string(name: String): String {
                val value = fields.get(name)
                require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                return value.asString
            }
            require(fields.get("version")?.toString() == "1" && string("type") == "original")
            require(fields.keySet() == setOf("version", "type", "title", "subtitle", "markdown"))
            return Original(string("title"), string("subtitle"), string("markdown"))
        }
    }
}

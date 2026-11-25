package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.StackLink

sealed interface SpaceArticleContent {
    data class Original(val title: String, val subtitle: String, val markdown: String) : SpaceArticleContent {
        init {
            require(title.isNotBlank() && title.length <= 250)
            require(subtitle.length <= 500 && markdown.isNotBlank())
            require(encode().toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Article exceeds the size limit" }
        }

        override fun toString() = "Space article"
    }

    data class PublicStack(val link: StackLink) : SpaceArticleContent {
        val url: String = "${link.backend.webUrl}/stack/${requireNotNull(link.author)}/${requireNotNull(link.signature)}"

        init { require(StackLink.parse(url) == link) }

        override fun toString() = "Shared public Stack article"
    }

    fun encode(): String = when (this) {
        is Original -> JsonObject().apply {
            addProperty("version", 1)
            addProperty("type", "original")
            addProperty("title", title)
            addProperty("subtitle", subtitle)
            addProperty("markdown", markdown)
        }.toString()
        is PublicStack -> JsonObject().apply {
            addProperty("version", 1)
            addProperty("type", "stack")
            addProperty("url", url)
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
            require(fields.get("version")?.toString() == "1")
            return when (string("type")) {
                "original" -> {
                    require(fields.keySet() == setOf("version", "type", "title", "subtitle", "markdown"))
                    Original(string("title"), string("subtitle"), string("markdown"))
                }
                "stack" -> {
                    require(fields.keySet() == setOf("version", "type", "url"))
                    val url = string("url")
                    PublicStack(requireNotNull(StackLink.parse(url))).also { require(it.url == url) }
                }
                else -> throw IllegalArgumentException("Unknown article format")
            }
        }
    }
}

data class SpaceArticleDraft(val title: String = "", val subtitle: String = "", val markdown: String = "",
    val sourceMode: Boolean = false) {
    fun content() = SpaceArticleContent.Original(title, subtitle, markdown)
    fun fitsStorage(): Boolean = runCatching { bytes() }.isSuccess

    internal fun bytes(): ByteArray = JsonObject().apply {
        addProperty("version", 1)
        addProperty("title", title)
        addProperty("subtitle", subtitle)
        addProperty("markdown", markdown)
        addProperty("source_mode", sourceMode)
    }.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= SpaceArticleContent.MAX_BYTES) }

    override fun toString() = "Space article draft"

    internal companion object {
        fun restore(bytes: ByteArray): SpaceArticleDraft {
            require(bytes.size <= SpaceArticleContent.MAX_BYTES)
            val fields = JsonParser.parseString(bytes.decodeToString(throwOnInvalidSequence = true)).asJsonObject
            require(fields.keySet() == setOf("version", "title", "subtitle", "markdown", "source_mode") &&
                fields.get("version").toString() == "1" && fields.getAsJsonPrimitive("source_mode").isBoolean)
            fun text(name: String): String = fields.getAsJsonPrimitive(name).also { require(it.isString) }.asString
            return SpaceArticleDraft(text("title"), text("subtitle"), text("markdown"), fields.get("source_mode").asBoolean)
        }
    }
}

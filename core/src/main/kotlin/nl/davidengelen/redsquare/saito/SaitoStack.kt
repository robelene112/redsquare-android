package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

data class StackArticle(val id: String, val revision: String, val author: String, val title: String,
    val excerpt: String, val markdown: String, val cover: String?, val createdAt: Long, val envelope: String,
    val images: Map<String, String> = emptyMap())

data class StackArticlePage(val articles: List<StackArticle>, val before: Long?, val rowCount: Int, val ignored: Int)

/** Public Stack articles, authenticated before any presentation metadata is used. */
object SaitoStack {
    fun decode(envelope: String): StackArticle {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.string("module") == "Stack" && body.string("request") == "create stack post request")
        // Restricted content belongs to Stack's access-script protocol, never a simulated local unlock.
        require(!body.has("access_hash") && !body.has("access_script"))
        val data = body.getAsJsonObject("data")
        require(data.string("type") == "stack_post")
        val title = data.string("title").also { require(it.isNotBlank() && it.length <= 10_000) }
        val markdown = data.string("content")
        val parent = data.text("parent_id").also { require(it.isEmpty() || signature.matches(it)) }
        val cover = raster(data.text("image")) ?: raster(data.text("imageUrl"))
        val images = data.get("images")?.takeIf { it.isJsonArray }?.asJsonArray?.take(100)?.mapNotNull { element ->
            try {
                val image = element.asJsonObject
                val id = image.string("id").also { require(it.length in 1..200) }
                RedSquareImages.safeSource("data:${image.string("mime")};base64,${image.string("data")}")?.let { id to it }
            } catch (_: Exception) { null }
        }?.toMap().orEmpty()
        return StackArticle(parent.ifEmpty { signed.id }, signed.id, signed.author, title,
            data.text("excerpt"), markdown, cover, signed.createdAt, envelope, images)
    }

    fun readArchive(response: String): StackArticlePage {
        val page = readArchiveRows(response, ::decode, { true }, { it.revision })
        return StackArticlePage(newest(page.entries), page.oldestCreatedAt, page.rowCount, page.ignored)
    }

    /** Revision identity includes its authenticated author; another signer cannot overwrite a story. */
    fun newest(articles: List<StackArticle>): List<StackArticle> = articles
        .groupBy { it.author to it.id }.values.map { versions ->
            versions.maxWith(compareBy<StackArticle> { it.createdAt }.thenBy { it.revision })
        }.sortedByDescending { it.createdAt }

    fun archiveRequest(before: Long, author: String? = null): String {
        require(before >= 0 && (author == null || SaitoWallet.isPublicKey(author)))
        return JsonObject().apply {
            addProperty("request", "archive")
            add("data", JsonObject().apply {
                addProperty("request", "load"); addProperty("field1", "Stack"); addProperty("field4", "stack:post")
                addProperty("created_earlier_than", before); addProperty("limit", PAGE_SIZE)
                if (author != null) addProperty("field2", author)
            })
        }.toString()
    }

    fun articleRequest(author: String, signature: String): String {
        require(SaitoWallet.isPublicKey(author) && this.signature.matches(signature))
        return JsonObject().apply {
            addProperty("request", "archive")
            add("data", JsonObject().apply {
                addProperty("request", "load"); addProperty("field1", "Stack"); addProperty("field4", "stack:post")
                addProperty("field2", author); addProperty("sig", signature); addProperty("limit", 1)
            })
        }.toString()
    }

    /** Filter before collapsing revisions: an explicit article URL must not become another revision. */
    fun readArticle(response: String, author: String, signature: String): StackArticle? {
        require(SaitoWallet.isPublicKey(author) && this.signature.matches(signature))
        return readArchiveRows(response, ::decode, { it.author == author && it.revision == signature },
            { it.revision }).entries.singleOrNull()
    }

    fun articleUrl(article: StackArticle, backend: SaitoBackend): String =
        "${backend.webUrl}/stack/${article.author}/${article.revision}"

    private fun JsonObject.string(name: String): String {
        val value = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }
    private fun JsonObject.text(name: String): String = if (!has(name) || get(name).isJsonNull) "" else string(name)
    private fun raster(value: String): String? {
        RedSquareImages.safeSource(value)?.let { return it }
        // The web editor also stores a raw base64 cover without a data-URI prefix.
        return value.takeIf { it.length in 4..1_400_000 && base64.matches(it) }
            ?.let { RedSquareImages.safeSource("data:image/png;base64,$it") }
    }
    private val signature = Regex("[0-9a-f]{128}")
    private val base64 = Regex("[A-Za-z0-9+/]+={0,2}")
    // Stack covers are embedded in the signed envelope. Ten avoids oversized multi-story responses.
    const val PAGE_SIZE = 10
}

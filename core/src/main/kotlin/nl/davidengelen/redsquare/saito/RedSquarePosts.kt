package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Compose compatible content before signing. An independently signed original is never rewritten. */
object RedSquarePosts {
    const val MAX_IMAGE_CHARS = 512 * 1024

    fun message(text: String, images: List<String> = emptyList(), parent: FeedPost? = null, quote: FeedPost? = null): JsonObject {
        require(parent == null || quote == null)
        require(images.size <= RedSquareImages.MAX_COUNT)
        require(images.all { it.length <= MAX_IMAGE_CHARS && RedSquareImages.safeSource(it) == it })
        return JsonObject().apply {
            addProperty("module", "RedSquare")
            addProperty("request", "create tweet")
            add("data", JsonObject().apply {
                addProperty("text", text)
                add("images", JsonArray().apply { images.forEach(::add) })
                parent?.let { addProperty("parent_id", it.id); addProperty("thread_id", it.threadId) }
                quote?.let { original ->
                    val verified = RedSquareFeed.decode(original.envelope)
                    require(verified.id == original.id)
                    addProperty("signature", verified.id)
                    addProperty("retweet_tx", verified.envelope)
                    add("embedded", JsonObject().apply {
                        addProperty("signature", verified.id)
                        addProperty("publicKey", verified.author)
                        addProperty("created_at", verified.createdAt)
                        addProperty("text", verified.text)
                        val originalData = SaitoEnvelope.decode(verified.envelope).body.getAsJsonObject("data")
                        add("images", originalData.get("images")?.takeIf { it.isJsonArray }?.deepCopy() ?: JsonArray())
                    })
                }
            })
        }
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Original signed content; archive metadata and chain inclusion are separate assertions. */
data class FeedPost(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: Long,
    val parentId: String,
    val threadId: String,
    val envelope: String,
)

data class ArchiveFeedPage(val posts: List<FeedPost>, val oldestCreatedAt: Long?, val rowCount: Int, val ignored: Int)

object RedSquareFeed {
    fun readArchive(response: String): ArchiveFeedPage = readPosts(response) { it.parentId.isEmpty() }

    fun readConversation(response: String, threadId: String): ArchiveFeedPage =
        readPosts(response) { it.threadId == threadId }

    fun readAuthor(response: String, author: String): ArchiveFeedPage =
        readPosts(response) { it.author == author }

    fun readTarget(response: String, signature: String): ArchiveFeedPage =
        readPosts(response) { it.id == signature }

    private fun readPosts(response: String, accepts: (FeedPost) -> Boolean): ArchiveFeedPage {
        require(response.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024)
        val rows = gson.fromJson(response, com.google.gson.JsonArray::class.java)
            ?: throw IllegalArgumentException("Missing archive response")
        require(rows.size() <= 100)
        var oldest: Long? = null
        var ignored = 0
        val posts = rows.mapNotNull { row ->
            try {
                val item = row.asJsonObject
                val cursor = item.get("created_at")?.asString?.toLongOrNull()?.takeIf { it >= 0 }
                if (cursor != null) oldest = minOf(oldest ?: cursor, cursor)
                decode(item.string("tx")).takeIf(accepts).also {
                    if (it == null) ignored++
                }
            } catch (_: Exception) { ignored++; null }
        }
        return ArchiveFeedPage(posts.distinctBy { it.id }, oldest, rows.size(), ignored)
    }

    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    /** Reads web Archive envelopes, verifies the original bytes, then exposes post content. */
    fun decode(envelope: String): FeedPost {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.string("module") == "RedSquare" && body.string("request") == "create tweet")
        val data = body.getAsJsonObject("data") ?: throw IllegalArgumentException("Missing post data")
        val parent = data.optionalString("parent_id")
        val thread = data.optionalString("thread_id").ifEmpty { signed.id }
        return FeedPost(signed.id, signed.author, data.optionalString("text"), signed.createdAt, parent, thread, envelope)
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }

    private fun JsonObject.optionalString(key: String): String = if (!has(key) || get(key).isJsonNull) "" else string(key)
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

/** Original signed content; archive metadata and chain inclusion are separate assertions. */
data class FeedPost(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: Long,
    val parentId: String,
    val threadId: String,
    val envelope: String,
    val recipients: List<String>,
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
        val page = readArchiveRows(response, ::decode, accepts) { it.id }
        return ArchiveFeedPage(page.entries, page.oldestCreatedAt, page.rowCount, page.ignored)
    }

    private val signaturePattern = Regex("[0-9a-f]{128}")

    /** Reads web Archive envelopes, verifies the original bytes, then exposes post content. */
    fun decode(envelope: String): FeedPost {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.string("module") == "RedSquare" && body.string("request") == "create tweet")
        val data = body.getAsJsonObject("data") ?: throw IllegalArgumentException("Missing post data")
        val parent = data.optionalString("parent_id")
        val thread = data.optionalString("thread_id").ifEmpty { signed.id }
        require(parent.isEmpty() || parent.matches(signaturePattern)) { "Invalid parent signature" }
        require(thread.matches(signaturePattern)) { "Invalid thread signature" }
        return FeedPost(signed.id, signed.author, data.optionalString("text"), signed.createdAt, parent, thread, envelope, signed.recipients)
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }

    private fun JsonObject.optionalString(key: String): String = if (!has(key) || get(key).isJsonNull) "" else string(key)
}

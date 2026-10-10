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
    val quoted: FeedPost? = null,
    val quoteTarget: String = quoted?.id.orEmpty(),
)

data class ArchiveFeedPage(val posts: List<FeedPost>, val oldestCreatedAt: Long?, val rowCount: Int, val ignored: Int)

object RedSquareFeed {
    /** Ordinary reposts are valid interactions, but do not create commentary cards. */
    fun readCurrentQuotes(response: String): ArchiveFeedPage {
        data class QuoteRow(val id: String, val post: FeedPost?)
        val page = readArchiveRows(response, { envelope ->
            val action = RedSquareActions.decode(envelope)
            require(action.kind == RedSquareActionKind.REPOST)
            val data = SaitoEnvelope.decode(envelope).body.getAsJsonObject("data")
            val hasCommentary = data.optionalString("text").isNotBlank() || data.getAsJsonArray("images")?.size()?.let { it > 0 } == true
            QuoteRow(action.id, if (hasCommentary) decode(envelope) else null)
        }, { true }, { it.id })
        return ArchiveFeedPage(page.entries.mapNotNull { it.post }, page.oldestCreatedAt, page.rowCount, page.ignored)
    }

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
    fun decode(envelope: String): FeedPost = decode(envelope, true)

    private fun decode(envelope: String, includeOriginal: Boolean): FeedPost {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.string("module") == "RedSquare")
        val request = body.string("request")
        require(request == "create tweet" || request == "retweet")
        val data = body.getAsJsonObject("data") ?: throw IllegalArgumentException("Missing post data")
        val text = data.optionalString("text")
        if (request == "retweet") require(text.isNotBlank() || data.getAsJsonArray("images")?.size()?.let { it > 0 } == true) {
            "Ordinary repost is not a quote post"
        }
        val target = when {
            data.has("retweet_tx") || request == "retweet" -> data.string("signature")
            data.has("embedded") -> data.getAsJsonObject("embedded").string("signature")
            else -> ""
        }
        require(target.isEmpty() || target.matches(signaturePattern)) { "Invalid quote target" }
        val parent = data.optionalString("parent_id")
        val thread = data.optionalString("thread_id").ifEmpty { signed.id }
        require(parent.isEmpty() || parent.matches(signaturePattern)) { "Invalid parent signature" }
        require(thread.matches(signaturePattern)) { "Invalid thread signature" }
        val original = if (includeOriginal && data.has("retweet_tx")) {
            try { decode(data.string("retweet_tx"), false).also {
                require(data.string("signature") == it.id) { "Conflicting quote reference" }
                data.getAsJsonObject("embedded")?.let { embedded ->
                    require(embedded.string("signature") == it.id && embedded.string("publicKey") == it.author &&
                        embedded.string("text") == it.text) { "Conflicting quote presentation" }
                }
            } } catch (_: Exception) { null }
        } else null
        return FeedPost(signed.id, signed.author, text, signed.createdAt, parent, thread, envelope, signed.recipients, original, target)
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }

    private fun JsonObject.optionalString(key: String): String = if (!has(key) || get(key).isJsonNull) "" else string(key)
}

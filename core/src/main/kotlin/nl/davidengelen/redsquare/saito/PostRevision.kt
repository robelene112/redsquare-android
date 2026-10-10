package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Verified public proof retained separately from bounded view caches. Never injects unseen posts. */
class PostRevision private constructor(val post: FeedPost, val deletion: AuthorAction?) {
    fun apply(posts: List<FeedPost>): List<FeedPost> = posts.mapNotNull { current ->
        if (current.id != post.id) current
        else if (deletion != null) null
        else RedSquareFeed.newest(listOf(current, post)).single()
    }

    fun encode(): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("post", post.envelope)
        deletion?.let { addProperty("delete", it.envelope) }
    }.toString()

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

        fun observed(envelope: String): PostRevision = PostRevision(RedSquareFeed.decode(envelope), null)

        fun deleted(originalEnvelope: String, commandEnvelope: String): PostRevision {
            val command = requireNotNull(RedSquareAuthorActions.deletion(originalEnvelope, commandEnvelope))
            return PostRevision(RedSquareFeed.decode(originalEnvelope), command)
        }

        fun restore(encoded: String): PostRevision {
            val record = gson.fromJson(encoded, JsonObject::class.java)
            require(record.get("version").asInt == 1)
            val original = record.get("post").asJsonPrimitive
            require(original.isString)
            return if (record.has("delete")) {
                val command = record.get("delete").asJsonPrimitive
                require(command.isString)
                deleted(original.asString, command.asString)
            } else observed(original.asString)
        }
    }
}

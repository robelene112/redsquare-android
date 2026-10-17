package nl.davidengelen.redsquare.notifications

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Routing hints share the recipient-proof coordinates; originals remain the authority for Chat content. */
internal data class ChatPushReference(val kind: Kind, val reference: SocialPushReference) {
    enum class Kind(val category: String) { PRIVATE("private-chats"), GROUP("group-chats") }

    companion object {
        fun parse(payload: String): ChatPushReference? {
            require(payload.toByteArray(Charsets.UTF_8).size <= 1024)
            val body = requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(payload, JsonObject::class.java))
            val value = body.get("kind") ?: return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            val kind = when (value.asString) {
                "private-chat" -> Kind.PRIVATE
                "group-chat" -> Kind.GROUP
                else -> throw IllegalArgumentException("Unsupported Chat wake kind")
            }
            body.remove("kind")
            return ChatPushReference(kind, SocialPushReference.parse(body.toString()))
        }
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

/** Original signed metadata for the existing unencrypted Chat reaction protocol. */
data class PrivateChatLikeEvent(val id: String, val author: String, val target: String,
    val targetAuthor: String, val createdAt: Long, val envelope: String)

object PrivateChatLike {
    fun create(wallet: SaitoWallet, contact: String, target: PrivateChatMessage, timestamp: Long): SaitoTransaction {
        require(target.conversationId == PrivateChat.conversationId(wallet.publicKey, contact))
        require(target.author in setOf(wallet.publicKey, contact))
        require(target.id.matches(Regex("[0-9a-f]{128}")))
        val body = JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat like")
            addProperty("group_id", target.conversationId); addProperty("sender", wallet.publicKey)
            addProperty("signature", target.id); addProperty("mentioned", target.author)
        }
        return SaitoTransaction.create(wallet, body, timestamp, listOf(contact))
    }

    fun read(wallet: SaitoWallet, contact: String, envelope: String): PrivateChatLikeEvent {
        val signed = SaitoEnvelope.decodePayload(envelope)
        val participants = setOf(wallet.publicKey, contact)
        require(signed.author in participants && signed.recipients.containsAll(participants))
        require(signed.body.isJsonObject)
        val body = signed.body.asJsonObject
        fun string(name: String): String = body.get(name).also {
            require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
        }.asString
        require(string("module") == "Chat" && string("request") == "chat like")
        require(string("group_id") == PrivateChat.conversationId(wallet.publicKey, contact))
        require(string("sender") == signed.author)
        val target = string("signature").also { require(it.matches(Regex("[0-9a-f]{128}"))) }
        val targetAuthor = string("mentioned").also { require(it in participants) }
        return PrivateChatLikeEvent(signed.id, signed.author, target, targetAuthor, signed.createdAt, envelope)
    }
}

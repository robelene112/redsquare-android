package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

enum class ChatGroupRole { ADMIN, MEMBER }

class NamedChatGroup internal constructor(val id: String, val name: String,
    val memberRoles: Map<String, ChatGroupRole>, val creationEnvelope: String)

/** Compatible unencrypted named-group controls; participants sign with their own wallet. */
object GroupChat {
    /** Construction follows an explicit joining choice; unsigned claims are not membership proof. */
    fun join(wallet: SaitoWallet, invitation: GroupInvitation, timestamp: Long): SaitoTransaction {
        require(wallet.publicKey != invitation.id)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat join")
            addProperty("group_id", invitation.id)
            addProperty("invited_by", invitation.sender)
        }
        return SaitoTransaction.createTo(wallet, body, timestamp, invitation.id,
            listOf(invitation.admin, invitation.sender))
    }

    /** Authenticate available creation without implying the reader has joined. */
    fun readCreation(envelope: String): NamedChatGroup {
        val signed = SaitoEnvelope.decode(envelope)
        val body = signed.body
        require(body.requiredString("module") == "Chat" && body.requiredString("request") == "chat group")
        require(body.requiredString("admin") == signed.author)
        require(signed.recipients.firstOrNull() == signed.author)
        require(signed.recipients.all(SaitoWallet::isPublicKey))
        val id = body.requiredString("id")
        val name = body.requiredString("name")
        require(SaitoWallet.isPublicKey(id) && id !in signed.recipients)
        require(name.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(name))
        val members = signed.recipients.distinct().associateWith {
            if (it == signed.author) ChatGroupRole.ADMIN else ChatGroupRole.MEMBER
        }
        return NamedChatGroup(id, name, java.util.Collections.unmodifiableMap(members), envelope)
    }

    fun create(wallet: SaitoWallet, groupId: String, name: String, invitees: List<String>,
        timestamp: Long): SaitoTransaction {
        require(SaitoWallet.isPublicKey(groupId) && groupId != wallet.publicKey)
        require(name.isNotBlank())
        require(invitees.all { SaitoWallet.isPublicKey(it) && it != groupId })
        val recipients = invitees.distinct().filter { it != wallet.publicKey }
        require(recipients.size <= 254)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat group")
            addProperty("name", name)
            addProperty("admin", wallet.publicKey)
            addProperty("id", groupId)
        }
        return SaitoTransaction.create(wallet, body, timestamp, recipients)
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
}

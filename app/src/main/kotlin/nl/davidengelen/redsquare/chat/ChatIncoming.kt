package nl.davidengelen.redsquare.chat

import android.content.Context
import nl.davidengelen.redsquare.saito.*

/** Route authenticated originals independently of the visible Chat mode, retaining before acknowledgement.
 * Return the private counterparty, or null for a named-group event.
 */
internal fun receiveChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    bytes: ByteArray): String? {
    val envelope = try { GroupChat.originalEnvelope(bytes) }
        catch (_: IllegalArgumentException) { PrivateChat.originalEnvelope(bytes) }
    val signed = SaitoEnvelope.decodePayload(envelope)
    if (signed.body.isJsonObject) {
        val body = signed.body.asJsonObject
        val id = body.get(if (body.get("request") == com.google.gson.JsonPrimitive("chat group")) "id" else "group_id")
        if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString && SaitoWallet.isPublicKey(id.asString)) {
            GroupChatStore(context, wallet, backend).receive(envelope)
            return null
        }
    }
    if (signed.body.isJsonObject) {
        val candidates = if (signed.author != wallet.publicKey) listOf(signed.author)
            else signed.recipients.distinct().filter { it != wallet.publicKey }
        val group = signed.body.asJsonObject.get("group_id")
        require(group != null && group.isJsonPrimitive && group.asJsonPrimitive.isString)
        val contact = candidates.single { PrivateChat.conversationId(wallet.publicKey, it) == group.asString }
        ChatReactionStore(context, wallet, backend, contact).receive(envelope)
        return contact
    }
    val received = PrivateChat.recover(wallet, envelope)
    ChatHistoryStore(context, wallet, backend, received.counterparty).save(listOf(envelope))
    return received.counterparty
}

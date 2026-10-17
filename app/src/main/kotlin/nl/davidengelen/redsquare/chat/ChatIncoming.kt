package nl.davidengelen.redsquare.chat

import android.content.Context
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.notifications.NotificationReceiptLedger

/** Route authenticated originals independently of the visible Chat mode, retaining before acknowledgement.
 * Return the private counterparty, or null for a named-group event.
 */
internal fun receiveChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    bytes: ByteArray, endpoint: String = backend.peerUrl): String? {
    val envelope = try { GroupChat.originalEnvelope(bytes) }
        catch (_: IllegalArgumentException) { PrivateChat.originalEnvelope(bytes) }
    val signed = SaitoEnvelope.decodePayload(envelope)
    if (signed.body.isJsonObject) {
        val body = signed.body.asJsonObject
        val id = body.get(if (body.get("request") == com.google.gson.JsonPrimitive("chat group")) "id" else "group_id")
        if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString && SaitoWallet.isPublicKey(id.asString)) {
            val message = synchronized(SaitoLocalState.lock) {
                val store = GroupChatStore(context, wallet, backend)
                val ordinaryMessage = body.get("module") == com.google.gson.JsonPrimitive("Chat") &&
                    body.get("request") == com.google.gson.JsonPrimitive("chat message")
                val seen = !ordinaryMessage || store.load(id.asString)?.messages?.any { it.id == signed.id } == true
                val retained = store.receive(envelope)
                if (!seen && retained.group.memberRoles[wallet.publicKey]?.active == true)
                    retained.messages.singleOrNull { it.id == signed.id } else null
            }
            message?.let { notifyGroupChat(context, wallet, backend, it, endpoint) }
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
    retainPrivateChat(context, wallet, backend, received, endpoint)
    return received.counterparty
}

/** Ordinary and background delivery commit pending state with history before optional publication. */
internal fun retainPrivateChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    received: ReceivedPrivateChat, endpoint: String): ChatNotificationResult = synchronized(SaitoLocalState.lock) {
    val history = ChatHistoryStore(context, wallet, backend, received.counterparty)
    val message = received.message
    if (!history.retainIncoming(received.envelope)) return@synchronized ChatNotificationResult.SUPPRESSED
    // History and pending eligibility are durable before optional receipt bookkeeping can fail.
    try {
        val ledger = NotificationReceiptLedger(context, wallet.publicKey, backend, message.id,
            NotificationReceiptLedger.Domain.PRIVATE_CHAT)
        val now = System.currentTimeMillis()
        if (!ledger.prepare(now)) {
            history.completeAlert(message.id)
            return@synchronized ChatNotificationResult.SUPPRESSED
        }
        val outcome = notifyPrivateChat(context, wallet, backend, message, endpoint)
        if (outcome == ChatNotificationResult.PUBLISHED) {
            ledger.recordPublication(now)
            history.completeAlert(message.id)
        }
        outcome
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

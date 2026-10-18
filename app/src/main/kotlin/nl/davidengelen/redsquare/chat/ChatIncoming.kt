package nl.davidengelen.redsquare.chat

import android.content.Context
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.notifications.NotificationReceiptLedger
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls

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
            val ordinaryMessage = body.get("module") == com.google.gson.JsonPrimitive("Chat") &&
                body.get("request") == com.google.gson.JsonPrimitive("chat message")
            if (ordinaryMessage) retainGroupChat(context, wallet, backend, envelope, endpoint)
            else GroupChatStore(context, wallet, backend).receive(envelope)
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
    received: ReceivedPrivateChat, endpoint: String,
    retryPublication: Boolean = false): ChatNotificationResult = synchronized(SaitoLocalState.lock) {
    val history = ChatHistoryStore(context, wallet, backend, received.counterparty)
    val message = received.message
    if (!history.retainIncoming(received.envelope)) return@synchronized ChatNotificationResult.SUPPRESSED
    // History and pending eligibility are durable before optional receipt bookkeeping can fail.
    try {
        publishPendingChatAlert(NotificationReceiptLedger(context, wallet.publicKey, backend, message.id,
            NotificationReceiptLedger.Domain.PRIVATE_CHAT), retryPublication,
            completeAlert = { history.completeAlert(message.id) }) {
            notifyPrivateChat(context, wallet, backend, message, endpoint)
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

/** Ordinary and background group delivery share the history-owned pending decision. */
internal fun retainGroupChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    envelope: String, endpoint: String,
    retryPublication: Boolean = false): ChatNotificationResult = synchronized(SaitoLocalState.lock) {
    val history = GroupChatStore(context, wallet, backend)
    val message = history.retainIncoming(envelope) ?: return@synchronized ChatNotificationResult.SUPPRESSED
    try {
        val silenced = try {
            val policy = AndroidPersonalControls(context, wallet.publicKey).load()
            policy.hidesSocial(message.author) || message.groupId in policy.mutedConversations
        } catch (_: Exception) {
            // The guarded notifier still requires readable policy. Let the shared
            // publication path preserve ordinary completion and bookkeeping retry precedence.
            false
        }
        if (silenced) {
            history.completeAlert(message.groupId, message.id)
            return@synchronized ChatNotificationResult.SUPPRESSED
        }
        publishPendingChatAlert(NotificationReceiptLedger(context, wallet.publicKey, backend, message.id,
            NotificationReceiptLedger.Domain.GROUP_CHAT), retryPublication,
            completeAlert = { history.completeAlert(message.groupId, message.id) }) {
            notifyGroupChat(context, wallet, backend, message, endpoint)
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

/** Both Chat histories own eligibility; their publication and completion policy is identical. */
private fun publishPendingChatAlert(ledger: NotificationReceiptLedger, retryPublication: Boolean,
    completeAlert: () -> Unit, publish: () -> ChatNotificationResult): ChatNotificationResult {
    val now = System.currentTimeMillis()
    if (!ledger.prepare(now)) {
        completeAlert()
        return ChatNotificationResult.SUPPRESSED
    }
    val outcome = publish()
    if (outcome == ChatNotificationResult.PUBLISHED) ledger.recordPublication(now)
    // Deliberate suppression stays seen after unmute. Ordinary Chat also keeps its
    // no-retrospective-alert policy after notifier errors. Bookkeeping exceptions
    // escape to the caller, preserving independently retained pending eligibility.
    if (outcome != ChatNotificationResult.RETRY || !retryPublication) completeAlert()
    return outcome
}

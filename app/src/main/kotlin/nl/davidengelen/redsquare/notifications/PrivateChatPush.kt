package nl.davidengelen.redsquare.notifications

import android.content.Context
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.retainPrivateChat
import nl.davidengelen.redsquare.chat.ChatNotificationResult
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

/** Gateway hints never replace original signature, participants, inner-module and local-decryption checks. */
internal object PrivateChatPush {
    /** Known retained work can recover without a still-live gateway copy. */
    fun resume(context: Context, wake: String, endpoint: String?): ChatNotificationResult? = try {
        synchronized(SaitoLocalState.lock) {
            val scope = PrivateChatWakeRoute.scope(context, wake) ?: return@synchronized null
            val wallet = AndroidWalletStore(context).loadExisting()
            val backend = AndroidBackendStore(context).load()
            if (scope.owner != wallet.publicKey || scope.backend != backend)
                return@synchronized ChatNotificationResult.SUPPRESSED
            val route = PrivateChatWakeRoute(context, scope.owner, backend, wake)
            val entry = route.load() ?: return@synchronized null
            val history = ChatHistoryStore(context, wallet, backend, entry.counterparty)
            val envelope = history.retainedOriginal(entry.signature) ?: return@synchronized null
            if (!ChatPushSettings(context, wallet.publicKey).enabled()) {
                history.completeAlert(entry.signature); route.remove()
                return@synchronized ChatNotificationResult.SUPPRESSED
            }
            receive(context, SocialPushReference(wallet.publicKey, backend, entry.signature), envelope,
                endpoint ?: backend.peerUrl, wake)
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }

    fun receive(context: Context, reference: SocialPushReference, envelope: String,
        endpoint: String, wake: String): ChatNotificationResult = try {
        synchronized(SaitoLocalState.lock) {
            val wallet = AndroidWalletStore(context).loadExisting()
            if (wallet.publicKey != reference.owner || AndroidBackendStore(context).load() != reference.backend ||
                !ChatPushSettings(context, reference.owner).enabled()) return@synchronized ChatNotificationResult.SUPPRESSED
            // This pure validation/decryption boundary has no persistence operations.
            // Unsupported encrypted candidates are terminal; storage failures below remain retryable.
            val received = try { PrivateChat.recover(wallet, envelope) }
                catch (_: Exception) { return@synchronized ChatNotificationResult.SUPPRESSED }
            val message = received.message
            val now = System.currentTimeMillis()
            if (message.id != reference.signature || message.author == wallet.publicKey ||
                now < message.createdAt || now - message.createdAt > 7 * 24 * 60 * 60 * 1000L)
                return@synchronized ChatNotificationResult.SUPPRESSED
            val route = PrivateChatWakeRoute(context, wallet.publicKey, reference.backend, wake)
            if (message.author in AndroidPersonalControls(context, reference.owner).load().blocked) {
                // A deliberate policy decision also completes an earlier retained pending receipt.
                ChatHistoryStore(context, wallet, reference.backend, received.counterparty).completeAlert(message.id)
                route.remove()
                return@synchronized ChatNotificationResult.SUPPRESSED
            }
            route.remember(wallet, received, now)
            val outcome = retainPrivateChat(context, wallet, reference.backend, received, endpoint, retryPublication = true)
            if (outcome != ChatNotificationResult.RETRY) route.remove()
            outcome
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

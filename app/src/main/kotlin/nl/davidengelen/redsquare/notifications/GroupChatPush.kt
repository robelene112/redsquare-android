package nl.davidengelen.redsquare.notifications

import android.content.Context
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.ChatNotificationResult
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.chat.retainGroupChat
import nl.davidengelen.redsquare.saito.*

/** A signed group original can extend known history; it cannot establish the receiving wallet's membership. */
internal object GroupChatPush {
    /** Resume a verified group original without requiring the gateway's retained copy. */
    fun resume(context: Context, wake: String, endpoint: String?): ChatNotificationResult? = try {
        synchronized(SaitoLocalState.lock) {
            val scope = ChatWakeRoute.scope(context, wake, ChatPushReference.Kind.GROUP) ?: return@synchronized null
            val wallet = AndroidWalletStore(context).loadExisting()
            val backend = AndroidBackendStore(context).load()
            if (scope.owner != wallet.publicKey || scope.backend != backend)
                return@synchronized ChatNotificationResult.SUPPRESSED
            val route = ChatWakeRoute(context, scope.owner, backend, wake, ChatPushReference.Kind.GROUP)
            val entry = route.load() ?: return@synchronized null
            val history = GroupChatStore(context, wallet, backend)
            val envelope = history.retainedOriginal(entry.conversation, entry.signature) ?: return@synchronized null
            if (!ChatPushSettings(context, wallet.publicKey).enabled()) {
                history.completeAlert(entry.conversation, entry.signature); route.remove()
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
            val signed = try {
                SaitoEnvelope.decode(envelope).also {
                    require(it.body.get("module") == com.google.gson.JsonPrimitive("Chat") &&
                        it.body.get("request") == com.google.gson.JsonPrimitive("chat message"))
                    val group = it.body.get("group_id")
                    require(group != null && group.isJsonPrimitive && group.asJsonPrimitive.isString &&
                        SaitoWallet.isPublicKey(group.asString) && group.asString != wallet.publicKey)
                }
            } catch (_: Exception) { return@synchronized ChatNotificationResult.SUPPRESSED }
            val now = System.currentTimeMillis()
            if (signed.id != reference.signature || signed.author == wallet.publicKey ||
                now < signed.createdAt || now - signed.createdAt > 7 * 24 * 60 * 60 * 1000L)
                return@synchronized ChatNotificationResult.SUPPRESSED
            val store = GroupChatStore(context, wallet, reference.backend)
            val previous = store.load(signed.body.get("group_id").asString) ?: return@synchronized ChatNotificationResult.SUPPRESSED
            if (previous.group.memberRoles[wallet.publicKey]?.active != true)
                return@synchronized ChatNotificationResult.SUPPRESSED
            val message = try { GroupChat.readMessage(previous.group, envelope) }
                catch (_: Exception) { return@synchronized ChatNotificationResult.SUPPRESSED }
            val route = ChatWakeRoute(context, wallet.publicKey, reference.backend, wake, ChatPushReference.Kind.GROUP)
            route.remember(wallet, ChatWakeRoute.Entry(message.id, message.groupId,
                message.createdAt + ChatWakeRoute.LIFETIME), now)
            val outcome = retainGroupChat(context, wallet, reference.backend, envelope, endpoint, retryPublication = true)
            if (outcome != ChatNotificationResult.RETRY) route.remove()
            outcome
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

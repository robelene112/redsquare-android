package nl.davidengelen.redsquare.notifications

import android.content.Context
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.ChatNotificationResult
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.chat.notifyGroupChat
import nl.davidengelen.redsquare.saito.*

/** A signed group original can extend known history; it cannot establish the receiving wallet's membership. */
internal object GroupChatPush {
    fun receive(context: Context, reference: SocialPushReference, envelope: String,
        endpoint: String): ChatNotificationResult = try {
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
            val seen = previous.messages.any { it.id == message.id }
            store.receive(envelope)
            if (seen) return@synchronized ChatNotificationResult.SUPPRESSED
            notifyGroupChat(context, wallet, reference.backend, message, endpoint)
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

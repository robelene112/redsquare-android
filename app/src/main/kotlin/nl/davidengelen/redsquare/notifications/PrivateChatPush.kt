package nl.davidengelen.redsquare.notifications

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.retainPrivateChat
import nl.davidengelen.redsquare.chat.ChatNotificationResult
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

/** Gateway hints never replace original signature, participants, inner-module and local-decryption checks. */
internal object PrivateChatPush {
    /** Private and social routing coordinates share the same validated recipient-proof reference schema. */
    fun reference(payload: String): SocialPushReference? {
        require(payload.toByteArray(Charsets.UTF_8).size <= 1024)
        val body = requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(payload, JsonObject::class.java))
        val kind = body.get("kind") ?: return null
        require(kind.isJsonPrimitive && kind.asJsonPrimitive.isString && kind.asString == "private-chat")
        body.remove("kind")
        return SocialPushReference.parse(body.toString())
    }

    fun receive(context: Context, reference: SocialPushReference, envelope: String,
        endpoint: String): ChatNotificationResult = try {
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
            if (message.author in AndroidPersonalControls(context, reference.owner).load().blocked)
                return@synchronized ChatNotificationResult.SUPPRESSED
            retainPrivateChat(context, wallet, reference.backend, received, endpoint)
        }
    } catch (_: Exception) { ChatNotificationResult.RETRY }
}

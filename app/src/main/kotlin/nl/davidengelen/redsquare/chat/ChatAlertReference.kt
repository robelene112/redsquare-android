package nl.davidengelen.redsquare.chat

import android.content.Context
import android.net.Uri
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

/** A private alert identifies one retained original, never carries its decrypted text. */
internal data class ChatAlertReference(val owner: String, val backend: SaitoBackend,
    val contact: String, val signature: String) {
    init {
        require(SaitoWallet.isPublicKey(owner) && SaitoWallet.isPublicKey(contact) && owner != contact)
        require(signature.matches(Regex("[0-9a-f]{128}")))
    }

    fun uri(): Uri = Uri.Builder().scheme("redsquare").authority("chat")
        .appendPath(backend.id).appendPath(owner).appendPath(contact).appendPath(signature).build()

    /** Opening uses the current wallet and authenticated local history, including during an outage. */
    fun canOpen(context: Context): Boolean = synchronized(SaitoLocalState.lock) {
        val wallet = AndroidWalletStore(context).loadExisting()
        if (wallet.publicKey != owner || AndroidBackendStore(context).load() != backend) return@synchronized false
        val policy = AndroidPersonalControls(context, owner).load()
        if (policy.hidesSocial(contact) || PrivateChat.conversationId(owner, contact) in policy.mutedConversations) {
            return@synchronized false
        }
        ChatHistoryStore(context, wallet, backend, contact).load().entries.any {
            it.message.id == signature && it.message.author == contact
        }
    }

    companion object {
        fun parse(uri: Uri?): ChatAlertReference? = try {
            requireNotNull(uri)
            require(uri.toString().toByteArray(Charsets.UTF_8).size <= 1024)
            require(uri.scheme == "redsquare" && uri.authority == "chat" && uri.query == null && uri.fragment == null)
            val parts = uri.pathSegments
            require(parts.size == 4)
            ChatAlertReference(parts[1], SaitoBackend.entries.single { it.id == parts[0] }, parts[2], parts[3])
                .also { require(it.uri().toString() == uri.toString()) }
        } catch (_: Exception) { null }
    }
}

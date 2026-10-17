package nl.davidengelen.redsquare.chat

import android.content.Context
import android.net.Uri
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

/** An alert identifies one retained original, never carries its message text. */
internal data class ChatAlertReference(val owner: String, val backend: SaitoBackend,
    val conversation: String, val signature: String, val kind: Kind = Kind.PRIVATE) {
    enum class Kind(val authority: String) { PRIVATE("chat"), GROUP("chat-group") }

    val privateContact: String? get() = conversation.takeIf { kind == Kind.PRIVATE }
    val groupId: String? get() = conversation.takeIf { kind == Kind.GROUP }

    init {
        require(SaitoWallet.isPublicKey(owner) && SaitoWallet.isPublicKey(conversation) && owner != conversation)
        require(signature.matches(Regex("[0-9a-f]{128}")))
    }

    fun uri(): Uri = Uri.Builder().scheme("redsquare").authority(kind.authority)
        .appendPath(backend.id).appendPath(owner).appendPath(conversation).appendPath(signature).build()

    /** Opening uses the current wallet and authenticated local history, including during an outage. */
    fun canOpen(context: Context): Boolean = synchronized(SaitoLocalState.lock) {
        val wallet = AndroidWalletStore(context).loadExisting()
        if (wallet.publicKey != owner || AndroidBackendStore(context).load() != backend) return@synchronized false
        val policy = AndroidPersonalControls(context, owner).load()
        when (kind) {
            Kind.PRIVATE -> {
                if (policy.hidesSocial(conversation) ||
                    PrivateChat.conversationId(owner, conversation) in policy.mutedConversations) return@synchronized false
                ChatHistoryStore(context, wallet, backend, conversation).load().entries.any {
                    it.message.id == signature && it.message.author == conversation
                }
            }
            Kind.GROUP -> {
                if (conversation in policy.mutedConversations) return@synchronized false
                val entry = GroupChatStore(context, wallet, backend).load(conversation) ?: return@synchronized false
                if (entry.group.memberRoles[owner]?.active != true) return@synchronized false
                entry.messages.any {
                    it.id == signature && it.author != owner && !policy.hidesSocial(it.author)
                }
            }
        }
    }

    companion object {
        fun parse(uri: Uri?): ChatAlertReference? = try {
            requireNotNull(uri)
            require(uri.toString().toByteArray(Charsets.UTF_8).size <= 1024)
            require(uri.scheme == "redsquare" && uri.query == null && uri.fragment == null)
            val kind = Kind.entries.single { it.authority == uri.authority }
            val parts = uri.pathSegments
            require(parts.size == 4)
            ChatAlertReference(parts[1], SaitoBackend.entries.single { it.id == parts[0] }, parts[2], parts[3], kind)
                .also { require(it.uri().toString() == uri.toString()) }
        } catch (_: Exception) { null }
    }
}

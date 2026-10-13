package nl.davidengelen.redsquare.chat

import android.content.Context
import nl.davidengelen.redsquare.saito.*

/** Authenticate and retain original private messages or reaction metadata before RPC acknowledgement. */
internal fun receivePrivateChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    bytes: ByteArray): String {
    val envelope = PrivateChat.originalEnvelope(bytes)
    val signed = SaitoEnvelope.decodePayload(envelope)
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

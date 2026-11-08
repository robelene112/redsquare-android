package nl.davidengelen.redsquare.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Retain a newly opened recipient so unsent local work remains discoverable in Chat. */
internal suspend fun retainChatContact(wallet: SaitoWallet, contact: String,
    onWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit) {
    val updated = withContext(Dispatchers.IO) {
        if (contact in wallet.chatContacts()) null else wallet.withChatContact(contact)
    }
    if (updated != null) onWalletMetadata(wallet, updated)
}

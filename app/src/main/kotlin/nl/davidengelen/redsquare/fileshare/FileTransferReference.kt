package nl.davidengelen.redsquare.fileshare

import android.content.Context
import android.net.Uri
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

/** Notifications address retained transfer metadata, never an arbitrary public-key navigation command. */
internal data class FileTransferReference(val owner: String, val backend: SaitoBackend, val contact: String, val token: String) {
    fun uri(): Uri = Uri.Builder().scheme("redsquare").authority("fileshare").appendPath(backend.id)
        .appendPath(owner).appendPath(contact).appendPath(token).build()
    fun canOpen(context: Context): Boolean {
        check(AndroidWalletStore(context).loadExisting().publicKey == owner && AndroidBackendStore(context).load() == backend)
        return !AndroidPersonalControls(context, owner).load().hidesIncomingChat(contact) &&
            FileTransferStore(context, owner, backend, contact).load().any { it.token == token }
    }
    companion object {
        fun parse(uri: Uri?): FileTransferReference? = try {
            requireNotNull(uri)
            require(uri.toString().length <= 1024 && uri.scheme == "redsquare" && uri.authority == "fileshare" && uri.query == null && uri.fragment == null)
            val paths = uri.pathSegments
            require(paths.size == 4 && SaitoWallet.isPublicKey(paths[1]) && SaitoWallet.isPublicKey(paths[2]) && paths[1] != paths[2])
            require(paths[3].matches(Regex("[0-9a-f-]{36}")))
            FileTransferReference(paths[1], SaitoBackend.entries.single { it.id == paths[0] }, paths[2], paths[3])
                .also { require(it.uri().toString() == uri.toString()) }
        } catch (_: Exception) { null }
    }
}

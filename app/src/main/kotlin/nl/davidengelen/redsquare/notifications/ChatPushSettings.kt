package nl.davidengelen.redsquare.notifications

import android.content.Context
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.File

/** Deliberate background Chat consent, independent of social consent and private previews. */
internal class ChatPushSettings(private val context: Context, private val owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val name = "chatpush.$owner.settings.v1"
    private val pending = File(context.noBackupFilesDir, "$name.new")
    private val file = EncryptedLocalFile(context, name, "redsquare.chatpush.$owner", "chatpush.$owner.")

    fun enabled(): Boolean = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: run { check(!pending.exists()); return@synchronized false }
        require(contents.version == 1.toByte() && contents.text in listOf("enabled", "disabled"))
        contents.text == "enabled"
    }

    fun setEnabled(enabled: Boolean) = synchronized(SaitoLocalState.lock) {
        check(AndroidWalletStore(context).loadExisting().publicKey == owner)
        enabled() // Preserve an unreadable choice instead of overwriting it.
        file.save(EncryptedLocalFile.Contents(1, if (enabled) "enabled" else "disabled"))
        check(enabled() == enabled)
        PushRegistration.request(context)
    }
}

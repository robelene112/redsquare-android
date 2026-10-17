package nl.davidengelen.redsquare.notifications

import android.content.Context
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.File

/** Deliberate app consent is independent of Android permission and the previously offered prompt. */
internal class SocialPushSettings(private val context: Context, owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val name = "socialpush.$owner.settings.v1"
    private val pending = File(context.noBackupFilesDir, "$name.new")
    private val file = EncryptedLocalFile(context, name, "redsquare.socialpush.$owner", "socialpush.$owner.")

    fun enabled(): Boolean = choice() == true

    fun choice(): Boolean? = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: run { check(!pending.exists()); return@synchronized null }
        require(contents.version == 1.toByte() && contents.text in listOf("enabled", "disabled"))
        contents.text == "enabled"
    }

    fun setEnabled(enabled: Boolean) = synchronized(SaitoLocalState.lock) {
        // Do not overwrite an unreadable existing consent record.
        enabled()
        file.save(EncryptedLocalFile.Contents(1, if (enabled) "enabled" else "disabled"))
        check(enabled() == enabled)
        PushRegistration.request(context)
    }
}

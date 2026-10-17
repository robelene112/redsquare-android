package nl.davidengelen.redsquare.chat

import android.app.NotificationManager
import android.content.Context
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*
import java.io.File

/** Local display consent belongs to the wallet, separately from remote social alert consent. */
internal class ChatAlertSettings(private val context: Context, private val owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val name = "chat-alerts.$owner.settings.v1"
    private val pending = File(context.noBackupFilesDir, "$name.new")
    private val file = EncryptedLocalFile(context, name, "redsquare.chat-alerts.$owner", "chat-alerts.$owner.")

    fun previewsEnabled(): Boolean = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: run { check(!pending.exists()); return@synchronized false }
        require(contents.version == 1.toByte() && contents.text in listOf("enabled", "disabled"))
        contents.text == "enabled"
    }

    fun setPreviewsEnabled(enabled: Boolean) = synchronized(SaitoLocalState.lock) {
        check(AndroidWalletStore(context).loadExisting().publicKey == owner)
        previewsEnabled() // Preserve unreadable prior choices for recovery instead of overwriting them.
        file.save(EncryptedLocalFile.Contents(1, if (enabled) "enabled" else "disabled"))
        check(previewsEnabled() == enabled)
        if (!enabled) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            notifications.activeNotifications.filter {
                it.tag?.startsWith("chat:$owner:") == true && it.notification.channelId == "private-chats"
            }.forEach { notifications.cancel(it.tag, it.id) }
        }
    }
}

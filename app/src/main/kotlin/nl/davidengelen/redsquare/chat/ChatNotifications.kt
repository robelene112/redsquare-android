package nl.davidengelen.redsquare.chat

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*

private enum class ChatAlertKind(val channel: String, val label: Int) {
    PRIVATE("private-chats", R.string.chat_private_notification_channel),
    GROUP("group-chats", R.string.chat_group_notification_channel)
}

private data class IncomingChatAlert(val id: String, val author: String, val conversationId: String,
    val kind: ChatAlertKind)

internal fun notifyPrivateChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    message: PrivateChatMessage) = notifyChat(context, wallet, backend,
        IncomingChatAlert(message.id, message.author, message.conversationId, ChatAlertKind.PRIVATE))

internal fun notifyGroupChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    message: GroupChatMessage) = notifyChat(context, wallet, backend,
        IncomingChatAlert(message.id, message.author, message.groupId, ChatAlertKind.GROUP))

/** Optional system alerts follow retention. Failures never undo an authenticated saved message. */
private fun notifyChat(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    message: IncomingChatAlert) {
    if (message.author == wallet.publicKey) return
    try {
        synchronized(SaitoLocalState.lock) {
            if (AndroidWalletStore(context).loadExisting().publicKey != wallet.publicKey ||
                AndroidBackendStore(context).load() != backend) return
            val policy = AndroidPersonalControls(context, wallet.publicKey).load()
            if (policy.hidesSocial(message.author) || message.conversationId in policy.mutedConversations) return
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val notifications = context.getSystemService(NotificationManager::class.java)
            if (!notifications.areNotificationsEnabled()) return
            val channel = message.kind.channel
            notifications.createNotificationChannel(NotificationChannel(channel,
                context.getString(message.kind.label), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.chat_new_message))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            notifications.notify("chat:${wallet.publicKey}:${backend.id}:${message.id}", 0, notification)
        }
    } catch (_: Exception) {
        // Missing permission, unreadable policy or an unavailable system service stays silent.
    }
}

package nl.davidengelen.redsquare.spaces

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState

/** Native delivery for accepted activity already observed by the local account. */
internal object SpaceActivityNotifications {
    private const val CHANNEL = "spaces-activity"
    private const val KIND = 0

    fun post(context: Context, owner: String, backend: SaitoBackend, endpoint: String, group: SpaceActivityGroup): Boolean =
        synchronized(SaitoLocalState.lock) {
            if (AndroidWalletStore(context).loadExisting().publicKey != owner || AndroidBackendStore(context).load() != backend ||
                group.target.community.node != backend.webUrl) return@synchronized false
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return@synchronized false
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return@synchronized false
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.spaces_activity_title),
                NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return@synchronized false
            val tag = "spaces:$owner:${backend.id}:${group.id}"
            val launch = Intent(Intent.ACTION_VIEW, Uri.parse(group.target.link()), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(SpacesLaunchRoutes.NOTIFICATION_OWNER, owner)
                .putExtra(SpacesLaunchRoutes.NOTIFICATION_BACKEND, backend.id)
            if (BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish") && endpoint.startsWith("ws://127.0.0.1:")) {
                launch.putExtra(if (backend == SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl", endpoint)
            }
            val pending = PendingIntent.getActivity(context, tag.hashCode(), launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(context.getString(R.string.spaces_activity_notification_title))
                .setContentText(context.getString(R.string.spaces_activity_notification))
                .setNumber(group.actorCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setCategory(Notification.CATEGORY_SOCIAL).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pending).build()
            manager.notify(tag, KIND, notification)
            true
        }
}

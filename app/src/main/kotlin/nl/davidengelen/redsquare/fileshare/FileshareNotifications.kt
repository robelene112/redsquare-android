package nl.davidengelen.redsquare.fileshare

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

internal object FileshareNotifications {
    fun available(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(FileTransferService.CHANNEL,
            context.getString(R.string.fileshare_channel), NotificationManager.IMPORTANCE_LOW))
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled() && manager.getNotificationChannel(FileTransferService.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}

internal class FileNotificationGate(val run: (() -> Unit) -> Unit, val notice: @Composable () -> Unit)

/** Request only when sharing a file; declining leaves the draft and offer intact. */
@Composable
internal fun rememberFileNotificationGate(): FileNotificationGate {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var blocked by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val action = pending; pending = null
        if (allowed && FileshareNotifications.available(context)) { blocked = false; action?.invoke() }
        else blocked = true
    }
    return FileNotificationGate(run = { action ->
        if (FileshareNotifications.available(context)) { blocked = false; action() }
        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = action; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else blocked = true
    }, notice = {
        InlineStatus(if (blocked) stringResource(R.string.fileshare_notifications_required) else null, StatusTone.INFO,
            actionLabel = stringResource(R.string.fileshare_notification_settings), onAction = {
                context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, FileTransferService.CHANNEL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
    })
}

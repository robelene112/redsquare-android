package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional feature consent before Android permission. In-app reading remains available. */
@Composable
internal fun NotificationPermissionPrompt(needsConsent: Boolean = false,
    detail: Int = R.string.notifications_permission_detail,
    onEnable: suspend () -> Unit = {}, onSkip: suspend () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("notification-permission", 0) }
    var open by remember(needsConsent) { mutableStateOf(needsConsent || (!preferences.getBoolean("offered", false) &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED))) }
    var failure by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        nl.davidengelen.redsquare.notifications.PushRegistration.request(context.applicationContext)
    }
    fun choose(allow: Boolean) {
        if (saving) return
        saving = true
        failure = null
        scope.launch {
            var recorded = false
            try {
                if (allow) onEnable() else onSkip()
                check(withContext(Dispatchers.IO) { preferences.edit().putBoolean("offered", true).commit() })
                recorded = true
                open = false
                if (allow) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                failure = if (recorded) R.string.notifications_controls_open_error else R.string.notifications_permission_save_error
                open = allow
            }
            finally { saving = false }
        }
    }
    if (!open) failure?.let { Text(stringResource(it)) }
    if (open) AlertDialog(onDismissRequest = { if (!saving) choose(false) },
        title = { Text(stringResource(R.string.notifications_permission_title)) },
        text = { Column {
            Text(stringResource(detail))
            failure?.let { Text(stringResource(it)) }
        } },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { choose(true) }) { Text(stringResource(R.string.notifications_permission_enable)) }
        }, dismissButton = {
            TextButton(enabled = !saving, onClick = { choose(false) }) { Text(stringResource(R.string.notifications_permission_skip)) }
        })
}

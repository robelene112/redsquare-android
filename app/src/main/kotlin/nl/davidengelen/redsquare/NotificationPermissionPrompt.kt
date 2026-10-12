package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device-level permission choice. In-app reading never depends on Android alerts. */
@Composable
internal fun NotificationPermissionPrompt() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val preferences = remember { context.getSharedPreferences("notification-permission", 0) }
    var open by remember { mutableStateOf(!preferences.getBoolean("offered", false) &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) }
    var saveFailed by remember { mutableStateOf(false) }
    fun recordOffer() {
        open = false
        if (activity == null) { saveFailed = true; return }
        activity.lifecycleScope.launch {
            saveFailed = !withContext(Dispatchers.IO) { preferences.edit().putBoolean("offered", true).commit() }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recordOffer() }
    if (saveFailed) Text(stringResource(R.string.notifications_permission_save_error))
    if (open) AlertDialog(onDismissRequest = { recordOffer() },
        title = { Text(stringResource(R.string.notifications_permission_title)) },
        text = { Text(stringResource(R.string.notifications_permission_detail)) },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    recordOffer()
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text(stringResource(R.string.notifications_permission_enable)) }
        }, dismissButton = { TextButton(onClick = { recordOffer() }) { Text(stringResource(R.string.notifications_permission_skip)) } })
}

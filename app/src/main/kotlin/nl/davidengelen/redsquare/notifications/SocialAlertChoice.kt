package nl.davidengelen.redsquare.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.R

/** Wallet-scoped app consent, separate from Android permission and categories. */
@Composable
internal fun SocialAlertChoice(owner: String, openAndroidSettings: suspend () -> Boolean) {
    val context = LocalContext.current.applicationContext
    val settings = remember(context, owner) { SocialPushSettings(context, owner) }
    val scope = rememberCoroutineScope()
    var choice by remember(owner) { mutableStateOf<Boolean?>(null) }
    var saving by remember(owner) { mutableStateOf(false) }
    var failed by remember(owner) { mutableStateOf(false) }
    var opening by remember(owner) { mutableStateOf(false) }
    var openFailed by remember(owner) { mutableStateOf(false) }
    var retry by remember(owner) { mutableIntStateOf(0) }
    LaunchedEffect(settings, retry) {
        choice = null
        try {
            choice = withContext(Dispatchers.IO) { settings.enabled() }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = choice == true, enabled = choice != null && !saving && !opening, role = Role.Switch,
        onValueChange = { requested ->
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { settings.setEnabled(requested) }
                    choice = requested
                    failed = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { choice = null; failed = true }
                finally { saving = false }
            }
        }), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.notifications_social_choice), Modifier.weight(1f))
        Switch(checked = choice == true, onCheckedChange = null, enabled = choice != null && !saving && !opening)
    }
    Text(stringResource(if (BuildConfig.PUSH_CONFIGURED) R.string.notifications_social_choice_detail
        else R.string.notifications_social_unconfigured_detail), style = MaterialTheme.typography.bodySmall)
    if (failed) {
        Text(stringResource(R.string.notifications_social_choice_error))
        TextButton(enabled = !saving && !opening, onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    }
    TextButton(enabled = !saving && !opening, onClick = {
        opening = true
        openFailed = false
        scope.launch {
            try { openFailed = !openAndroidSettings() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { openFailed = true }
            finally { opening = false }
        }
    }) { Text(stringResource(R.string.notifications_android_settings)) }
    if (openFailed) Text(stringResource(R.string.notifications_android_settings_error))
}

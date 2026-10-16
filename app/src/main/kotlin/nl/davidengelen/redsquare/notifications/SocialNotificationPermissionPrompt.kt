package nl.davidengelen.redsquare.notifications

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.NotificationPermissionPrompt
import nl.davidengelen.redsquare.R

/** Social consent belongs to this wallet, independently of Chat's Android permission offer. */
@Composable
internal fun SocialNotificationPermissionPrompt(owner: String) {
    val context = LocalContext.current.applicationContext
    val settings = remember(context, owner) { SocialPushSettings(context, owner) }
    val preferences = remember(context) { context.getSharedPreferences("notification-permission", 0) }
    val offerKey = "social-offered:$owner"
    var choice by remember(owner) { mutableStateOf<Boolean?>(null) }
    var offered by remember(owner) { mutableStateOf(false) }
    var ready by remember(owner) { mutableStateOf(false) }
    var failed by remember(owner) { mutableStateOf(false) }
    var retry by remember(owner) { mutableIntStateOf(0) }
    LaunchedEffect(settings, retry) {
        ready = false
        failed = false
        try {
            val saved = withContext(Dispatchers.IO) { settings.choice() to preferences.getBoolean(offerKey, false) }
            choice = saved.first
            offered = saved.second
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    fun recordOffer() { check(preferences.edit().putBoolean(offerKey, true).commit()) }
    if (ready) NotificationPermissionPrompt(needsConsent = choice == null && !offered,
        detail = R.string.notifications_social_offer_detail,
        onEnable = {
            withContext(Dispatchers.IO) {
                SocialPush.ensureChannels(context)
                settings.setEnabled(true)
                recordOffer()
            }
        }, onSkip = { withContext(Dispatchers.IO) { recordOffer() } })
    if (failed) {
        Text(stringResource(R.string.notifications_social_choice_error))
        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    }
}

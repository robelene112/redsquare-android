package nl.davidengelen.redsquare.policy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.R

@Composable
internal fun PublisherEmailDialog(purpose: PublisherContactPurpose, onClose: () -> Unit) {
    val context = LocalContext.current
    val routes = remember(context) { PublisherRoutes.load(context) }
    val email = routes.email(purpose)
    if (email == null) {
        AlertDialog(onDismissRequest = onClose,
            title = { Text(stringResource(purpose.title)) },
            text = { Text(stringResource(purpose.unavailable)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.publisher_done)) } })
        return
    }
    var description by rememberSaveable { mutableStateOf("") }
    var draftRequested by rememberSaveable { mutableStateOf(false) }
    var handoffFailed by rememberSaveable { mutableStateOf(false) }
    val appContext = stringResource(R.string.publisher_app_context, BuildConfig.VERSION_NAME)
    val subject = stringResource(purpose.subject)
    val body = stringResource(R.string.publisher_email_body, appContext, description)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val keyboard = LocalSoftwareKeyboardController.current
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text(stringResource(purpose.title),
                    Modifier.fillMaxWidth().padding(20.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.publisher_name))
                            Text(stringResource(R.string.publisher_email_destination, email))
                            Text(appContext)
                        }
                    }
                    Text(stringResource(R.string.publisher_email_explanation), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(description, onValueChange = { description = it },
                        label = { Text(stringResource(R.string.publisher_description)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
                if (handoffFailed || draftRequested) Text(stringResource(if (handoffFailed)
                    R.string.publisher_email_unavailable else R.string.publisher_email_requested),
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (handoffFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(if (draftRequested) R.string.publisher_done else R.string.publisher_cancel))
                    }
                    Button(enabled = description.isNotBlank(), onClick = {
                        keyboard?.hide()
                        handoffFailed = false
                        draftRequested = false
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null))
                                .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                                .putExtra(Intent.EXTRA_SUBJECT, subject)
                                .putExtra(Intent.EXTRA_TEXT, body))
                            draftRequested = true
                        } catch (_: ActivityNotFoundException) { handoffFailed = true }
                        catch (_: SecurityException) { handoffFailed = true }
                    }) { Text(stringResource(R.string.publisher_compose_email)) }
                }
            }
        }
    }
}

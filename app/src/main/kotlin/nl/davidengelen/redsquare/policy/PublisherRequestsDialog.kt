package nl.davidengelen.redsquare.policy

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

/** Opening a configured page never submits a request or mutates local/account data. */
@Composable
internal fun PublisherRequestsDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val routes = remember(context) { PublisherRoutes.load(context) }
    val url = routes.requestUrl.takeIf { routes.ready }
    if (url == null) {
        AlertDialog(onDismissRequest = onClose,
            title = { Text(stringResource(R.string.publisher_requests)) },
            text = { Text(stringResource(R.string.publisher_requests_unavailable)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.publisher_done)) } })
        return
    }
    var requested by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(stringResource(R.string.publisher_requests)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.publisher_name))
                            Text(url)
                        }
                    }
                    Text(stringResource(R.string.publisher_requests_explanation))
                }
                if (failed || requested) Text(stringResource(if (failed) R.string.publisher_browser_unavailable
                    else R.string.publisher_request_page_requested),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                requested = false
                failed = false
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url).normalizeScheme()))
                    requested = true
                } catch (_: ActivityNotFoundException) { failed = true }
                catch (_: SecurityException) { failed = true }
            }) { Text(stringResource(R.string.publisher_open_request_page)) }
        },
        dismissButton = { TextButton(onClick = onClose) {
            Text(stringResource(if (requested) R.string.publisher_done else R.string.publisher_cancel))
        } })
}

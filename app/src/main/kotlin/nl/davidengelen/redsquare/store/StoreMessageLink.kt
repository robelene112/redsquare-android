package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import nl.davidengelen.redsquare.saito.StoreLink
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

private val webTarget = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** Chat keeps authored text literal and exposes the first supported Store reading target. */
@Composable
internal fun StoreMessageLink(text: String) {
    val url = remember(text) { webTarget.findAll(text).map { it.value }.firstOrNull { StoreLink.parse(it) != null } } ?: return
    val uri = LocalUriHandler.current
    val navigation = LocalPostNavigation.current
    val scope = rememberCoroutineScope()
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(enabled = navigation?.transitioning != true, modifier = Modifier.heightIn(min = 48.dp), onClick = {
        scope.launch {
            try {
                failed = if (navigation == null) { uri.openUri(url); false }
                else !navigation.navigate { uri.openUri(url); true }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }) { Text(stringResource(R.string.post_open_store)) }
    InlineStatus(if (failed) stringResource(R.string.post_link_unavailable) else null, StatusTone.ERROR)
}

package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.identity.PublicIdentityDetailsDialog
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.registry.registeredNameIsCurrent
import nl.davidengelen.redsquare.saito.SaitoBackend

/** Account surfaces share the same backend/registrar-qualified, freshness-checked cache. */
@Composable
internal fun cachedAccountName(owner: String, backend: SaitoBackend?, registrar: String, enabled: Boolean = true): String? {
    val context = LocalContext.current.applicationContext
    if (!enabled || backend == null) return null
    val name by key(owner, backend, registrar) { produceState<String?>(null) {
        value = try {
            withContext(Dispatchers.IO) {
                RegisteredNameStore(context, owner, registrar).load()
                    ?.takeIf { registeredNameIsCurrent(it, backend, System.currentTimeMillis()) }?.name?.identifier
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    } }
    return name
}

/** Only public identity enters the local article preview. No wallet, signing or publication. */
@Composable
internal fun StackPreviewAuthor(owner: String, backend: SaitoBackend, registrar: String) {
    val name = cachedAccountName(owner, backend, registrar) ?: "Anon-${owner.take(6)}"
    var details by remember(owner, backend, registrar) { mutableStateOf(false) }
    Surface(onClick = { details = true }, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("stack-preview-author")) {
        Row(Modifier.heightIn(min = 48.dp).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileAvatar(owner, Modifier.size(42.dp).clip(CircleShape), cachedOnly = true)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.stack_local_author), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (details) PublicIdentityDetailsDialog(owner, name) { details = false }
}

package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar

internal enum class AppModule(val title: Int) {
    RED_SQUARE(R.string.app_name), STACK(R.string.stack_title)
}

/** Shared account navigation over the real installation, never a preview wallet. */
@Composable
internal fun AppHeader(node: String?, owner: String?, module: AppModule, walletEnabled: Boolean, settingsEnabled: Boolean,
    onWallet: () -> Unit, onSettings: () -> Unit, onModule: (AppModule) -> Unit, onConnectionDetails: () -> Unit) {
    val switchLabel = stringResource(R.string.switch_app, stringResource(module.title))
    val accountLabel = stringResource(R.string.account)
    var appsOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { appsOpen = true }, enabled = walletEnabled, modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { contentDescription = switchLabel },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    // The selector already carries its full accessible name once.
                    Text(stringResource(module.title), Modifier.weight(1f, fill = false).clearAndSetSemantics { },
                        style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                }
                AppMenu(appsOpen, onDismissRequest = { appsOpen = false }) {
                    AppModule.entries.forEach { target ->
                        DropdownMenuItem(text = { Text(stringResource(target.title)) },
                            modifier = Modifier.semantics { selected = module == target },
                            enabled = walletEnabled,
                            onClick = { if (appsOpen) { appsOpen = false; onModule(target) } })
                    }
                }
            }
            Box {
                IconButton(onClick = { accountOpen = true },
                    enabled = walletEnabled || settingsEnabled,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = accountLabel }) {
                    if (owner != null) ProfileAvatar(owner,
                        Modifier.size(36.dp).clip(CircleShape).clearAndSetSemantics { }, cachedOnly = true)
                    else Icon(painterResource(R.drawable.ic_profile), null)
                }
                AppMenu(accountOpen, onDismissRequest = { accountOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.wallet)) }, enabled = walletEnabled,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_wallet), null) },
                        onClick = { if (accountOpen) { accountOpen = false; onWallet() } })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, enabled = settingsEnabled,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_settings), null) },
                        onClick = { if (accountOpen) { accountOpen = false; onSettings() } })
                }
            }
        }
        if (node != null) TextButton(onClick = onConnectionDetails,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.selected_server, node), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.connection_details), Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

}

/** Shared-host modal ownership also keeps pending routes behind the open information view. */
@Composable
internal fun NodeConnectionDetails(node: String?, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_details)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(if (node != null) stringResource(R.string.selected_server, node)
                else stringResource(R.string.connection_node_unavailable))
            Text(stringResource(R.string.block_observation_unavailable), style = MaterialTheme.typography.bodyMedium)
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

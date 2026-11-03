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
import nl.davidengelen.redsquare.feed.GeneratedProfilePicture

internal enum class AppModule(val title: Int) {
    RED_SQUARE(R.string.app_name), STACK(R.string.stack_title)
}

/** Shared account navigation over the real installation, never a preview wallet. */
@Composable
internal fun AppHeader(node: String?, owner: String?, module: AppModule, walletEnabled: Boolean, settingsEnabled: Boolean,
    onWallet: () -> Unit, onSettings: () -> Unit, onModule: (AppModule) -> Unit) {
    val switchLabel = stringResource(R.string.switch_app, stringResource(module.title))
    val accountLabel = stringResource(R.string.account)
    var appsOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var nodeOpen by remember { mutableStateOf(false) }
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
                    Text(stringResource(module.title), Modifier.weight(1f, fill = false),
                        style = if (LocalDensity.current.fontScale > 1.3f) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                }
                DropdownMenu(appsOpen, onDismissRequest = { appsOpen = false }) {
                    AppModule.entries.forEach { target ->
                        DropdownMenuItem(text = { Text(stringResource(target.title)) },
                            modifier = Modifier.semantics { selected = module == target },
                            enabled = walletEnabled,
                            onClick = { appsOpen = false; onModule(target) })
                    }
                }
            }
            Box {
                IconButton(onClick = { accountOpen = true },
                    enabled = walletEnabled || settingsEnabled,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = accountLabel }) {
                    if (owner != null) GeneratedProfilePicture(owner,
                        Modifier.size(36.dp).clip(CircleShape).clearAndSetSemantics { })
                    else Icon(painterResource(R.drawable.ic_profile), null)
                }
                DropdownMenu(accountOpen, onDismissRequest = { accountOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.wallet)) }, enabled = walletEnabled,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_wallet), null) },
                        onClick = { accountOpen = false; onWallet() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, enabled = settingsEnabled,
                        leadingIcon = { Icon(painterResource(R.drawable.ic_settings), null) },
                        onClick = { accountOpen = false; onSettings() })
                }
            }
        }
        if (node != null) TextButton(onClick = { nodeOpen = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.selected_server, node), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.connection_details), Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (nodeOpen) AlertDialog(onDismissRequest = { nodeOpen = false },
        title = { Text(stringResource(R.string.connection_details)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(stringResource(R.string.selected_server, node.orEmpty()))
            Text(stringResource(R.string.block_observation_unavailable), style = MaterialTheme.typography.bodyMedium)
        } }, confirmButton = { TextButton(onClick = { nodeOpen = false }) { Text(stringResource(R.string.done)) } })
}

package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import nl.davidengelen.redsquare.NodeBlockObservation
import nl.davidengelen.redsquare.NodeBlockState
import nl.davidengelen.redsquare.NodeBlockFailure
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
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
    onWallet: () -> Unit, onSettings: () -> Unit, onModule: (AppModule) -> Unit, onConnectionDetails: () -> Unit,
    blocks: NodeBlockState? = null) {
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
            val report = blocks?.takeIf { it.backend?.domain == node }
            val label = nodeBlockHeaderLabel(report)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.selected_server, node),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleOwnerReveal(label) {
                    Text(label,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.connection_details), Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

}

/** Shared-host modal ownership also keeps pending routes behind the open information view. */
@Composable
internal fun NodeConnectionDetails(node: String?, blocks: NodeBlockState? = null, onDismiss: () -> Unit) {
    val report = blocks?.takeIf { it.backend?.domain == node }
    val block = report?.observation
    val notice = when {
        report?.showProgress == true -> R.string.node_block_checking
        report?.failure == NodeBlockFailure.CONNECTION -> R.string.node_block_disconnected
        report?.failure == NodeBlockFailure.INFORMATION -> R.string.node_block_missing
        report?.lastSeen == true -> R.string.node_block_check_old
        report?.olderBlock == true -> R.string.node_block_old_status
        else -> null
    }
    val tone = when {
        report?.showProgress == true -> StatusTone.WORKING
        report?.failure != null -> StatusTone.ERROR
        else -> StatusTone.WARNING
    }
    AlertDialog(onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        title = { Text(stringResource(R.string.connection_details), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(if (node != null) stringResource(R.string.selected_server, node)
                else stringResource(R.string.connection_node_unavailable))
            InlineStatus(notice?.let { stringResource(it) }, tone)
            SingleOwnerReveal(block != null) {
                if (block == null) Text(stringResource(R.string.block_observation_unavailable))
                else AccountCard {
                    Column(Modifier.padding(AppSpacing.gap), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(nodeBlockLabel(block), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.node_block_time), style = MaterialTheme.typography.labelSmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(block.block.timestamp)))
                        Text(stringResource(R.string.node_block_checked_at), style = MaterialTheme.typography.labelSmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(block.receivedAtMillis)))
                    }
                }
            }
            Text(stringResource(R.string.node_block_feed_separate), style = MaterialTheme.typography.bodySmall)
        } }, dismissButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), enabled = report?.backend != null && !report.checking,
                onClick = { report?.checkAgain() }) { Text(stringResource(R.string.node_block_check_again)) }
        }, confirmButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun nodeBlockLabel(observation: NodeBlockObservation): String =
    stringResource(R.string.node_block_height, NumberFormat.getIntegerInstance().format(observation.block.height))

@Composable
private fun nodeBlockHeaderLabel(report: NodeBlockState?): String {
    if (report == null) return stringResource(R.string.node_block_unknown)
    val observation = report.observation
    if (observation == null) return stringResource(when {
        report.checking -> R.string.node_block_checking
        report.failure == NodeBlockFailure.CONNECTION -> R.string.node_block_unreachable
        else -> R.string.node_block_unknown
    })
    val label = nodeBlockLabel(observation)
    return when {
        report.lastSeen -> stringResource(R.string.node_block_last_seen, label)
        report.olderBlock -> stringResource(R.string.node_block_older, label)
        else -> label
    }
}

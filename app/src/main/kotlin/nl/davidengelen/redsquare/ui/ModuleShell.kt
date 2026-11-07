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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.registry.registeredNameIsCurrent
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoBackend
import androidx.compose.foundation.background
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
    blocks: NodeBlockState? = null, registryAuthority: String = SaitoRegistry.REGISTRAR) {
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
                DesignSheet(appsOpen, stringResource(R.string.apps_title), { appsOpen = false }) {
                    Text(stringResource(R.string.apps_summary), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppModule.entries.forEach { target ->
                        Surface(onClick = { if (appsOpen) { appsOpen = false; onModule(target) } },
                            enabled = walletEnabled && appsOpen, shape = RoundedCornerShape(AppSpacing.cardRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.semantics { selected = module == target }) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(shape = RoundedCornerShape(12.dp), color = if (target == AppModule.RED_SQUARE)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        Icon(painterResource(if (target == AppModule.RED_SQUARE) R.drawable.ic_home else R.drawable.ic_article),
                                            null, Modifier.size(22.dp), tint = if (target == AppModule.RED_SQUARE)
                                                MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(target.title), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(if (target == AppModule.RED_SQUARE) R.string.app_redsquare_summary else R.string.app_stack_summary),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(painterResource(if (module == target) R.drawable.ic_check else R.drawable.ic_forward), null, Modifier.size(22.dp))
                            }
                        }
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
                DesignSheet(accountOpen, accountLabel, { accountOpen = false }) {
                    if (owner != null) {
                        val context = LocalContext.current.applicationContext
                        val backend = SaitoBackend.entries.firstOrNull { it.domain == node }
                        val name by produceState<String?>(null, owner, backend, registryAuthority, accountOpen) {
                            value = if (!accountOpen || backend == null) null else try {
                                withContext(Dispatchers.IO) {
                                    RegisteredNameStore(context, owner, registryAuthority).load()
                                        ?.takeIf { registeredNameIsCurrent(it, backend, System.currentTimeMillis()) }?.name?.identifier
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileAvatar(owner, Modifier.size(50.dp).clip(CircleShape), cachedOnly = true)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(name ?: "Anon-${owner.take(6)}", style = MaterialTheme.typography.titleLarge)
                                Text(owner, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider()
                    AccountActionRow(stringResource(R.string.wallet), R.drawable.ic_wallet,
                        summary = stringResource(R.string.account_wallet_summary), enabled = walletEnabled && accountOpen,
                        onClick = { if (accountOpen) { accountOpen = false; onWallet() } })
                    AccountActionRow(stringResource(R.string.settings), R.drawable.ic_settings,
                        summary = stringResource(R.string.settings_preferences_summary), enabled = settingsEnabled && accountOpen,
                        onClick = { if (accountOpen) { accountOpen = false; onSettings() } })
                }
            }
        }
        if (node != null) TextButton(onClick = onConnectionDetails,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            val report = blocks?.takeIf { it.backend?.domain == node }
            val label = nodeBlockHeaderLabel(report)
            val responsive = LocalDensity.current.fontScale > 1.3f
            Box(Modifier.size(6.dp).background(when {
                report?.failure != null -> MaterialTheme.colorScheme.error
                report?.observation != null && !report.lastSeen && !report.olderBlock -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }, CircleShape))
            Spacer(Modifier.width(7.dp))
            if (responsive) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.selected_server, node), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.selected_server, node), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleOwnerReveal(label) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
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
    AccountDialog(title = stringResource(R.string.connection_details),
        closeLabel = stringResource(R.string.node_block_close_details), onDismissRequest = onDismiss,
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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

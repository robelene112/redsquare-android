package nl.davidengelen.redsquare.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

/** Shared geometry and motion for the actual application frame and destination surfaces. */
internal object AppSpacing {
    val screen = 16.dp
    val gap = 12.dp
    val small = 8.dp
    const val transitionMillis = 180
}

internal enum class PrimaryDestination(val label: Int, val caption: Int, val icon: Int) {
    HOME(R.string.home_title, R.string.home_title, R.drawable.ic_home),
    SEARCH(R.string.search_title, R.string.search_title, R.drawable.ic_search),
    NOTIFICATIONS(R.string.notifications_title, R.string.navigation_activity, R.drawable.ic_notifications),
    CHAT(R.string.chat_title, R.string.chat_title, R.drawable.ic_chat),
    PROFILE(R.string.profile_own, R.string.navigation_profile, R.drawable.ic_profile)
}

@Composable
internal fun AppHeader(node: String?, home: Boolean, walletEnabled: Boolean, settingsEnabled: Boolean,
    onWallet: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = AppSpacing.small),
                style = if (home) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                maxLines = 2)
            FrameIconButton(R.drawable.ic_wallet, stringResource(R.string.wallet), walletEnabled, onWallet)
            FrameIconButton(R.drawable.ic_settings, stringResource(R.string.settings), settingsEnabled, onSettings)
        }
        if (node != null) Text(stringResource(R.string.selected_server, node),
            Modifier.padding(bottom = AppSpacing.small), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The icon carries one text label at its button seam; its painted path is decorative. */
@Composable
internal fun FrameIconButton(icon: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.semantics { text = AnnotatedString(label) }) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

@Composable
internal fun CompactBackupNotice(visible: Boolean, enabled: Boolean, onBackup: () -> Unit, onLater: () -> Unit,
    onImport: () -> Unit) {
    AnimatedVisibility(visible = visible,
        enter = expandVertically(tween(AppSpacing.transitionMillis), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(AppSpacing.transitionMillis), shrinkTowards = Alignment.Top)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).padding(bottom = AppSpacing.small)
            .animateContentSize(tween(AppSpacing.transitionMillis)), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = AppSpacing.small, vertical = 4.dp)) {
                Text(stringResource(R.string.wallet_backup_reminder), style = MaterialTheme.typography.labelLarge)
                if (LocalDensity.current.fontScale > 1.3f) {
                    BackupNoticeAction(R.string.wallet_import_existing, enabled, onImport, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        BackupNoticeAction(R.string.wallet_back_up, enabled, onBackup)
                        BackupNoticeAction(R.string.later, enabled, onLater)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BackupNoticeAction(R.string.wallet_import_existing, enabled, onImport, Modifier.weight(1f))
                        BackupNoticeAction(R.string.wallet_back_up, enabled, onBackup)
                        BackupNoticeAction(R.string.later, enabled, onLater)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupNoticeAction(label: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(AppSpacing.small),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}

/** Keep captions intact; an adaptive legend and tooltips name the icon-only layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppNavigation(selected: PrimaryDestination, enabled: Boolean, onSelect: (PrimaryDestination) -> Unit) {
    val measurer = rememberTextMeasurer()
    val captions = PrimaryDestination.entries.map { stringResource(it.caption) }
    val tooltipStates = PrimaryDestination.entries.map { rememberTooltipState() }
    val tooltipMutex = remember { MutatorMutex() }
    val tooltips = remember(tooltipStates, tooltipMutex) {
        tooltipStates.map { NavigationTooltipState(it, tooltipStates, tooltipMutex) }
    }
    val style = MaterialTheme.typography.labelSmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // Material's bar separates items by8dp. Leave another8dp for caption breathing room.
        val gap = with(density) { AppSpacing.small.roundToPx() }
        val labelWidth = (constraints.maxWidth - gap * (PrimaryDestination.entries.size - 1)) /
            PrimaryDestination.entries.size - gap
        val compact = captions.any { measurer.measure(it, style, softWrap = false).size.width > labelWidth }
        Column {
            if (compact) Crossfade(selected, animationSpec = tween(AppSpacing.transitionMillis),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }, label = "Navigation caption") { destination ->
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(destination.caption), style = MaterialTheme.typography.labelLarge,
                        maxLines = 1)
                }
            }
            NavigationBar(modifier = if (compact) Modifier.height(64.dp) else Modifier,
                containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                PrimaryDestination.entries.forEachIndexed { index, destination ->
                    val name = stringResource(destination.label)
                    // TooltipBox applies its modifier to an inner anchor. Give the outer
                    // navigation row a real weighted child so all five items share its width.
                    Box(Modifier.weight(1f)) {
                        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text(stringResource(destination.caption)) } },
                            state = tooltips[index]) {
                            Row(Modifier.fillMaxWidth()) {
                                NavigationBarItem(selected = selected == destination, enabled = enabled,
                                    modifier = Modifier.semantics { text = AnnotatedString(name) },
                                    onClick = { onSelect(destination) },
                                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                                    label = if (compact) null else { {
                                        Text(stringResource(destination.caption), Modifier.clearAndSetSemantics { },
                                            style = style, maxLines = 1)
                                    } })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keyboard-focus tooltips can outlive Material's cancelled show job. Dismiss the previous
 * navigation caption explicitly when another actual anchor is used, retaining normal motion. */
@OptIn(ExperimentalMaterial3Api::class)
private class NavigationTooltipState(private val current: TooltipState, private val group: List<TooltipState>,
    private val mutex: MutatorMutex) : TooltipState by current {
    override suspend fun show(mutatePriority: MutatePriority) {
        mutex.mutate(mutatePriority) {
            group.forEach { if (it !== current) it.dismiss() }
            current.show(mutatePriority)
        }
    }
}

@Composable
internal fun FeedMoreActions(enabled: Boolean, onQueue: () -> Unit, onChanges: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FrameIconButton(R.drawable.ic_more, stringResource(R.string.feed_more_actions), enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.post_queue_title)) },
                onClick = { open = false; onQueue() })
            DropdownMenuItem(text = { Text(stringResource(R.string.author_saved_changes)) },
                onClick = { open = false; onChanges() })
        }
    }
}

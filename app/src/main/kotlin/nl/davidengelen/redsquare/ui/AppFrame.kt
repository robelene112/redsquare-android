package nl.davidengelen.redsquare.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
internal fun CompactBackupNotice(enabled: Boolean, onBackup: () -> Unit, onLater: () -> Unit,
    onImport: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).padding(bottom = AppSpacing.small)
        .animateContentSize(tween(AppSpacing.transitionMillis)), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = AppSpacing.small, vertical = 4.dp)) {
            Text(stringResource(R.string.wallet_backup_reminder), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.wallet_import_existing), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onBackup, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(stringResource(R.string.wallet_back_up), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onLater, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(stringResource(R.string.later), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun AppNavigation(selected: PrimaryDestination, enabled: Boolean, onSelect: (PrimaryDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)) {
        PrimaryDestination.entries.forEach { destination ->
            val name = stringResource(destination.label)
            NavigationBarItem(selected = selected == destination, enabled = enabled,
                modifier = Modifier.semantics { text = AnnotatedString(name) },
                onClick = { onSelect(destination) },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.caption), Modifier.clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelSmall, maxLines = 2) })
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

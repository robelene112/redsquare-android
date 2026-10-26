package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.FeedMoreActions

/** Give feed modes their own row when text and actions cannot share the available width. */
@Composable
internal fun FeedToolbar(followingView: Boolean, onFollowing: (Boolean) -> Unit,
    refreshEnabled: Boolean, onRefresh: () -> Unit, maintenanceEnabled: Boolean,
    onQueue: () -> Unit, onChanges: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val modes = listOf(stringResource(R.string.feed_title), stringResource(R.string.following_title))
    val refresh = stringResource(R.string.feed_refresh)
    val style = MaterialTheme.typography.labelMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val tabsWidth = 2 * (modes.maxOf { measurer.measure(it, style, softWrap = false).size.width } +
            with(density) { 8.dp.roundToPx() })
        val actionsWidth = measurer.measure(refresh, MaterialTheme.typography.labelLarge, softWrap = false).size.width +
            with(density) { 80.dp.roundToPx() }
        val separateActions = tabsWidth + actionsWidth > constraints.maxWidth
        Column {
            Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                FeedTab(modes[0], !followingView) { onFollowing(false) }
                FeedTab(modes[1], followingView) { onFollowing(true) }
                if (!separateActions) FeedActions(refresh, refreshEnabled, onRefresh, maintenanceEnabled, onQueue, onChanges)
            }
            if (separateActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                FeedActions(refresh, refreshEnabled, onRefresh, maintenanceEnabled, onQueue, onChanges)
            }
        }
    }
}

@Composable
private fun FeedActions(refresh: String, enabled: Boolean, onRefresh: () -> Unit,
    maintenanceEnabled: Boolean, onQueue: () -> Unit, onChanges: () -> Unit) {
    TextButton(onClick = onRefresh, enabled = enabled) { Text(refresh, maxLines = 1) }
    FeedMoreActions(maintenanceEnabled, onQueue, onChanges)
}

@Composable
private fun RowScope.FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f)) {
        Tab(selected = selected, onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(label, Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
    }
}

package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.FeedMoreActions
import nl.davidengelen.redsquare.ui.DesignTabs

/** Give feed modes their own row when text and actions cannot share the available width. */
@Composable
internal fun FeedToolbar(followingView: Boolean, onFollowing: (Boolean) -> Unit,
    maintenanceEnabled: Boolean,
    onQueue: () -> Unit, onChanges: () -> Unit) {
    val modes = listOf(stringResource(R.string.feed_title), stringResource(R.string.following_title))
    DesignTabs(modes, if (followingView) 1 else 0, onSelect = { onFollowing(it == 1) },
        trailing = { FeedActions(maintenanceEnabled, onQueue, onChanges) })
}

@Composable
private fun FeedActions(maintenanceEnabled: Boolean, onQueue: () -> Unit, onChanges: () -> Unit) {
    FeedMoreActions(maintenanceEnabled, onQueue, onChanges)
}

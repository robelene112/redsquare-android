package nl.davidengelen.redsquare.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R

/** The gesture's equivalent for readers who cannot pull, without moving their reading position. */
@Composable
internal fun Modifier.refreshAction(enabled: Boolean, onRefresh: () -> Unit): Modifier {
    val label = stringResource(R.string.feed_refresh)
    return semantics {
        customActions = if (enabled) listOf(CustomAccessibilityAction(label) { onRefresh(); true }) else emptyList()
    }
}

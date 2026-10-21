package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot

/** Compensate for header growth only while a scrolled reader is idle, never during their gesture. */
internal fun Modifier.preserveReadingAnchor(state: LazyListState, enabled: Boolean = true,
    relativeToRoot: Boolean = false): Modifier = composed {
    val compensationEnabled by rememberUpdatedState(enabled)
    // The list's restored offset already includes header compensation. Retain
    // its parent-relative baseline and any unapplied delta through recreation.
    var previousTop by rememberSaveable(state) { mutableStateOf<Float?>(null) }
    var pending by rememberSaveable(state) { mutableStateOf(0f) }
    LaunchedEffect(state) {
        snapshotFlow { pending }.filter { it != 0f }.collect { delta ->
            pending -= delta
            if (compensationEnabled && !state.isScrollInProgress && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0))
                state.scrollBy(delta)
        }
    }
    onGloballyPositioned { coordinates ->
        // Conversations have an intermediate composition-budget container. Their
        // header can move that whole container without changing its child's top.
        val top = if (relativeToRoot) coordinates.positionInRoot().y else coordinates.positionInParent().y
        val old = previousTop
        previousTop = top
        if (compensationEnabled && old != null && top != old && !state.isScrollInProgress &&
            (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)) pending += top - old
    }
}

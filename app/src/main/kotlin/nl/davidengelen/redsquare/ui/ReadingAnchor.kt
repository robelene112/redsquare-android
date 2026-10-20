package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

/** Compensate for header growth only while a scrolled reader is idle, never during their gesture. */
internal fun Modifier.preserveReadingAnchor(state: LazyListState): Modifier = composed {
    var previousTop by remember(state) { mutableStateOf<Float?>(null) }
    var pending by remember(state) { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        snapshotFlow { pending }.filter { it != 0f }.collect { delta ->
            pending -= delta
            if (!state.isScrollInProgress && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0))
                state.scrollBy(delta)
        }
    }
    onGloballyPositioned { coordinates ->
        val top = coordinates.positionInParent().y
        val old = previousTop
        previousTop = top
        if (old != null && top != old && !state.isScrollInProgress &&
            (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)) pending += top - old
    }
}

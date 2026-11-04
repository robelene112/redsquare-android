package nl.davidengelen.redsquare.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import nl.davidengelen.redsquare.R

/** A feature retains its reading offset while other destinations render expanded chrome. */
@OptIn(ExperimentalMaterial3Api::class)
internal class ReadingHeaderState(private val behavior: TopAppBarScrollBehavior,
    private val enabled: State<Boolean>, private val scope: CoroutineScope) {
    var inMotion by mutableStateOf(false)
        private set
    private var generation = 0L
    private var revealing: Job? = null
    val navigationHidden by derivedStateOf { enabled.value && behavior.state.collapsedFraction > 0f && !inMotion }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled.value || source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y != 0f) beginMotion()
            return behavior.nestedScrollConnection.onPreScroll(available, source)
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled.value || source != NestedScrollSource.UserInput) return Offset.Zero
            if (consumed.y != 0f || available.y != 0f) beginMotion()
            return behavior.nestedScrollConnection.onPostScroll(consumed, available, source)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!enabled.value) return Velocity.Zero
            beginMotion()
            return behavior.nestedScrollConnection.onPreFling(available)
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!enabled.value) return Velocity.Zero
            val token = beginMotion()
            return try { behavior.nestedScrollConnection.onPostFling(consumed, available) }
            finally { finishMotion(token) }
        }
    }

    private fun beginMotion(): Long {
        revealing?.cancel()
        revealing = null
        generation++
        inMotion = true
        return generation
    }

    private fun finishMotion(token: Long) {
        scope.launch {
            // Measure the last settled header frame before re-enabling idle outage compensation.
            withFrameNanos { }
            if (generation == token) inMotion = false
        }
    }

    fun stopMotionObservation() {
        if (inMotion) {
            generation++
            revealing?.cancel()
            revealing = null
            inMotion = false
        }
    }

    fun showNavigation() {
        val token = beginMotion()
        revealing = scope.launch {
            try {
                animate(behavior.state.heightOffset, 0f, animationSpec = tween(AppSpacing.transitionMillis)) { value, _ ->
                    behavior.state.heightOffset = value
                }
            } finally { finishMotion(token) }
        }
    }

    fun measuredHeight(height: Int) {
        // Typing may remove all chrome. Keep the previous reading fraction through that empty layout.
        if (height <= 0 || behavior.state.heightOffsetLimit == -height.toFloat()) return
        val fraction = behavior.state.collapsedFraction
        behavior.state.heightOffsetLimit = -height.toFloat()
        behavior.state.heightOffset = -height * fraction
    }

    fun offset() = if (enabled.value) behavior.state.heightOffset else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberReadingHeaderState(enabled: Boolean): ReadingHeaderState {
    val currentEnabled = rememberUpdatedState(enabled)
    val canScroll = remember { { currentEnabled.value } }
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState(), canScroll = canScroll)
    val scope = rememberCoroutineScope()
    val state = remember(behavior, scope) { ReadingHeaderState(behavior, currentEnabled, scope) }
    SideEffect { if (!enabled) state.stopMotionObservation() }
    return state
}

/** Read the changing offset in layout, without recomposing a live feature on every drag frame. */
@Composable
internal fun ReadingHeader(state: ReadingHeaderState, content: @Composable () -> Unit) {
    Layout(content = { Column(Modifier.fillMaxWidth()) { content() } }, modifier = Modifier.clipToBounds()) { children, constraints ->
        val child = children.single().measure(constraints.copy(minHeight = 0))
        state.measuredHeight(child.height)
        val offset = state.offset().roundToInt().coerceIn(-child.height, 0)
        layout(child.width, child.height + offset) { child.placeRelative(0, offset) }
    }
}

/** A real accessible equivalent lets readers reach shared controls without changing their list position. */
@Composable
internal fun Modifier.readingNavigation(state: ReadingHeaderState): Modifier {
    val label = stringResource(R.string.show_app_navigation)
    val description = stringResource(R.string.app_navigation_controls)
    return if (state.navigationHidden) semantics {
        contentDescription = description
        customActions = listOf(CustomAccessibilityAction(label) { state.showNavigation(); true })
    } else this
}

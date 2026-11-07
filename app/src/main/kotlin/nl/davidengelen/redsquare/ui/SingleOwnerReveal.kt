package nl.davidengelen.redsquare.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import android.view.ViewTreeObserver
import kotlinx.coroutines.CompletableDeferred

/** Retain route motion when a saveable page holder replaces the rendered content owner. */
internal class SingleOwnerRevealState(initialTarget: Any?) {
    val opacity = Animatable(1f)
    val previous = mutableStateOf(initialTarget)
}

@Composable
internal fun rememberSingleOwnerRevealState(initialTarget: Any?): SingleOwnerRevealState =
    remember { SingleOwnerRevealState(initialTarget) }

/** Animate the new result while disposing the old feature immediately, including all of its effects. */
@Composable
internal fun SingleOwnerReveal(target: Any?, modifier: Modifier = Modifier,
    state: SingleOwnerRevealState = rememberSingleOwnerRevealState(target), content: @Composable () -> Unit) {
    val opacity = state.opacity
    val scope = rememberCoroutineScope()
    fun observe(event: String, value: Float) {
        if (nl.davidengelen.redsquare.BuildConfig.DEBUG) RevealDrawObservation.record(event, target, value,
            scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)
    }
    val motionEnabled = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    var previous by state.previous
    val view = LocalView.current
    val preparingReveal = remember(target, motionEnabled) { motionEnabled && previous != target }
    val firstFrame = remember(view, target, motionEnabled) { FirstContentFrame() }
    DisposableEffect(firstFrame) { onDispose { firstFrame.dispose() } }
    // Keep the preparation frame drawable so initial rendering work happens before
    // the short reveal clock starts. Input and the current feature are already live.
    val preparationOpacity = 0.01f
    LaunchedEffect(target, motionEnabled) {
        observe("effect-start", opacity.value)
        if (!motionEnabled) {
            opacity.snapTo(1f)
            previous = target
        } else if (previous != target) {
            opacity.snapTo(preparationOpacity)
            previous = target
            firstFrame.submitted.await()
            withFrameNanos { }
            observe("animation-start", opacity.value)
            opacity.animateTo(1f, tween(AppSpacing.transitionMillis, easing = LinearEasing))
            observe("animation-end", opacity.value)
        }
    }
    Box(modifier.graphicsLayer {
        alpha = if (!motionEnabled) 1f else if (previous == target) opacity.value else preparationOpacity
        observe("layer-alpha", alpha)
    }.drawWithContent {
        drawContent()
        if (!firstFrame.drawn) {
            observe("first-content-draw", if (!motionEnabled) 1f else if (previous == target) opacity.value else preparationOpacity)
            firstFrame.drawn = true
            // Drawing only records commands. Starting before their first submission can
            // consume most of the reveal while Android prepares the new destination.
            val observer = view.viewTreeObserver.takeIf { preparingReveal && view.isHardwareAccelerated && it.isAlive }
            firstFrame.submit(observer)
            // ViewRoot may have collected callbacks before this draw. Request one
            // subsequent real traversal so the preparation cannot await itself.
            if (observer != null) view.invalidate()
        }
    }) { content() }
}

/** Own one submission callback per destination; disposed destinations never drive a later reveal. */
private class FirstContentFrame {
    val submitted = CompletableDeferred<Unit>()
    var drawn = false
    private var observer: ViewTreeObserver? = null
    private val callback = Runnable { submitted.complete(Unit) }

    fun submit(current: ViewTreeObserver?) {
        if (current == null) submitted.complete(Unit)
        else {
            observer = current
            current.registerFrameCommitCallback(callback)
        }
    }

    fun dispose() {
        observer?.takeIf { it.isAlive }?.unregisterFrameCommitCallback(callback)
        submitted.cancel()
    }
}

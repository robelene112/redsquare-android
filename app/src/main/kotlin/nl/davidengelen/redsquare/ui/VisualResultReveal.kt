package nl.davidengelen.redsquare.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Overlap only the rendered result, with effects outside and controls enabled only for its owner.
 * Stateful destinations use SingleOwnerReveal instead, disposing their old controllers immediately. */
@Composable
internal fun <T> VisualResultReveal(target: T, modifier: Modifier, content: @Composable (T, Boolean) -> Unit) {
    val latest by rememberUpdatedState(target)
    val scope = rememberCoroutineScope()
    val motion = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val visible = remember { mutableStateListOf(VisualResultLayer(target, 1f, prepared = true)) }
    val incoming = visible.firstOrNull { it.target == target }
        ?: VisualResultLayer(target, 0.01f).also { visible.add(it) }
    val view = LocalView.current
    val firstFrame = remember(view, incoming, motion) { FirstContentFrame() }
    DisposableEffect(firstFrame) { onDispose { firstFrame.dispose() } }
    LaunchedEffect(incoming, motion, firstFrame) {
        if (!motion) {
            incoming.opacity.snapTo(1f)
            incoming.prepared = true
        } else {
            if (!incoming.prepared) {
                // A rapidly revisited, never-prepared layer may already have
                // faded to zero. Its preparation must remain drawable.
                if (incoming.opacity.value < 0.01f) incoming.opacity.snapTo(0.01f)
                firstFrame.submitted.await()
                incoming.prepared = true
                withFrameNanos { }
            }
            if (incoming.target != latest) return@LaunchedEffect
            // Keep the existing rendered layers until the new positive-opacity
            // preparation frame has reached the hardware submission boundary.
            coroutineScope {
                visible.toList().forEach { layer ->
                    val destination = if (layer === incoming) 1f else 0f
                    if (layer.opacity.value != destination) launch {
                        layer.opacity.animateTo(destination,
                            tween(AppSpacing.transitionMillis, easing = LinearEasing))
                    }
                }
            }
        }
        if (incoming.target == latest) visible.removeAll { it !== incoming }
    }
    Box(modifier) {
        val layers = if (motion) visible.toList() else listOf(incoming)
        layers.forEach { layer -> key(layer) {
            val current = layer.target == latest
            Box(Modifier.fillMaxSize().then(if (current) Modifier else Modifier.clearAndSetSemantics { })
                .graphicsLayer { alpha = if (motion) layer.opacity.value else 1f }
                .drawWithContent {
                    drawContent()
                    if (layer === incoming && motion && !incoming.prepared && !firstFrame.drawn) {
                        firstFrame.drawn = true
                        val observer = view.viewTreeObserver.takeIf { view.isHardwareAccelerated && it.isAlive }
                        firstFrame.submit(observer)
                        // A callback registered during drawing may belong to the
                        // next traversal. Submit it without starting the fade yet.
                        if (observer != null) view.invalidate()
                    }
                }) {
                content(layer.target, current)
            }
        } }
    }
}

/** Only inert rendered content is retained; feature effects remain with the caller. */
private class VisualResultLayer<T>(val target: T, alpha: Float, var prepared: Boolean = false) {
    val opacity = Animatable(alpha)
}

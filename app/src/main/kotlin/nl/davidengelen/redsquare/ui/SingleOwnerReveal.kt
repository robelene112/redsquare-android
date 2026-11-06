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
import kotlinx.coroutines.CompletableDeferred

/** Animate the new result while disposing the old feature immediately, including all of its effects. */
@Composable
internal fun SingleOwnerReveal(target: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val opacity = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    fun observe(event: String, value: Float) {
        if (nl.davidengelen.redsquare.BuildConfig.DEBUG) RevealDrawObservation.record(event, target, value,
            scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)
    }
    val motionEnabled = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    var previous by remember { mutableStateOf(target) }
    val firstDraw = remember(target, motionEnabled) { CompletableDeferred<Unit>() }
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
            firstDraw.await()
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
        if (!firstDraw.isCompleted) {
            observe("first-content-draw", if (!motionEnabled) 1f else if (previous == target) opacity.value else preparationOpacity)
            firstDraw.complete(Unit)
        }
    }) { content() }
}

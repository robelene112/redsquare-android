package nl.davidengelen.redsquare.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Animate the new result while disposing the old feature immediately, including all of its effects. */
@Composable
internal fun SingleOwnerReveal(target: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val opacity = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (previous != target) {
            previous = target
            opacity.snapTo(0f)
            opacity.animateTo(1f, tween(AppSpacing.transitionMillis))
        }
    }
    Box(modifier.graphicsLayer { alpha = opacity.value }) { content() }
}

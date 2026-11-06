package nl.davidengelen.redsquare.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import nl.davidengelen.redsquare.BuildConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Overlap only the rendered result, with effects outside and controls enabled only for its owner.
 * Stateful destinations use SingleOwnerReveal instead, disposing their old controllers immediately. */
@Composable
internal fun <T> VisualResultReveal(target: T, modifier: Modifier, content: @Composable (T, Boolean) -> Unit) {
    val latest by rememberUpdatedState(target)
    val observedEasing = androidx.compose.runtime.remember {
        Easing { fraction ->
            val value = LinearEasing.transform(fraction)
            if (BuildConfig.DEBUG) android.util.Log.d("DEBUG-m81", "easing:${android.os.SystemClock.elapsedRealtimeNanos()}:$fraction:$value")
            value
        }
    }
    Crossfade(targetState = target, modifier = modifier,
        animationSpec = tween(AppSpacing.transitionMillis, easing = observedEasing), label = "Loaded result") { rendered ->
        val current = rendered == latest
        Box(Modifier.fillMaxSize().then(if (current) Modifier else Modifier.clearAndSetSemantics { })) {
            content(rendered, current)
        }
    }
}

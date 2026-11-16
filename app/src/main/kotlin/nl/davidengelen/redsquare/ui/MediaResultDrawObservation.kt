package nl.davidengelen.redsquare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import android.view.ViewTreeObserver
import nl.davidengelen.redsquare.BuildConfig

/** Optional debug timing only. Never requests a draw, advances motion or records media content. */
internal object MediaResultDrawObservation {
    fun interface Listener {
        fun observe(event: String, targetIdentity: Int, monotonicNanos: Long, elapsedNanos: Long)
    }

    @Volatile var listener: Listener? = null

    fun record(event: String, target: Any?) {
        if (!BuildConfig.DEBUG) return
        val current = listener ?: return
        try {
            current.observe(event, System.identityHashCode(target), System.nanoTime(),
                android.os.SystemClock.elapsedRealtimeNanos())
        } catch (_: Throwable) { /* Optional timing cannot replace a rendered result or failure. */ }
    }
}

@Composable
internal fun observeMediaResultDraw(target: Any?): Modifier {
    if (!BuildConfig.DEBUG || MediaResultDrawObservation.listener == null) return Modifier
    val view = LocalView.current
    val timing = remember(view, target) { MediaDrawTiming(target) }
    DisposableEffect(timing) {
        MediaResultDrawObservation.record("content-composed", target)
        val observer = view.viewTreeObserver
        val before = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) {
                    observer.removeOnPreDrawListener(this)
                    MediaResultDrawObservation.record("pre-draw", target)
                    if (view.isHardwareAccelerated) observer.registerFrameCommitCallback(timing.committed)
                }
                return true
            }
        }
        if (observer.isAlive) observer.addOnPreDrawListener(before)
        onDispose {
            if (observer.isAlive) {
                observer.removeOnPreDrawListener(before)
                observer.unregisterFrameCommitCallback(timing.committed)
            }
        }
    }
    return Modifier.drawWithContent {
        MediaResultDrawObservation.record("draw-start", target)
        drawContent()
        MediaResultDrawObservation.record("draw-end", target)
    }
}

private class MediaDrawTiming(target: Any?) {
    val committed = Runnable { MediaResultDrawObservation.record("observed-window-frame-commit", target) }
}

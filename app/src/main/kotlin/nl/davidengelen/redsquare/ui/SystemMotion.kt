package nl.davidengelen.redsquare.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * Compose 1.11.4 caches the previous window's scale across an unsubscribed setting change.
 * Keep its normal lifecycle/frame clock, with a freshly observed scale for each window.
 * Install once at Application startup so Compose tests can still substitute their factory.
 */
@OptIn(InternalComposeUiApi::class)
internal fun installSystemMotion() {
    WindowRecomposerPolicy.setFactory(WindowRecomposerFactory { root ->
        val lifecycle = requireNotNull(root.findViewTreeLifecycleOwner()).lifecycle
        val scale = WindowMotionScale(root, lifecycle)
        try {
            root.createLifecycleAwareWindowRecomposer(coroutineContext = scale, lifecycle = lifecycle)
        } catch (failure: Throwable) {
            scale.close()
            throw failure
        }
    })
}

private class WindowMotionScale(private val root: View, private val lifecycle: Lifecycle) :
    ContentObserver(Handler(Looper.getMainLooper())), MotionDurationScale,
    LifecycleEventObserver, View.OnAttachStateChangeListener {
    private val resolver = root.context.contentResolver
    private var current by mutableFloatStateOf(1f)
    private var closed = false
    override val scaleFactor: Float get() = current

    init {
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, this)
        try {
            // Register first, then read: no change can fall between the initial value and subscription.
            refresh()
            root.addOnAttachStateChangeListener(this)
            lifecycle.addObserver(this)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    override fun onChange(selfChange: Boolean) { if (!closed) refresh() }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> if (!closed) refresh()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }
    override fun onViewAttachedToWindow(view: View) = Unit
    override fun onViewDetachedFromWindow(view: View) = close()

    private fun refresh() {
        current = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            .takeIf { it.isFinite() && it >= 0f } ?: 1f
    }

    fun close() {
        if (closed) return
        closed = true
        resolver.unregisterContentObserver(this)
        root.removeOnAttachStateChangeListener(this)
        lifecycle.removeObserver(this)
    }
}

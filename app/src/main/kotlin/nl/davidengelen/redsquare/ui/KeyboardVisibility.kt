package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/** Keep conversation space while the IME occupies the window, including visibility transitions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun keyboardOccupiesWindow(): Boolean {
    val visible = WindowInsets.isImeVisible
    val bottom = if (visible) null else WindowInsets.ime.getBottom(LocalDensity.current)
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        if (nl.davidengelen.redsquare.BuildConfig.DEBUG) {
            val native = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
            KeyboardVisibilityProbe.record(System.identityHashCode(view),
                "type=${view.javaClass.name} focus=${view.hasWindowFocus()} attached=${view.isAttachedToWindow} " +
                    "composeVisible=$visible composeBottom=$bottom nativeVisible=${native?.isVisible(ime)} " +
                    "nativeBottom=${native?.getInsets(ime)?.bottom}")
        }
    }
    return visible || requireNotNull(bottom) > 0
}

/** TEMPORARY diagnosis only. Remove before final source; no input or identity values retained. */
internal object KeyboardVisibilityProbe {
    private val last = linkedMapOf<Int, String>()
    private val samples = java.util.ArrayDeque<String>()
    @Synchronized fun record(view: Int, value: String) {
        if (last[view] == value) return
        last[view] = value
        if (last.size > 128) last.remove(last.keys.first())
        samples.addLast("${android.os.SystemClock.uptimeMillis()} view=$view $value")
        while (samples.size > 256) samples.removeFirst()
    }
    @Synchronized fun snapshot(): String = samples.joinToString("\n") + "\n"
}

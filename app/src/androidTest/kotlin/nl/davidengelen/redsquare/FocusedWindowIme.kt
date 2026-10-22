package nl.davidengelen.redsquare

import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry

/** Observe the actually focused window, including full-window Chat key dialogs. */
internal fun focusedWindowImeBottom(): Int {
    var result = 0
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
        val insets = root?.let { ViewCompat.getRootWindowInsets(it) }
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            result = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    }
    return result
}

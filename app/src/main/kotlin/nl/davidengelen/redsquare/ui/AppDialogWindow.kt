package nl.davidengelen.redsquare.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Call inside the dialog content so these settings belong to its actual window. */
@Composable
internal fun AppDialogWindow(styleSystemBars: Boolean = false) {
    val view = LocalView.current
    val window = (view.parent as DialogWindowProvider).window
    val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        // Platform shadow surface insets misalign API29 accessibility windows.
        // Material surfaces and the existing dim keep their normal visual hierarchy.
        (view.parent as android.view.View).elevation = 0f
        if (window.decorView.elevation != 0f) window.setElevation(0f)
        // Floating dialogs retain their platform bar appearance over the dimmed parent.
        if (styleSystemBars) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

/** Route initial keyboard entry and Escape in the actual sheet window, independently of touch focus. */
@Composable
internal fun SheetKeyboardWindow(onDismiss: () -> Unit, onKeyboardEntry: () -> Boolean) {
    val view = LocalView.current
    val window = (view.parent as DialogWindowProvider).window
    val dismiss by rememberUpdatedState(onDismiss)
    val enterContent by rememberUpdatedState(onKeyboardEntry)
    DisposableEffect(window) {
        val previous = requireNotNull(window.callback)
        var tracking = false
        var enteredContent = false
        var entryKey: Int? = null
        val callback = object : Window.Callback by previous {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) {
                    if (event.keyCode == entryKey) {
                        if (event.action == KeyEvent.ACTION_UP) entryKey = null
                        return true
                    }
                    val entering = !enteredContent && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                        event.hasNoModifiers() && event.keyCode in setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DPAD_DOWN)
                    if (entering && enterContent()) {
                        enteredContent = true
                        entryKey = event.keyCode
                        return true
                    }
                    return previous.dispatchKeyEvent(event)
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) tracking = true
                    KeyEvent.ACTION_UP -> {
                        val shouldDismiss = tracking && !event.isCanceled
                        tracking = false
                        if (shouldDismiss) dismiss()
                    }
                }
                return true
            }
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) { tracking = false; entryKey = null }
                previous.onWindowFocusChanged(hasFocus)
            }
        }
        window.callback = callback
        onDispose { if (window.callback === callback) window.callback = previous }
    }
}

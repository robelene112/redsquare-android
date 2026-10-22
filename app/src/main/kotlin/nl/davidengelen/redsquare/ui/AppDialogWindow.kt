package nl.davidengelen.redsquare.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

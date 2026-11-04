package nl.davidengelen.redsquare.ui

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.transition.Fade
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** Same native menu content, with a pre-S window that does not allocate shadow surface insets. */
@Composable
internal fun AppMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DropdownMenu(expanded, onDismissRequest, shape = RoundedCornerShape(12.dp), content = content)
    } else LegacyAppMenu(expanded, onDismissRequest, content)
}

/** AndroidX's pre-S popup surface-inset issue (b/232788477) offsets native window bounds.
 * Configure this platform PopupWindow before attachment, with room for the Material shadow
 * inside its bounds. Content, lifecycle, Back/outside dismissal and input remain native.
 */
@Composable
private fun LegacyAppMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val view = LocalView.current
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    var anchor by remember { mutableStateOf<ComposeRect?>(null) }
    Layout(content = {}, modifier = Modifier.onGloballyPositioned { coordinates ->
        anchor = coordinates.parentLayoutCoordinates?.boundsInWindow()
    }) { _, _ -> layout(0, 0) {} }

    DisposableEffect(expanded, anchor, view, parent) {
        val bounds = anchor
        if (!expanded || bounds == null) return@DisposableEffect onDispose { }
        if (!view.isAttachedToWindow || view.windowToken == null) {
            currentDismiss()
            return@DisposableEffect onDispose { }
        }
        val viewport = Rect().also(view::getWindowVisibleDisplayFrame)
        val margin = (8 * view.resources.displayMetrics.density).roundToInt()
        val availableWidth = (viewport.width() - margin * 2).coerceAtLeast(1)
        val availableHeight = (viewport.height() - margin * 2).coerceAtLeast(1)
        val rootOrigin = IntArray(2).also(view.rootView::getLocationOnScreen)
        val panel = ComposeView(view.context).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parent)
            setViewTreeLifecycleOwner(view.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(view.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(view.findViewTreeSavedStateRegistryOwner())
            setContent {
                Box(Modifier.padding(8.dp).semantics { popup() }) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 4.dp) {
                        Column(Modifier.width(IntrinsicSize.Max).verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp), content = currentContent)
                    }
                }
            }
        }
        val host = object : FrameLayout(view.context) {
            private var escapeTracking = false
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in setOf(KeyEvent.KEYCODE_TAB,
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) panel.requestFocus()
                    return super.dispatchKeyEvent(event)
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) escapeTracking = true
                    KeyEvent.ACTION_UP -> {
                        if (escapeTracking && !event.isCanceled) currentDismiss()
                        escapeTracking = false
                    }
                }
                return true
            }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) escapeTracking = false
            }
        }.apply {
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val window = PopupWindow(host, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            isOutsideTouchable = true
            isClippingEnabled = true
            setIsLaidOutInScreen(true)
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            enterTransition = Fade().apply { duration = AppSpacing.transitionMillis.toLong() }
            exitTransition = Fade().apply { duration = AppSpacing.transitionMillis.toLong() }
            setOnDismissListener { currentDismiss() }
        }
        // Touch-mode rows may decline focus. The host remains a real key target
        // for Escape, then transfers navigation keys into the Compose subtree.
        panel.doOnLayout { host.requestFocus() }
        try {
            host.measure(View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST))
            val start = rootOrigin[0] + bounds.left.roundToInt() - margin
            val end = rootOrigin[0] + bounds.right.roundToInt() - host.measuredWidth + margin
            val x = (if (start + host.measuredWidth <= viewport.right) start else end)
                .coerceIn(viewport.left, (viewport.right - host.measuredWidth).coerceAtLeast(viewport.left))
            val below = rootOrigin[1] + bounds.bottom.roundToInt() - margin
            val above = rootOrigin[1] + bounds.top.roundToInt() - host.measuredHeight + margin
            val y = (if (below + host.measuredHeight <= viewport.bottom) below else above)
                .coerceIn(viewport.top, (viewport.bottom - host.measuredHeight).coerceAtLeast(viewport.top))
            // Anchor coordinates are physical, independent of text layout direction.
            window.showAtLocation(view, Gravity.TOP or Gravity.LEFT, x, y)
        } catch (failure: Throwable) {
            window.setOnDismissListener(null)
            try { window.dismiss() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            try { panel.disposeComposition() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
        onDispose {
            window.setOnDismissListener(null)
            window.dismiss()
            // An exit transition keeps its last rendered view until native detach.
            // The composition strategy disposes then; failed/unattached views are owned here.
            if (!panel.isAttachedToWindow) panel.disposeComposition()
        }
    }
}

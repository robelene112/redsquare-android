package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Reveal the complete field again when the actual keyboard changes its viewport. */
@Composable internal fun Modifier.spaceEditorFocusVisibility(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val keyboardBottom = WindowInsets.ime.getBottom(density)
    val margin = with(density) { 8.dp.toPx() }
    LaunchedEffect(focused, size, keyboardBottom) {
        if (focused && size != IntSize.Zero) requester.bringIntoView(
            Rect(0f, -margin, size.width.toFloat(), size.height + margin))
    }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }.onSizeChanged { size = it }
}

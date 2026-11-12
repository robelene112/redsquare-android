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
internal fun keyboardOccupiesWindow(): Boolean =
    WindowInsets.isImeVisible || WindowInsets.ime.getBottom(LocalDensity.current) > 0

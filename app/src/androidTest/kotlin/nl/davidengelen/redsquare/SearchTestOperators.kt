package nl.davidengelen.redsquare

import androidx.compose.ui.test.*

/** Select the visible compact person row, excluding the editable search query. */
internal fun compactSearchKey(key: String) = "${key.take(8)}…${key.takeLast(6)}"

internal fun searchPersonResult(key: String): SemanticsMatcher =
    hasText(compactSearchKey(key)) and hasClickAction() and !hasSetTextAction()

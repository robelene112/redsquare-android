package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

/** Keep history readable while the complete composer, including its preview, grows. */
@Composable
internal fun ConversationContent(modifier: Modifier,
    history: @Composable (Modifier) -> Unit, composer: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val historyMinimum = if (fontScale >= 2f) 48.dp else 96.dp
        val compositionMinimum = 56.dp * fontScale + 48.dp
        val reservedHistory = minOf(historyMinimum, (maxHeight - compositionMinimum).coerceAtLeast(0.dp))
        val composerMaximum = maxHeight - reservedHistory
        Column(Modifier.fillMaxSize()) {
            history(Modifier.weight(1f))
            composer(Modifier.heightIn(max = composerMaximum))
        }
    }
}

/** Scroll supplemental content independently; the editor and Send keep their own row. */
@Composable
internal fun ConversationComposerFrame(session: String, modifier: Modifier, hasPreview: Boolean,
    supplemental: @Composable () -> Unit, editor: @Composable (Modifier) -> Unit) = key(session) {
    BoxWithConstraints(modifier) {
        val editorMinimum = 56.dp * LocalDensity.current.fontScale
        val editorMaximum = minOf(maxHeight, maxOf(editorMinimum,
            if (hasPreview) maxHeight / 2 else (maxHeight - 48.dp).coerceAtLeast(0.dp)))
        val previewLabel = stringResource(R.string.chat_composition_preview)
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .semantics { contentDescription = previewLabel }) { supplemental() }
            editor(Modifier.heightIn(max = editorMaximum))
        }
    }
}

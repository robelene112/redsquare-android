package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Reference-shell action sheet; keep its exit animation mounted after selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesignSheet(expanded: Boolean, title: String, onDismiss: () -> Unit, contentKey: Any? = null,
    footer: (@Composable ColumnScope.(Modifier) -> Unit)? = null,
    content: @Composable ColumnScope.(Modifier) -> Unit) {
    val firstAction = remember { FocusRequester() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(false) }
    var dismissalRequest by remember { mutableIntStateOf(0) }
    val requestDismiss: () -> Unit = { onDismiss(); dismissalRequest++ }
    LaunchedEffect(expanded, dismissalRequest) {
        if (expanded) {
            // Material may hide the sheet before asking the caller to dismiss.
            // A caller reviewing unsaved work can keep it open.
            if (mounted) state.show() else mounted = true
        }
        else if (mounted) {
            state.hide()
            mounted = false
        }
    }
    if (mounted) ModalBottomSheet(onDismissRequest = requestDismiss, sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SheetKeyboardWindow(requestDismiss) { firstAction.requestFocus(FocusDirection.Enter) }
        val scroll = key(contentKey) { rememberScrollState() }
        val body: @Composable ColumnScope.() -> Unit = {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            content(Modifier.focusRequester(firstAction))
        }
        val latestBody by rememberUpdatedState(body)
        val latestFooter by rememberUpdatedState(footer)
        val stableBody = remember(contentKey) { movableContentOf<ColumnScope> { parent -> latestBody.invoke(parent) } }
        val stableFooter = remember(contentKey) { movableContentOf<ColumnScope> { parent ->
            latestFooter?.invoke(parent, Modifier.focusRequester(firstAction))
        } }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Preserve room for a complete focused field at large type or with
            // the IME open. A pinned footer must not consume the whole form.
            val scrollActions = footer != null && maxHeight < 160.dp * LocalDensity.current.fontScale + 144.dp
            if (footer == null || scrollActions) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).focusGroup()
                    .padding(horizontal = AppSpacing.screen).padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stableBody(this)
                    if (footer != null) {
                        HorizontalDivider()
                        stableFooter(this)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().focusGroup()) {
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)
                        .padding(horizontal = AppSpacing.screen).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) { stableBody(this) }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).padding(top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stableFooter(this)
                    }
                }
            }
        }
    }
}

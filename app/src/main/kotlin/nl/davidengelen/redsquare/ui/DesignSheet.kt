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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Reference-shell action sheet; keep its exit animation mounted after selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesignSheet(expanded: Boolean, title: String, onDismiss: () -> Unit, contentKey: Any? = null,
    content: @Composable ColumnScope.(Modifier) -> Unit) {
    val firstAction = remember { FocusRequester() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) mounted = true
        else if (mounted) {
            state.hide()
            mounted = false
        }
    }
    if (mounted) ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        SheetKeyboardWindow(onDismiss) { firstAction.requestFocus(FocusDirection.Enter) }
        val scroll = key(contentKey) { rememberScrollState() }
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).focusGroup()
            .padding(horizontal = AppSpacing.screen).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            content(Modifier.focusRequester(firstAction))
        }
    }
}

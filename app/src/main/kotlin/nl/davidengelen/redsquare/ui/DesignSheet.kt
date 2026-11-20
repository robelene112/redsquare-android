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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Reference-shell action sheet; keep its exit animation mounted after selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesignSheet(expanded: Boolean, title: String, onDismiss: () -> Unit, contentKey: Any? = null,
    footer: (@Composable ColumnScope.(Modifier) -> Unit)? = null, onHidden: () -> Unit = {},
    content: @Composable ColumnScope.(Modifier) -> Unit) {
    val firstAction = remember { FocusRequester() }
    val hidden by rememberUpdatedState(onHidden)
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
        else {
            if (mounted) {
                state.hide()
                mounted = false
            }
            hidden()
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
        if (footer == null) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).focusGroup()
                .padding(horizontal = AppSpacing.screen).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
        } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            var footerPixels by remember(contentKey) { mutableIntStateOf(0) }
            val sheetScroll = key(contentKey) { rememberScrollState() }
            val footerHeight = with(density) { footerPixels.toDp() }
            val remaining = (maxHeight - footerHeight).coerceAtLeast(0.dp)
            val bodyHeight = if (remaining >= 160.dp * density.fontScale) remaining else maxHeight
            // Both scroll/focus ancestries stay mounted through IME changes.
            // With room, only the body scrolls and actions stay pinned. When
            // constrained, the body gets a full viewport and the outer scroll
            // makes the actions reachable without replacing the input session.
            Column(Modifier.fillMaxWidth().verticalScroll(sheetScroll).focusGroup()) {
                Column(Modifier.fillMaxWidth().heightIn(max = bodyHeight).verticalScroll(scroll)
                    .padding(horizontal = AppSpacing.screen).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
                Column(Modifier.fillMaxWidth().onSizeChanged { footerPixels = it.height }) {
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).padding(top = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        footer(Modifier.focusRequester(firstAction))
                    }
                }
            }
        }
    }
}

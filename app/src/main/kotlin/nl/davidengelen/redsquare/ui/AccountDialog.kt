package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.davidengelen.redsquare.R

/** One real account task window; body state/effects and cancellation remain owned by the caller. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountDialog(title: String, closeLabel: String, onDismissRequest: () -> Unit,
    dismissEnabled: Boolean = true, backLabel: String? = null,
    headerExtra: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null) {
    Dialog(onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = dismissEnabled, dismissOnClickOutside = dismissEnabled)) {
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.fillMaxHeight().widthIn(max = 600.dp).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismissRequest, enabled = dismissEnabled,
                            modifier = Modifier.size(48.dp).semantics { this.text = AnnotatedString(backLabel ?: closeLabel) }) {
                            Icon(painterResource(if (backLabel == null) R.drawable.ic_close else R.drawable.ic_back), null)
                        }
                        Text(title, Modifier.weight(1f).padding(horizontal = AppSpacing.small).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (headerExtra != null) Column(Modifier.padding(horizontal = AppSpacing.screen,
                        vertical = AppSpacing.small)) { headerExtra() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = AppSpacing.screen,
                        vertical = AppSpacing.gap)) { text() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
                        if (dismissButton != null) dismissButton()
                        confirmButton()
                    }
                }
            }
        }
    }
}

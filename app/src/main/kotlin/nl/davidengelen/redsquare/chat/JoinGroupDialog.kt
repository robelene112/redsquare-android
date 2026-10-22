package nl.davidengelen.redsquare.chat

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.GroupInvitation
import nl.davidengelen.redsquare.saito.NamedChatGroup
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.ui.AppDialogWindow
import nl.davidengelen.redsquare.ui.AppSpacing

/** Clipboard input and unsigned claims never choose a node or trigger membership by themselves. */
@Composable
internal fun JoinGroupDialog(backend: SaitoBackend, busy: Boolean, knownGroups: List<NamedChatGroup>,
    expectedGroupId: String? = null, onDismiss: () -> Unit, onJoin: (GroupInvitation) -> Unit) {
    val context = LocalContext.current
    var input by rememberSaveable(backend, expectedGroupId) { mutableStateOf("") }
    var invitation by remember(backend, expectedGroupId) { mutableStateOf<GroupInvitation?>(null) }
    var validationError by remember(backend, expectedGroupId) { mutableIntStateOf(0) }
    var resolving by remember(backend, expectedGroupId) { mutableStateOf(false) }
    var savedDetails by remember(backend, expectedGroupId) { mutableStateOf(false) }
    val currentGroups by rememberUpdatedState(knownGroups)
    val scope = rememberCoroutineScope()
    val working = busy || resolving
    val preview = invitation
    Dialog(onDismissRequest = { if (!busy && !resolving) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !working)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val scroll = rememberScrollState()
        val feedbackView = remember { BringIntoViewRequester() }
        var feedbackHeight by remember { mutableIntStateOf(0) }
        var viewportHeight by remember { mutableIntStateOf(0) }
        val feedback = if (validationError == 0) null else stringResource(validationError)
        LaunchedEffect(feedback, feedbackHeight, viewportHeight) {
            if (feedback != null && feedbackHeight > 0 && viewportHeight > 0) feedbackView.bringIntoView()
        }
        // The preview begins with trust and node information, regardless of input/error scroll position.
        LaunchedEffect(preview) { if (preview != null) scroll.scrollTo(0) }
        fun requestPreview() {
            if (busy || resolving || input.isBlank()) return
            val raw = input
            validationError = 0
            resolving = true
            scope.launch {
                try {
                    val parsed = withContext(Dispatchers.Default) { GroupInvitation.parse(raw) }
                    if (expectedGroupId != null && parsed.id != expectedGroupId) {
                        validationError = R.string.group_invitation_different_group
                        return@launch
                    }
                    val known = currentGroups.firstOrNull { it.id == parsed.id }
                    val resolved = withContext(Dispatchers.Default) { known?.let { parsed.usingKnownGroup(it) } ?: parsed }
                    savedDetails = known != null
                    invitation = resolved
                    focus.clearFocus()
                    keyboard?.hide()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { validationError = R.string.group_invitation_invalid }
                finally { resolving = false }
            }
        }
        fun paste() {
            if (busy || resolving) return
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                if (text.isNullOrBlank() || text.length > 2 * SaitoTransaction.MAX_MESSAGE_BYTES) {
                    validationError = R.string.group_invitation_paste_error
                    return
                }
                input = text.toString()
                validationError = 0
            } catch (_: Exception) { validationError = R.string.group_invitation_paste_error }
        }
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !working, onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.group_cancel))
                    }
                    Text(stringResource(R.string.group_join), Modifier.weight(1f).padding(start = AppSpacing.small)
                        .semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                val contentLabel = stringResource(R.string.group_join_content)
                Column(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportHeight = it.height }
                    .verticalScroll(scroll).semantics { contentDescription = contentLabel }.padding(AppSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    if (preview == null) {
                        if (expectedGroupId != null) {
                            Text(stringResource(R.string.group_recovery_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer { Text(expectedGroupId, style = MaterialTheme.typography.bodySmall) }
                        }
                        OutlinedButton(enabled = !working, onClick = ::paste, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.group_invitation_paste))
                        }
                        OutlinedTextField(input, onValueChange = { value ->
                            if (!busy && !resolving) { input = value; validationError = 0 }
                        }, label = { Text(stringResource(R.string.group_invitation_link)) },
                            modifier = Modifier.fillMaxWidth(), enabled = !working, maxLines = 4,
                            isError = validationError != 0,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false,
                                imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { requestPreview() }))
                        if (feedback != null) Text(feedback, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth()
                                .bringIntoViewRequester(feedbackView).onSizeChanged { feedbackHeight = it.height }
                                .semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        Text(preview.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (savedDetails) R.string.group_invitation_saved else R.string.group_invitation_trust),
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.group_join_backend, backend.domain), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.group_privacy), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        Text(stringResource(R.string.group_admin_label), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(preview.admin, style = MaterialTheme.typography.bodySmall) }
                        Text(stringResource(R.string.group_inviter_label), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(preview.sender, style = MaterialTheme.typography.bodySmall) }
                        Text(stringResource(R.string.group_id_label), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(preview.id, style = MaterialTheme.typography.bodySmall) }
                        TextButton(enabled = !working, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                            invitation = null; savedDetails = false; validationError = 0
                        }) { Text(stringResource(R.string.group_invitation_edit)) }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !working, onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.group_cancel))
                    }
                    Button(enabled = !working && (preview != null || input.isNotBlank()),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                            if (!busy && !resolving) {
                                if (preview != null) onJoin(preview) else requestPreview()
                            }
                        }) { Text(stringResource(when {
                            busy -> R.string.group_joining
                            resolving -> R.string.group_invitation_reading
                            preview != null -> R.string.group_join_confirm
                            else -> R.string.group_preview
                        })) }
                }
            }
        }
    }
}

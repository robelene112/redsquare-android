package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.ui.AppDialogWindow
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** The two deliberate key-entry actions share validation, keyboard and feedback behavior. */
internal enum class ChatKeyPurpose(val title: Int, val action: Int, val working: Int, val privacy: Int, val selfError: Int) {
    PRIVATE(R.string.chat_new_private, R.string.chat_open, R.string.chat_opening, R.string.chat_private, R.string.chat_key_self),
    GROUP_MEMBER(R.string.group_add_member_title, R.string.group_add_member, R.string.group_checking_key,
        R.string.group_privacy, R.string.group_owner_included)
}

/** A deliberate key fallback leaves the main Chat destination free for known contacts. */
@Composable
internal fun ChatPublicKeyDialog(owner: String, purpose: ChatKeyPurpose, busy: Boolean, saveFailed: Boolean = false,
    selectionError: (String) -> Int = { 0 }, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var publicKey by rememberSaveable(owner, purpose) { mutableStateOf("") }
    var validationError by rememberSaveable(owner, purpose) { mutableIntStateOf(0) }
    var validating by remember(owner, purpose) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val personal = LocalPersonalControls.current
    val working = busy || validating
    Dialog(onDismissRequest = { if (!working) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !working)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val feedback = when {
            validationError != 0 -> stringResource(validationError)
            saveFailed -> stringResource(R.string.chat_contact_save_error)
            else -> null
        }
        val feedbackView = remember { BringIntoViewRequester() }
        var feedbackHeight by remember { mutableIntStateOf(0) }
        var viewportHeight by remember { mutableIntStateOf(0) }
        // Re-evaluate during notice expansion and real IME resizing, retaining both keyboard and draft.
        LaunchedEffect(feedback, feedbackHeight, viewportHeight) {
            if (feedback != null && feedbackHeight > 0 && viewportHeight > 0) feedbackView.bringIntoView()
        }
        fun open() {
            if (busy || validating || personal?.ready != true || publicKey.isBlank()) return
            val address = publicKey.trim()
            validating = true
            scope.launch {
                try {
                    val error = withContext(Dispatchers.Default) {
                        when {
                            !SaitoWallet.isPublicKey(address) -> R.string.chat_key_invalid
                            address == owner -> purpose.selfError
                            else -> 0
                        }
                    }
                    if (publicKey.trim() != address) return@launch
                    validationError = if (error != 0) error else selectionError(address)
                    if (validationError == 0) {
                        focus.clearFocus()
                        keyboard?.hide()
                        onConfirm(address)
                    }
                } finally { validating = false }
            }
        }
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !working, onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.group_cancel))
                    }
                    Text(stringResource(purpose.title), Modifier.weight(1f).padding(start = AppSpacing.small)
                        .semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportHeight = it.height }
                    .verticalScroll(rememberScrollState()).padding(AppSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    PersonalControlsStatus(personal)
                    OutlinedTextField(publicKey, onValueChange = { publicKey = it; validationError = 0 },
                        label = { Text(stringResource(R.string.chat_public_key)) }, singleLine = true,
                        enabled = !busy, isError = validationError != 0, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false,
                            imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { open() }))
                    InlineStatus(feedback, StatusTone.ERROR, modifier = Modifier.bringIntoViewRequester(feedbackView)
                        .onSizeChanged { feedbackHeight = it.height })
                    Text(stringResource(purpose.privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = ::open, enabled = !working && personal?.ready == true && publicKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small).heightIn(min = 48.dp)) {
                    Text(stringResource(if (working) purpose.working else purpose.action))
                }
            }
        }
    }
}

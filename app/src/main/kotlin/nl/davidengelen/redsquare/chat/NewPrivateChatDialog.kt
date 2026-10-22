package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** A deliberate key fallback leaves the main Chat destination free for known contacts. */
@Composable
internal fun NewPrivateChatDialog(owner: String, busy: Boolean, saveFailed: Boolean,
    onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var publicKey by rememberSaveable(owner) { mutableStateOf("") }
    var validationError by rememberSaveable(owner) { mutableIntStateOf(0) }
    var validating by remember(owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val personal = LocalPersonalControls.current
    val working = busy || validating
    Dialog(onDismissRequest = { if (!working) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !working)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun open() {
            if (busy || validating || personal?.ready != true || publicKey.isBlank()) return
            val address = publicKey.trim()
            validating = true
            scope.launch {
                try {
                    val error = withContext(Dispatchers.Default) {
                        when {
                            !SaitoWallet.isPublicKey(address) -> R.string.chat_key_invalid
                            address == owner -> R.string.chat_key_self
                            else -> 0
                        }
                    }
                    if (publicKey.trim() != address) return@launch
                    validationError = error
                    if (validationError == 0) {
                        focus.clearFocus()
                        keyboard?.hide()
                        onOpen(address)
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
                    Text(stringResource(R.string.chat_new_private), Modifier.weight(1f).padding(horizontal = AppSpacing.small)
                        .semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    PersonalControlsStatus(personal)
                    OutlinedTextField(publicKey, onValueChange = { publicKey = it; validationError = 0 },
                        label = { Text(stringResource(R.string.chat_public_key)) }, singleLine = true,
                        enabled = !busy, isError = validationError != 0, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false,
                            imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { open() }))
                    InlineStatus(if (validationError != 0) stringResource(validationError) else null, StatusTone.ERROR)
                    InlineStatus(if (saveFailed) stringResource(R.string.chat_contact_save_error) else null, StatusTone.ERROR)
                    Text(stringResource(R.string.chat_private), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = ::open, enabled = !working && personal?.ready == true && publicKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(AppSpacing.screen).heightIn(min = 48.dp)) {
                    Text(stringResource(if (working) R.string.chat_opening else R.string.chat_open))
                }
            }
        }
    }
}

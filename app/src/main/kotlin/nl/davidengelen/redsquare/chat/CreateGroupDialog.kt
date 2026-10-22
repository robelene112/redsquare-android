package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.ui.AppDialogWindow
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Local identities and explicit individual selections, without a global contact directory. */
@Composable
internal fun CreateGroupDialog(wallet: SaitoWallet, backend: SaitoBackend, busy: Boolean,
    onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    val context = LocalContext.current.applicationContext
    val owner = wallet.publicKey
    val personal = LocalPersonalControls.current
    var name by rememberSaveable(owner, backend) { mutableStateOf("") }
    var selected by rememberSaveable(owner, backend) { mutableStateOf<List<String>>(emptyList()) }
    var addingKey by rememberSaveable(owner, backend) { mutableStateOf(false) }
    var inspecting by rememberSaveable(owner, backend) { mutableStateOf<String?>(null) }
    var selectionError by remember(owner, backend) { mutableIntStateOf(0) }
    var contacts by remember(owner, backend) { mutableStateOf<List<String>>(emptyList()) }
    var contactsReady by remember(owner, backend) { mutableStateOf(false) }
    var contactsFailed by remember(owner, backend) { mutableStateOf(false) }
    LaunchedEffect(wallet, backend) {
        try {
            val found = withContext(Dispatchers.IO) {
                val retained = ChatHistoryStore.contacts(context, wallet, backend)
                (wallet.chatContacts() + retained.accounts).distinct().filter { it != owner } to retained.unavailable
            }
            contacts = found.first
            contactsFailed = found.second
            contactsReady = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { contactsFailed = true; contactsReady = true }
    }
    fun remove(key: String) { selected = selected - key; selectionError = 0 }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = !busy)) {
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        fun finishTyping() { focus.clearFocus(); keyboard?.hide() }
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !busy, onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.group_cancel))
                    }
                    Text(stringResource(R.string.group_create), Modifier.weight(1f).padding(start = AppSpacing.small)
                        .semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                val contentLabel = stringResource(R.string.group_setup_content)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = contentLabel },
                    contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    item(key = "personal-controls") { PersonalControlsStatus(personal) }
                    item(key = "group-name") {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.group_name)) }, singleLine = true, enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { finishTyping() }))
                    }
                    item(key = "privacy") {
                        Text(stringResource(R.string.group_privacy), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item(key = "add-key") {
                        OutlinedButton(enabled = !busy && personal?.ready == true,
                            onClick = { finishTyping(); addingKey = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.group_add_by_key))
                        }
                    }
                    item(key = "known-heading") {
                        if (contactsReady) Text(stringResource(R.string.chat_known_contacts), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.labelLarge)
                        else Text(stringResource(R.string.chat_contacts_loading))
                    }
                    item(key = "contacts-feedback") {
                        InlineStatus(if (contactsFailed) stringResource(R.string.group_contacts_error) else null, StatusTone.ERROR)
                        if (contactsReady && contacts.isEmpty() && !contactsFailed)
                            Text(stringResource(R.string.group_contacts_empty), style = MaterialTheme.typography.bodySmall)
                    }
                    items(contacts, key = { "known:$it" }) { key ->
                        val selectedContact = key in selected
                        val selectLabel = stringResource(R.string.group_select_member, key)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(selectedContact, enabled = !busy && personal?.ready == true, role = Role.Checkbox) { checked ->
                                finishTyping()
                                when {
                                    !checked -> remove(key)
                                    selected.size >= 254 -> selectionError = R.string.group_member_limit
                                    else -> { selected = selected + key; selectionError = 0 }
                                }
                            }.semantics { contentDescription = selectLabel }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selectedContact, onCheckedChange = null, enabled = !busy && personal?.ready == true)
                            GroupContactIdentity(key, Modifier.weight(1f)) { finishTyping(); inspecting = key }
                        }
                    }
                    if (selected.isNotEmpty()) {
                        item(key = "selected-heading") {
                            Text(stringResource(R.string.group_selected_people), Modifier.semantics { heading() },
                                style = MaterialTheme.typography.labelLarge)
                        }
                        items(selected, key = { "selected:$it" }) { key ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(AppSpacing.small)) {
                                    GroupContactIdentity(key) { finishTyping(); inspecting = key }
                                    val removeLabel = stringResource(R.string.group_remove_member_accessibility, key)
                                    TextButton(enabled = !busy, onClick = { remove(key) },
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = removeLabel }) {
                                        Text(stringResource(R.string.group_remove))
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small)) {
                    InlineStatus(if (selectionError != 0) stringResource(selectionError) else null, StatusTone.ERROR)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(pluralStringResource(R.plurals.group_selected_count, selected.size, selected.size),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled = !busy && personal?.ready == true && name.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp), onClick = { finishTyping(); onCreate(name.trim(), selected.toList()) }) {
                            Text(stringResource(R.string.group_create_confirm))
                        }
                    }
                }
            }
        }
    }
    if (addingKey) ChatPublicKeyDialog(owner, ChatKeyPurpose.GROUP_MEMBER, busy,
        selectionError = { key -> when {
            key in selected -> R.string.group_member_duplicate
            selected.size >= 254 -> R.string.group_member_limit
            else -> 0
        } }, onDismiss = { addingKey = false }, onConfirm = { key ->
            selected = selected + key
            selectionError = 0
            addingKey = false
        })
    inspecting?.let { key ->
        AlertDialog(onDismissRequest = { inspecting = null }, title = { Text(stringResource(R.string.group_identity_details)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(registeredIdentity(key).name, style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(key) }
            } }, confirmButton = { TextButton(onClick = { inspecting = null }) { Text(stringResource(R.string.group_done)) } })
    }
}

@Composable
private fun GroupContactIdentity(key: String, modifier: Modifier = Modifier, onInspect: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { ChatIdentityRow(key, registeredIdentity(key).name, key) }
        IconButton(onClick = onInspect) {
            Icon(painterResource(R.drawable.ic_info), stringResource(R.string.group_member_identity, key))
        }
    }
}

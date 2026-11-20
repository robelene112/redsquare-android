package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet

/** A new channel's fields belong to its account, node and Space, not a selected channel. */
@Stable internal class SpaceChannelCreatorState {
    var owner by mutableStateOf("")
    var node by mutableStateOf("")
    var space by mutableStateOf("")
    var name by mutableStateOf("")
    var category by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var kind by mutableStateOf(SpaceChannelKind.CHAT)
    var restricted by mutableStateOf(false)
    var members by mutableStateOf(emptySet<String>())
    var open by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var previousAttempt by mutableStateOf("")
    var discard by mutableStateOf(false)

    fun show(account: SpacesAccount, spaceId: String, change: SpaceClient.ChannelCreation?) {
        owner = account.ownerKey; node = account.backend.webUrl; space = spaceId
        val initial = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }
        name = initial?.profile?.name.orEmpty(); category = initial?.profile?.category.orEmpty(); categoryId = initial?.categoryId
        kind = initial?.profile?.kind ?: SpaceChannelKind.CHAT
        restricted = initial?.privateMembers != null; members = initial?.privateMembers.orEmpty()
        submitting = initial?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
        previousAttempt = if (submitting) "" else change?.id.orEmpty()
        discard = false; open = true
    }
    fun matches(account: SpacesAccount, state: SpacesAccount.State) = owner == account.ownerKey && node == account.backend.webUrl && space == state.selected

    companion object {
        val Saver = listSaver<SpaceChannelCreatorState, Any>(save = {
            listOf(3, it.owner, it.node, it.space, it.name, it.category, it.kind.name, it.open, it.submitting, it.previousAttempt, it.discard, it.restricted, it.members.sorted().joinToString(","), it.categoryId.orEmpty())
        }, restore = { saved ->
            if (!(saved.size == 11 && saved[0] == 1 || saved.size == 13 && saved[0] == 2 || saved.size == 14 && saved[0] == 3)) null else runCatching { SpaceChannelCreatorState().apply {
                owner = saved[1] as String; node = saved[2] as String; space = saved[3] as String
                name = saved[4] as String; category = saved[5] as String; kind = SpaceChannelKind.valueOf(saved[6] as String)
                open = saved[7] as Boolean; submitting = saved[8] as Boolean; previousAttempt = saved[9] as String; discard = saved[10] as Boolean
                if (saved.size >= 13) { restricted = saved[11] as Boolean; members = (saved[12] as String).split(',').filter { it.isNotBlank() }.toSet() }
                categoryId = saved.getOrNull(13)?.let { (it as String).ifBlank { null } }
            } }.getOrNull()
        })
    }
}

@Composable internal fun SpaceChannelCreationSheet(editor: SpaceChannelCreatorState, account: SpacesAccount,
    state: SpacesAccount.State, onClosed: () -> Unit, onCreated: suspend (String, String) -> Boolean) {
    val bound = editor.matches(account, state)
    val rosterReady = bound && !state.loading && !state.loadingChannel && state.current != null
    val change = state.channelCreation?.takeIf { bound }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    val authorized = rosterReady && state.current?.dissolved != true && state.current?.joined == true && state.discoverySupported == true &&
        state.members.any { it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) }
    val nameLong = editor.name.trim().toByteArray(Charsets.UTF_8).size > 128
    val categoryLabel = state.categories.firstOrNull { it.id == editor.categoryId }?.profile?.name ?: editor.category
    val categoryLong = categoryLabel.trim().toByteArray(Charsets.UTF_8).size > 128
    val desired = if (editor.name.isBlank() || nameLong || categoryLong) null
        else SpaceChannelProfile(editor.name.trim(), editor.kind, categoryLabel.trim())
    val missingMembers = if (rosterReady) editor.members - state.members.map { it.publicKey }.toSet() else emptySet()
    val chosenMembers = if (editor.restricted) editor.members else null
    val created = change?.takeIf { editor.submitting && it.id != editor.previousAttempt &&
        it.status == SpaceClient.ChangeStatus.ACCEPTED &&
        (if (editor.categoryId != null && it.categoryId == editor.categoryId) it.profile.copy(category = categoryLabel.trim()) else it.profile) == desired && it.privateMembers == chosenMembers && state.channels.any { channel -> channel.id == it.channelId } }
    val locked = pending || state.working || created != null
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun close() { editor.discard = false; editor.open = false; if (bound) onClosed() }
    fun dismiss() {
        focus.clearFocus(); keyboard?.hide()
        val changed = editor.name.isNotBlank() || editor.category.isNotBlank() || editor.kind != SpaceChannelKind.CHAT || editor.restricted
        val saved = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }
        if (created == null && changed && (desired == null || desired != saved?.profile || chosenMembers != saved?.privateMembers)) editor.discard = true else close()
    }
    LaunchedEffect(editor.open, bound, state.loadingChannel, state.selected) {
        if (editor.open && !bound && state.selected != null && !state.loadingChannel) { editor.open = false; editor.discard = false }
    }
    suspend fun openCreated() {
        val confirmed = created ?: return
        if (onCreated(editor.space, confirmed.channelId)) { editor.open = false; editor.discard = false }
    }
    LaunchedEffect(editor.open, bound, created?.id) {
        if (editor.open && bound && created != null) openCreated()
    }
    DesignSheet(editor.open && bound, stringResource(R.string.spaces_add_channel), ::dismiss,
        contentKey = "${editor.owner}:${editor.node}:${editor.space}", footer = { first ->
            SpaceManagementProblem(if (bound) state.problem else null, account::clearProblem)
            ManagementChangeNotice(change?.status?.takeIf { it != SpaceClient.ChangeStatus.ACCEPTED || created != null }, state.availability, account::refresh)
            Button(onClick = {
                if (created != null) scope.launch { openCreated() }
                else desired?.let { profile ->
                    focus.clearFocus(); keyboard?.hide()
                    editor.previousAttempt = change?.id.orEmpty(); editor.submitting = true
                    account.createChannel(editor.space, profile, chosenMembers, editor.categoryId)
                }
            }, enabled = editor.open && bound && !state.savingNavigation && !state.working &&
                (created != null || authorized && desired != null && (!editor.restricted || state.privateChannelAccessSupported && missingMembers.isEmpty()) && state.current?.canManage == true && !locked),
                modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (created != null) R.string.spaces_open_created_channel else R.string.spaces_create_channel))
            }
            TextButton(onClick = ::dismiss, enabled = editor.open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_close))
            }
        }) {
        Text(stringResource(if (editor.restricted) R.string.spaces_create_private_channel_detail else R.string.spaces_create_channel_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(editor.name, { editor.name = it.take(512) }, Modifier.fillMaxWidth().spaceEditorFocusVisibility().animateContentSize(tween(AppSpacing.transitionMillis)),
            label = { Text(stringResource(R.string.spaces_channel_name)) }, singleLine = true, enabled = editor.open && !locked,
            isError = nameLong, supportingText = if (nameLong) ({ Text(stringResource(R.string.spaces_channel_name_long)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = editor.open && !locked, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(channelKindIcon(editor.kind)), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(channelKindLabel(editor.kind)))
                Spacer(Modifier.width(8.dp)); Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
            }
            DropdownMenu(menu && editor.open, { menu = false }) {
                SpaceChannelKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(stringResource(channelKindLabel(kind))) },
                    enabled = !locked, onClick = { editor.kind = kind; menu = false }) }
            }
        }
        OutlinedTextField(categoryLabel, { editor.category = it.take(512); editor.categoryId = null }, Modifier.fillMaxWidth().spaceEditorFocusVisibility().animateContentSize(tween(AppSpacing.transitionMillis)),
            label = { Text(stringResource(R.string.spaces_category_optional)) }, singleLine = true, enabled = editor.open && !locked,
            isError = categoryLong, supportingText = if (categoryLong) ({ Text(stringResource(R.string.spaces_category_long)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
        SpaceCategoryChoices(state, editor.restricted || state.profiles[editor.space]?.visibility == SpaceVisibility.PRIVATE,
            editor.categoryId, editor.open && !locked) { category ->
            editor.categoryId = category.id; editor.category = category.profile.name
        }
        if (state.privateChannelAccessSupported || editor.restricted) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(editor.restricted, enabled = editor.open && !locked && rosterReady && state.privateChannelAccessSupported,
                role = Role.Checkbox, onValueChange = { restricted ->
                    editor.restricted = restricted
                    if (!restricted && state.profiles[editor.space]?.visibility == SpaceVisibility.PUBLIC &&
                        state.categories.any { it.id == editor.categoryId && it.isPrivate }) {
                        editor.categoryId = null; editor.category = ""
                    }
                }), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.spaces_channel_restricted), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(if (editor.restricted) R.string.spaces_channel_restricted_detail else R.string.spaces_channel_usual_visibility),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(editor.restricted, null, enabled = editor.open && !locked && rosterReady && state.privateChannelAccessSupported)
            }
            androidx.compose.animation.AnimatedVisibility(editor.restricted) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.spaces_choose_channel_members), style = MaterialTheme.typography.titleSmall)
                    if (!rosterReady) Text(stringResource(R.string.spaces_loading_members), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (missingMembers.isNotEmpty()) TextButton(onClick = { editor.members = editor.members - missingMembers },
                        enabled = !locked, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_remove_unavailable_members)) }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(state.members.filter { it.role == SpaceRole.MEMBER }, key = { it.publicKey }) { member ->
                            val selected = member.publicKey in editor.members
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(selected, enabled = editor.open && !locked && rosterReady,
                                role = Role.Checkbox, onValueChange = { checked -> editor.members = if (checked)
                                    editor.members + member.publicKey else editor.members - member.publicKey }),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(registeredIdentity(member.publicKey).name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Checkbox(selected, null, enabled = editor.open && !locked && rosterReady)
                            }
                        }
                    }
                }
            }
        }
        if (rosterReady && !authorized) Text(stringResource(if (state.current?.dissolved == true) R.string.spaces_dissolved_detail else R.string.spaces_edit_access_changed), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editor.discard && editor.open && bound) AlertDialog(onDismissRequest = { editor.discard = false },
        title = { Text(stringResource(R.string.spaces_discard_channel_title)) }, text = { Text(stringResource(R.string.spaces_discard_channel_detail)) },
        confirmButton = { TextButton(onClick = ::close, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_discard_changes)) } },
        dismissButton = { TextButton(onClick = { editor.discard = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_keep_editing)) } })
}

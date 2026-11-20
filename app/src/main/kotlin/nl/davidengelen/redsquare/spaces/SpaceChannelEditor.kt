package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet

/** Saved editor contents remain bound to the account, node and exact channel. */
@Stable internal class SpaceChannelEditorState {
    var owner by mutableStateOf("")
    var node by mutableStateOf("")
    var space by mutableStateOf("")
    var channel by mutableStateOf("")
    var name by mutableStateOf("")
    var category by mutableStateOf("")
    var categoryId by mutableStateOf<String?>(null)
    var open by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var previousAttempt by mutableStateOf("")
    var discard by mutableStateOf(false)

    fun show(account: SpacesAccount, spaceId: String, selected: SpaceClient.Channel, change: SpaceClient.ChannelEdit?) {
        owner = account.ownerKey; node = account.backend.webUrl; space = spaceId; channel = selected.id
        val initial = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }?.profile ?: selected.profile
        name = initial.name; category = initial.category
        categoryId = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }?.categoryId ?: selected.categoryId.takeIf { initial.category == selected.profile.category }
        previousAttempt = change?.id.orEmpty()
        submitting = false; discard = false; open = true
    }
    fun matches(account: SpacesAccount, state: SpacesAccount.State) = owner == account.ownerKey && node == account.backend.webUrl &&
        space == state.selected && channel == state.selectedChannel

    companion object {
        val Saver = listSaver<SpaceChannelEditorState, Any>(save = {
            listOf(2, it.owner, it.node, it.space, it.channel, it.name, it.category, it.open, it.submitting, it.previousAttempt, it.discard, it.categoryId.orEmpty())
        }, restore = { saved ->
            if (!(saved.size == 11 && saved[0] == 1 || saved.size == 12 && saved[0] == 2)) null else runCatching { SpaceChannelEditorState().apply {
                owner = saved[1] as String; node = saved[2] as String; space = saved[3] as String; channel = saved[4] as String
                name = saved[5] as String; category = saved[6] as String; open = saved[7] as Boolean
                submitting = saved[8] as Boolean; previousAttempt = saved[9] as String; discard = saved[10] as Boolean
                categoryId = saved.getOrNull(11)?.let { (it as String).ifBlank { null } }
            } }.getOrNull()
        })
    }
}

@Composable internal fun SpaceChannelEditSheet(editor: SpaceChannelEditorState, account: SpacesAccount,
    state: SpacesAccount.State, onClosed: () -> Unit) {
    val bound = editor.matches(account, state)
    val channel = state.channels.firstOrNull { it.id == editor.channel }?.takeIf { bound }
    val change = state.channelEdit?.takeIf { bound && it.channelId == editor.channel }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    val supported = if (channel?.visibility == SpaceVisibility.PUBLIC) state.publicChannelEditSupported else state.privateChannelEditSupported
    val authorized = bound && supported && channel?.metadataReady == true &&
        state.members.any { it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) }
    val nameEmpty = editor.name.isBlank()
    val nameLong = editor.name.trim().toByteArray(Charsets.UTF_8).size > 128
    val categoryLabel = state.categories.firstOrNull { it.id == editor.categoryId }?.profile?.name ?: editor.category
    val categoryLong = categoryLabel.trim().toByteArray(Charsets.UTF_8).size > 128
    val desired = channel?.takeIf { !nameEmpty && !nameLong && !categoryLong }?.let {
        it.profile.copy(name = editor.name.trim(), category = categoryLabel.trim())
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun close() { editor.discard = false; editor.open = false; if (bound) onClosed() }
    fun dismiss() {
        val changed = editor.name.trim() != channel?.profile?.name || categoryLabel.trim() != channel?.profile?.category || editor.categoryId != null && editor.categoryId != channel?.categoryId
        val savedIntent = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }?.profile
        focus.clearFocus(); keyboard?.hide()
        if (changed && (desired == null || desired != savedIntent)) editor.discard = true else close()
    }
    LaunchedEffect(editor.open, bound, state.loadingChannel, state.selected) {
        if (editor.open && !bound && state.selected != null && !state.loadingChannel) {
            editor.open = false; editor.discard = false
        }
    }
    LaunchedEffect(editor.open, bound, editor.submitting, change?.id, change?.status) {
        if (editor.open && bound && editor.submitting && change != null && change.id != editor.previousAttempt &&
            change.status == SpaceClient.ChangeStatus.ACCEPTED &&
            (if (editor.categoryId != null && change.categoryId == editor.categoryId) change.profile.copy(category = categoryLabel.trim()) else change.profile) == desired) close()
    }
    DesignSheet(editor.open && bound, stringResource(R.string.spaces_edit_channel), ::dismiss,
        contentKey = "${editor.owner}:${editor.node}:${editor.space}:${editor.channel}", footer = { first ->
            SpaceManagementProblem(if (bound) state.problem else null, account::clearProblem)
            ManagementChangeNotice(change?.status, state.availability, account::refresh)
            Button(onClick = {
                desired?.let { profile ->
                    focus.clearFocus(); keyboard?.hide()
                    editor.previousAttempt = change?.id.orEmpty(); editor.submitting = true
                    account.editChannel(editor.space, editor.channel, profile, editor.categoryId)
                }
            }, enabled = editor.open && authorized && desired != null && (desired != channel?.profile || editor.categoryId != null && editor.categoryId != channel?.categoryId) && state.current?.canManage == true &&
                !pending && !state.working, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_save_channel))
            }
            TextButton(onClick = ::dismiss, enabled = editor.open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_close))
            }
        }) {
        Text(stringResource(R.string.spaces_edit_channel_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(editor.name, { editor.name = it.take(512) }, Modifier.fillMaxWidth().spaceEditorFocusVisibility().animateContentSize(tween(AppSpacing.transitionMillis)),
            label = { Text(stringResource(R.string.spaces_channel_name)) }, singleLine = true, enabled = editor.open && !pending && !state.working,
            isError = nameEmpty || nameLong, supportingText = if (nameEmpty || nameLong) ({
                Text(stringResource(if (nameEmpty) R.string.spaces_channel_name_required else R.string.spaces_channel_name_long))
            }) else null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
        OutlinedTextField(categoryLabel, { editor.category = it.take(512); editor.categoryId = null }, Modifier.fillMaxWidth().spaceEditorFocusVisibility().animateContentSize(tween(AppSpacing.transitionMillis)),
            label = { Text(stringResource(R.string.spaces_category_optional)) }, singleLine = true, enabled = editor.open && !pending && !state.working,
            isError = categoryLong, supportingText = if (categoryLong) ({ Text(stringResource(R.string.spaces_category_long)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
        SpaceCategoryChoices(state, channel?.visibility == SpaceVisibility.PRIVATE, editor.categoryId,
            editor.open && !pending && !state.working) { category ->
            editor.categoryId = category.id; editor.category = category.profile.name
        }
        AnimatedVisibility(editor.open && bound && !authorized) { Text(stringResource(R.string.spaces_edit_access_changed), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (editor.discard && editor.open && bound) AlertDialog(onDismissRequest = { editor.discard = false },
        title = { Text(stringResource(R.string.spaces_discard_channel_title)) },
        text = { Text(stringResource(R.string.spaces_discard_channel_detail)) },
        confirmButton = { TextButton(onClick = ::close, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_discard_changes)) } },
        dismissButton = { TextButton(onClick = { editor.discard = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_keep_editing)) } })
}

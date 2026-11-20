package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet

@Stable internal class SpaceCategoryEditorState {
    var owner by mutableStateOf("")
    var node by mutableStateOf("")
    var space by mutableStateOf("")
    var open by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var creating by mutableStateOf(true)
    var privateCategory by mutableStateOf(false)
    var category by mutableStateOf("")
    var name by mutableStateOf("")
    var submitting by mutableStateOf(false)
    var previousAttempt by mutableStateOf("")
    var discard by mutableStateOf(false)
    var closeAfterDiscard by mutableStateOf(false)

    fun show(account: SpacesAccount, state: SpacesAccount.State) {
        owner = account.ownerKey; node = account.backend.webUrl; space = state.selected.orEmpty()
        open = true; editing = false; creating = true; category = ""; name = ""; submitting = false; discard = false
        privateCategory = state.profiles[space]?.visibility == SpaceVisibility.PRIVATE
        val pending = state.categoryChange?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED }
        submitting = pending?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
        previousAttempt = if (submitting) "" else pending?.id.orEmpty()
        if (pending != null) {
            editing = true; creating = pending.creating; category = pending.categoryId; name = pending.profile.name; privateCategory = pending.isPrivate
        }
    }
    fun matches(account: SpacesAccount, state: SpacesAccount.State) = owner == account.ownerKey &&
        node == account.backend.webUrl && space == state.selected

    companion object {
        val Saver = listSaver<SpaceCategoryEditorState, Any>(save = {
            listOf(2, it.owner, it.node, it.space, it.open, it.editing, it.category, it.name,
                it.submitting, it.previousAttempt, it.discard, it.closeAfterDiscard, it.creating, it.privateCategory)
        }, restore = { saved ->
            if (!(saved.size == 12 && saved[0] == 1 || saved.size == 14 && saved[0] == 2)) null else runCatching { SpaceCategoryEditorState().apply {
                owner = saved[1] as String; node = saved[2] as String; space = saved[3] as String
                open = saved[4] as Boolean; editing = saved[5] as Boolean; category = saved[6] as String; name = saved[7] as String
                submitting = saved[8] as Boolean; previousAttempt = saved[9] as String
                discard = saved[10] as Boolean; closeAfterDiscard = saved[11] as Boolean
                creating = saved.getOrNull(12) as? Boolean ?: category.isEmpty()
                privateCategory = saved.getOrNull(13) as? Boolean ?: true
            } }.getOrNull()
        })
    }
}

@Composable internal fun SpaceCategorySheet(editor: SpaceCategoryEditorState, account: SpacesAccount,
    state: SpacesAccount.State, onClosed: () -> Unit) {
    val bound = editor.matches(account, state)
    val selected = state.categories.firstOrNull { it.id == editor.category }?.takeIf { bound }
    val allChange = state.categoryChange?.takeIf { bound }
    val change = allChange?.takeIf { !editor.editing || editor.submitting || it.categoryId == editor.category }
    val pending = allChange?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    val targetAvailable = !editor.editing || editor.creating || selected != null
    val audienceUnchanged = !editor.creating || editor.privateCategory == (state.profiles[editor.space]?.visibility == SpaceVisibility.PRIVATE)
    val rosterReady = bound && state.ready && !state.loading && !state.loadingChannel && state.current?.id == editor.space &&
        state.profiles.containsKey(editor.space) && state.members.isNotEmpty()
    val roleAllowed = state.members.any {
        it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
    }
    val authorized = rosterReady && roleAllowed && targetAvailable && audienceUnchanged && state.categoriesSupported
    val accessNotice = when {
        !rosterReady -> if (state.availability == SpacesAccount.Availability.OFFLINE) R.string.spaces_category_settings_connection
            else R.string.spaces_category_settings_loading
        !roleAllowed -> R.string.spaces_category_access_changed
        !targetAvailable || !audienceUnchanged -> R.string.spaces_category_unavailable
        !state.categoriesSupported -> if (state.availability == SpacesAccount.Availability.ONLINE) R.string.spaces_category_not_supported
            else R.string.spaces_category_settings_connection
        else -> null
    }
    val name = editor.name.trim()
    val invalid = name.isBlank() || name.toByteArray(Charsets.UTF_8).size > 128
    val desired = name.takeUnless { invalid }?.let(::SpaceCategoryProfile)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finish() { editor.open = false; editor.discard = false; if (bound) onClosed() }
    fun back(close: Boolean) {
        focus.clearFocus(); keyboard?.hide()
        if (editor.submitting && state.working && (change == null || change.id == editor.previousAttempt)) return
        val saved = change?.takeIf { it.status != SpaceClient.ChangeStatus.ACCEPTED &&
            (it.categoryId == editor.category || editor.submitting && it.id != editor.previousAttempt &&
                it.creating == editor.creating && it.profile == desired) }?.profile?.name
        val dirty = editor.editing && name != selected?.profile?.name.orEmpty() && name != saved
        if (dirty) { editor.discard = true; editor.closeAfterDiscard = close }
        else if (close) finish() else editor.editing = false
    }
    LaunchedEffect(editor.open, bound, state.ready, state.loading, state.loadingChannel, state.selected, account) {
        val identityChanged = editor.owner != account.ownerKey || editor.node != account.backend.webUrl
        val restoredRouteReady = state.ready && !state.loading && !state.loadingChannel && state.selected != null
        if (editor.open && !bound && (identityChanged || restoredRouteReady)) { editor.open = false; editor.discard = false }
    }
    LaunchedEffect(editor.open, editor.submitting, change?.id, change?.status, state.categories) {
        if (editor.open && bound && editor.submitting && change != null && change.id != editor.previousAttempt &&
            change.creating == editor.creating && change.profile == desired && editor.creating) editor.category = change.categoryId
        if (editor.open && bound && editor.submitting && change != null && change.id != editor.previousAttempt &&
            change.status == SpaceClient.ChangeStatus.ACCEPTED && change.profile == desired &&
            state.categories.any { it.id == change.categoryId && it.profile == desired }) {
            editor.editing = false; editor.submitting = false
        }
    }
    DesignSheet(editor.open && bound, stringResource(if (editor.editing) {
        if (editor.creating) R.string.spaces_create_category else R.string.spaces_rename_category
    } else R.string.spaces_categories), { back(true) }, contentKey = "${editor.owner}:${editor.node}:${editor.space}:${editor.editing}:${editor.category}",
        footer = { first ->
            SpaceManagementProblem(if (bound) state.problem else null, account::clearProblem)
            ManagementChangeNotice(change?.status, state.availability, account::refresh)
            if (editor.editing) Button(onClick = {
                desired?.let {
                    focus.clearFocus(); keyboard?.hide()
                    editor.previousAttempt = change?.id.orEmpty(); editor.submitting = true
                    account.saveCategory(editor.space, if (editor.creating) null else editor.category, it, editor.privateCategory)
                }
            }, enabled = editor.open && authorized && desired != null && desired != selected?.profile &&
                state.current?.canManage == true && !state.working && !pending,
                modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_save_category))
            }
            TextButton(onClick = { back(!editor.editing) }, enabled = editor.open,
                modifier = (if (editor.editing) Modifier else first).fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (editor.editing) R.string.spaces_back_to_categories else R.string.spaces_close))
            }
        }) {
        AnimatedContent(editor.editing, transitionSpec = {
            fadeIn(tween(AppSpacing.transitionMillis)) togetherWith fadeOut(tween(AppSpacing.transitionMillis))
        }, label = "category-editor") { editing ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (editing) {
                    Text(stringResource(R.string.spaces_category_rename_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(editor.name, { editor.name = it.take(512) },
                        Modifier.fillMaxWidth().spaceEditorFocusVisibility().animateContentSize(tween(AppSpacing.transitionMillis)),
                        label = { Text(stringResource(R.string.spaces_category_name)) }, singleLine = true,
                        enabled = editor.open && !pending && !state.working,
                        isError = editor.name.isNotEmpty() && invalid, supportingText = if (name.toByteArray(Charsets.UTF_8).size > 128) ({
                            Text(stringResource(R.string.spaces_category_long))
                        }) else null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
                    val isPrivate = selected?.isPrivate ?: editor.privateCategory
                    Text(stringResource(if (isPrivate) R.string.spaces_category_private_detail else R.string.spaces_category_public_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.spaces_categories_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = {
                        editor.category = ""; editor.name = ""; editor.editing = true; editor.creating = true; editor.submitting = false
                        editor.privateCategory = state.profiles[editor.space]?.visibility == SpaceVisibility.PRIVATE
                    }, enabled = editor.open && authorized && !pending && !state.working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_create_category)) }
                    state.categories.forEach { category ->
                        OutlinedCard(onClick = {
                            editor.category = category.id; editor.name = category.profile.name; editor.editing = true; editor.creating = false; editor.submitting = false
                            editor.privateCategory = category.isPrivate
                        }, enabled = editor.open && authorized && !pending && !state.working, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(category.profile.name, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(if (category.isPrivate) R.string.spaces_category_private else R.string.spaces_category_public),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        accessNotice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (editor.discard && editor.open && bound) AlertDialog(onDismissRequest = { editor.discard = false },
        title = { Text(stringResource(R.string.spaces_discard_category_title)) },
        text = { Text(stringResource(R.string.spaces_discard_category_detail)) },
        confirmButton = { TextButton(onClick = {
            editor.discard = false
            if (editor.closeAfterDiscard) finish() else editor.editing = false
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_discard_changes)) } },
        dismissButton = { TextButton(onClick = { editor.discard = false }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.spaces_keep_editing))
        } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SpaceCategoryChoices(state: SpacesAccount.State, privateChannel: Boolean, selected: String?,
    enabled: Boolean, choose: (SpaceClient.Category) -> Unit) {
    if (!state.categoriesSupported) return
    val choices = state.categories.filter { privateChannel || !it.isPrivate }
    if (choices.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { category -> FilterChip(selected = selected == category.id, onClick = { choose(category) },
            enabled = enabled, label = { Text(category.profile.name) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
}

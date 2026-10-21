package nl.davidengelen.redsquare.feed

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable
internal fun AuthorEditScreen(wallet: SaitoWallet, endpoint: String, backend: SaitoBackend, post: FeedPost,
    modifier: Modifier, kind: AuthorActionKind = AuthorActionKind.EDIT, onClose: () -> Unit) {
    require(wallet.publicKey == post.author)
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val memory = remember(activity, wallet.publicKey, backend, post.id, kind) {
        ViewModelProvider(activity)["${kind.storageId}:${wallet.publicKey}:${backend.id}:${post.id}", PostComposerMemory::class.java]
    }
    val store = remember(wallet.publicKey, backend, post.id, kind) {
        PostDraftStore.author(activity.applicationContext, wallet.publicKey, backend, post.id, kind)
    }
    val scope = rememberCoroutineScope()
    val publishing = nl.davidengelen.redsquare.policy.LocalPublishingAgreement.current
    val state = remember(store, memory, endpoint) {
        AuthorEditState(store, memory, wallet, endpoint, post, scope, AndroidPostRevisions(activity.applicationContext, backend), kind)
    }
    val navigation = requireNotNull(LocalPostNavigation.current)
    LaunchedEffect(state, state.reload) { state.load() }
    DisposableEffect(state) {
        val save: suspend () -> Boolean = { state.persist() }
        val guarded: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            if (navigation.saveCurrent !== save || !state.ready || state.busy || state.saving) false
            else state.persist { navigation.saveCurrent === save && commit() }
        }
        navigation.saveCurrent = save
        navigation.guardedCurrent = guarded
        onDispose {
            if (navigation.saveCurrent === save) navigation.saveCurrent = null
            if (navigation.guardedCurrent === guarded) navigation.guardedCurrent = null
        }
    }
    BackHandler { state.close(onClose) }
    with(state) {
        Column(modifier.imePadding()) {
            Row(Modifier.fillMaxWidth().padding(bottom = AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), !busy && !saving) { close(onClose) }
                Text(stringResource(if (kind == AuthorActionKind.EDIT) R.string.author_edit_title else R.string.author_delete_title),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Button(modifier = Modifier.heightIn(min = 48.dp), enabled = ready && !busy && !saving &&
                    (available || (!conflict && (kind == AuthorActionKind.DELETE || text.isNotBlank()))), onClick = {
                    if (available) close(onClose) else send(publishing::beforePublishing)
                }) {
                    Text(stringResource(if (available) R.string.done else if (candidate != null) R.string.retry else
                        if (kind == AuthorActionKind.EDIT) R.string.author_edit_save else R.string.author_delete_confirm))
                }
            }
            val summary = when {
                saving -> R.string.publishing_saving
                available -> if (kind == AuthorActionKind.EDIT) R.string.author_edit_available else R.string.author_delete_available
                conflict -> R.string.author_edit_conflict
                candidate != null -> if (kind == AuthorActionKind.EDIT) R.string.author_edit_pending else R.string.author_delete_pending
                busy -> R.string.publishing_preparing
                !ready && !error -> R.string.publishing_loading
                else -> null
            }
            InlineStatus(summary?.let { stringResource(it) }, when {
                busy || saving || !ready -> StatusTone.WORKING
                available -> StatusTone.SUCCESS
                conflict || candidate != null -> StatusTone.WARNING
                else -> StatusTone.INFO
            }, details = buildList {
                add(stringResource(R.string.status_origin_node, backend.domain))
                if (busy || saving) add(stringResource(R.string.publishing_busy_detail))
            })
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                InlineStatus(if (networkError) stringResource(R.string.author_action_network_error) else null, StatusTone.ERROR)
                InlineStatus(if (error) stringResource(R.string.post_draft_error) else null, StatusTone.ERROR)
                if (!ready && error) TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                if (kind == AuthorActionKind.EDIT) {
                    PublishingEditor(text, ::edit, ready && !busy && !saving && candidate == null)
                } else {
                    Text(stringResource(R.string.author_delete_explanation), style = MaterialTheme.typography.bodyLarge)
                    PublishingWorkItem(backend.domain) { Text(post.text, style = MaterialTheme.typography.bodyLarge) }
                }
                if (conflict) {
                    Text(stringResource(R.string.author_edit_conflict_resolution), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(enabled = !busy && !saving, onClick = { resolveConflict(false) }) {
                        Text(stringResource(R.string.author_edit_keep_text))
                    }
                    OutlinedButton(enabled = !busy && !saving, onClick = { resolveConflict(true) }) {
                        Text(stringResource(R.string.author_edit_use_latest))
                    }
                }
            }
        }
    }
}

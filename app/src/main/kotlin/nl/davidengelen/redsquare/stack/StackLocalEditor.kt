package nl.davidengelen.redsquare.stack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import nl.davidengelen.redsquare.ui.*

@OptIn(FlowPreview::class)
@Composable
internal fun StackLocalEditor(state: StackLocalDraft, review: Boolean, modifier: Modifier,
    chrome: @Composable (Boolean) -> Unit, onBack: () -> Unit, onReview: () -> Unit,
    onOpenWeb: () -> Unit, feedback: @Composable (Modifier) -> Unit, footer: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val navigation = requireNotNull(LocalPostNavigation.current)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state) {
        state.load()
        snapshotFlow { state.draft }.debounce(600).collect { state.autosave() }
    }
    DisposableEffect(state, navigation) {
        val loading: () -> Boolean = { !state.ready && !state.error }
        val save: suspend () -> Boolean = {
            focus.clearFocus(); keyboard?.hide()
            state.persist()
        }
        val guarded: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            if (navigation.saveCurrent !== save) false
            else {
                focus.clearFocus(); keyboard?.hide()
                state.persist { navigation.saveCurrent === save && commit() }
            }
        }
        navigation.saveCurrent = save
        navigation.guardedCurrent = guarded
        navigation.loadingCurrent = loading
        onDispose {
            if (navigation.saveCurrent === save) navigation.saveCurrent = null
            if (navigation.guardedCurrent === guarded) navigation.guardedCurrent = null
            if (navigation.loadingCurrent === loading) navigation.loadingCurrent = null
        }
    }
    fun leave(action: () -> Unit) {
        scope.launch { navigation.navigate { action(); true } }
    }
    BackHandler { leave(onBack) }
    val keyboardVisible = keyboardOccupiesWindow()
    Column(modifier.imePadding()) {
        chrome(false)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), !state.saving) { leave(onBack) }
            Text(stringResource(if (review) R.string.stack_review_draft else R.string.stack_write),
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (!review) TextButton(enabled = state.ready && !state.saving, onClick = { leave(onReview) }) {
                Text(stringResource(R.string.stack_review_action))
            }
        }
        InlineStatus(if (state.error) stringResource(if (state.ready) R.string.stack_draft_save_failed else R.string.stack_draft_load_failed) else null, StatusTone.ERROR,
                actionLabel = stringResource(R.string.retry), actionEnabled = !state.saving && !state.loading,
                onAction = { scope.launch { if (state.ready) state.persist() else state.load() } })
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("stack-local-editor")
            .padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!state.ready && state.loading) InlineStatus(stringResource(R.string.stack_draft_loading), StatusTone.WORKING)
            else if (state.ready && review) {
                Text(state.draft.title, style = MaterialTheme.typography.headlineLarge)
                if (state.draft.subtitle.isNotBlank()) Text(state.draft.subtitle, style = MaterialTheme.typography.bodyLarge)
                StackMarkdown(state.draft.body)
                Text(stringResource(R.string.stack_web_publish_detail), style = MaterialTheme.typography.bodyMedium)
                StackBlockButton({ leave {
                    clipboard.setText(AnnotatedString(state.draft.body))
                    onOpenWeb()
                } }, enabled = !state.saving && state.hasWork) { Text(stringResource(R.string.stack_copy_open_publish)) }
            } else if (state.ready) StackEditorForm(state.draft, state.visual, state::edit, state::editVisual,
                { leave(onReview) }, sampleMedia = false, enabled = !state.saving)
            Spacer(Modifier.height(16.dp))
        }
        feedback(Modifier.align(Alignment.BottomCenter).padding(AppSpacing.screen))
        }
        if (!keyboardVisible) footer()
    }
}

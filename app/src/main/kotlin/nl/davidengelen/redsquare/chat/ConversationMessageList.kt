package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.preserveReadingAnchor

/** Each wallet/node/conversation owns its saved viewport. Callers retain their message and action models. */
@Composable
internal fun ConversationMessageList(messageKeys: List<String>, viewportKey: String, messagesLabel: String,
    modifier: Modifier = Modifier, historyHidden: Boolean = false, requestLatest: Int = 0,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.(showMessage: (Int) -> Unit) -> Unit) = key(viewportKey) {
    val scope = rememberCoroutineScope()
    val messageList = rememberLazyListState()
    var openedHistory by rememberSaveable { mutableStateOf(false) }
    var newMessages by rememberSaveable { mutableStateOf(false) }
    var knownMessages by remember { mutableStateOf(emptyList<String>()) }
    var following by remember { mutableStateOf(true) }
    var anchoring by remember { mutableStateOf(false) }
    var latestRequest by remember { mutableIntStateOf(0) }
    var handledRequest by remember { mutableIntStateOf(0) }
    var handledExternalRequest by remember { mutableIntStateOf(0) }
    suspend fun showLatest() {
        if (messageKeys.isEmpty()) return
        messageList.scrollToItem(messageKeys.lastIndex)
        // A last bubble can be taller than the viewport. Finish at its bottom.
        val layout = messageList.layoutInfo
        layout.visibleItemsInfo.firstOrNull { it.key == messageKeys.last() }?.let { last ->
            val overflow = last.offset + last.size - layout.viewportEndOffset
            if (overflow > 0) messageList.scrollBy(overflow.toFloat())
        }
    }
    LaunchedEffect(messageList) {
        snapshotFlow {
            val layout = messageList.layoutInfo
            // An appended layout must not redefine the reader's previous end position.
            if (anchoring || knownMessages.isEmpty() || layout.totalItemsCount != knownMessages.size ||
                layout.visibleItemsInfo.any { it.key !in knownMessages }) null
            else !messageList.canScrollForward
        }.collect { atEnd ->
            if (atEnd != null) {
                following = atEnd
                if (atEnd) newMessages = false
            }
        }
    }
    LaunchedEffect(messageKeys, latestRequest, requestLatest, historyHidden) {
        if (messageKeys.isEmpty()) {
            // A deliberate policy change can hide every known message. Initial
            // loading/restoration must keep its saved reading state until data arrives.
            if (knownMessages.isNotEmpty() && historyHidden) {
                knownMessages = emptyList()
                newMessages = false
                openedHistory = false
                following = true
                handledRequest = latestRequest
                handledExternalRequest = requestLatest
            }
            return@LaunchedEffect
        }
        val firstOpening = !openedHistory
        val requested = latestRequest != handledRequest || requestLatest != handledExternalRequest
        val appended = knownMessages.isNotEmpty() && knownMessages.last() != messageKeys.last()
        val follow = firstOpening || requested || (appended && following && !messageList.isScrollInProgress)
        openedHistory = true
        handledRequest = latestRequest
        handledExternalRequest = requestLatest
        anchoring = true
        try {
            if (follow) {
                following = true
                showLatest()
                newMessages = false
            } else if (appended) newMessages = true
        } finally {
            knownMessages = messageKeys
            anchoring = false
        }
    }
    val showMessage: (Int) -> Unit = { index ->
        if (index in messageKeys.indices) scope.launch { messageList.animateScrollToItem(index) }
    }
    Box(modifier.preserveReadingAnchor(messageList, enabled = !following)) {
        LazyColumn(Modifier.fillMaxSize().semantics { contentDescription = messagesLabel },
            state = messageList, verticalArrangement = verticalArrangement) {
            content(showMessage)
        }
        if (newMessages && messageKeys.isNotEmpty()) FilledTonalButton(onClick = { latestRequest++ },
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.chat_new_messages))
        }
    }
}

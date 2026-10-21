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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.preserveReadingAnchor

internal data class ConversationMessageRequest(val messageKey: String, val attempt: Int)

/** Each identity/node/conversation owns its viewport. History keys include retained originals even
 * when filtered; rendered keys include admitted history and any distinct queued preview. */
@Composable
internal fun ConversationMessageList(messageKeys: List<String>, viewportKey: String, messagesLabel: String,
    modifier: Modifier = Modifier, historyHidden: Boolean = false, requestLatest: Int = 0,
    historyMessageKeys: List<String> = messageKeys,
    requestMessage: ConversationMessageRequest? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.(showMessage: (Int) -> Unit) -> Unit) = key(viewportKey) {
    val scope = rememberCoroutineScope()
    val messageList = rememberLazyListState()
    var openedHistory by rememberSaveable { mutableStateOf(false) }
    var unreadMessages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var knownMessages by remember { mutableStateOf(emptyList<String>()) }
    var knownHistory by remember { mutableStateOf(emptyList<String>()) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var knownViewportHeight by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(true) }
    var anchoring by remember { mutableStateOf(false) }
    var latestRequest by remember { mutableIntStateOf(0) }
    var handledRequest by remember { mutableIntStateOf(0) }
    var handledExternalRequest by remember { mutableIntStateOf(0) }
    var handledMessage by remember { mutableStateOf<ConversationMessageRequest?>(null) }
    suspend fun showLatest(target: String?) {
        val index = messageKeys.indexOf(target)
        if (index < 0) return
        messageList.scrollToItem(index)
        // A last bubble can be taller than the viewport. Finish at its bottom.
        val layout = messageList.layoutInfo
        layout.visibleItemsInfo.firstOrNull { it.key == target }?.let { last ->
            val overflow = last.offset + last.size - layout.viewportEndOffset
            if (overflow > 0) messageList.scrollBy(overflow.toFloat())
        }
    }
    val historySet = historyMessageKeys.toHashSet()
    val renderedHistory = messageKeys.filter { it in historySet }
    val historyUnavailable by rememberUpdatedState(
        historyMessageKeys.isNotEmpty() && renderedHistory.isEmpty() && !historyHidden)
    LaunchedEffect(messageList) {
        snapshotFlow {
            val layout = messageList.layoutInfo
            // An appended layout must not redefine the reader's previous end position.
            if (historyUnavailable || anchoring || knownMessages.isEmpty() || layout.viewportSize.height != knownViewportHeight ||
                layout.totalItemsCount != knownMessages.size ||
                layout.visibleItemsInfo.any { it.key !in knownMessages }) null
            else !messageList.canScrollForward || layout.visibleItemsInfo.any {
                it.key == knownMessages.lastOrNull { key -> key in knownHistory } && it.offset >= layout.viewportStartOffset &&
                    it.offset + it.size <= layout.viewportEndOffset
            }
        }.collect { atEnd ->
            if (atEnd != null) {
                following = atEnd
                if (atEnd) unreadMessages = emptyList()
            }
        }
    }
    LaunchedEffect(messageKeys, historyMessageKeys, latestRequest, requestLatest, requestMessage, historyHidden, viewportHeight) {
        if (historyUnavailable) {
            // A queue can remain visible during policy recovery. Deliberate queue exposure
            // is still possible, but it cannot mark unavailable real history as read.
            if (requestLatest != handledExternalRequest && messageKeys.isNotEmpty()) {
                anchoring = true
                try {
                    showLatest(messageKeys.last())
                    handledExternalRequest = requestLatest
                } finally { anchoring = false }
            }
            return@LaunchedEffect
        }
        // A temporary policy-read failure must not erase unread state. Deliberate filtering can.
        if (renderedHistory.isNotEmpty() || historyHidden)
            unreadMessages = unreadMessages.filter { it in renderedHistory }
        if (messageKeys.isEmpty()) {
            // A deliberate policy change can hide every known message. Initial
            // loading/restoration must keep its saved reading state until data arrives.
            if (knownMessages.isNotEmpty() && historyHidden) {
                knownMessages = emptyList()
                knownHistory = historyMessageKeys
                unreadMessages = emptyList()
                openedHistory = false
                following = true
                handledRequest = latestRequest
                handledExternalRequest = requestLatest
            }
            if (historyHidden) knownHistory = historyMessageKeys
            return@LaunchedEffect
        }
        val firstOpening = !openedHistory
        val requestedHistory = latestRequest != handledRequest
        val requestedTail = requestLatest != handledExternalRequest
        // An explicit send can enter history after its queued preview disappears.
        // Keep its exact request pending until that message is actually rendered.
        val requestedMessage = requestMessage?.takeIf { it != handledMessage && it.messageKey in messageKeys }
        val previousTail = historyMessageKeys.indexOf(knownHistory.lastOrNull())
        // Older-page prepends and cached hide/reveal are not arrivals. A received batch may
        // end with a blocked original, so retain every new admitted key in its appended suffix.
        val arrivals = if (knownHistory.isNotEmpty() && previousTail >= 0)
            historyMessageKeys.drop(previousTail + 1).filter { it !in knownHistory && it in renderedHistory }
            else emptyList()
        val appended = arrivals.isNotEmpty()
        val resized = knownViewportHeight > 0 && viewportHeight != knownViewportHeight
        val requested = requestedHistory || requestedTail || requestedMessage != null
        val follow = firstOpening || requested || ((appended || resized) && following && !messageList.isScrollInProgress)
        openedHistory = true
        handledRequest = latestRequest
        handledExternalRequest = requestLatest
        anchoring = true
        try {
            if (follow) {
                following = true
                showLatest(requestedMessage?.messageKey ?:
                    if (firstOpening || requestedTail || resized) messageKeys.last() else renderedHistory.lastOrNull())
                if (requestedMessage != null) handledMessage = requestedMessage
                unreadMessages = emptyList()
            } else if (appended) unreadMessages = (unreadMessages + arrivals).distinct()
        } finally {
            knownMessages = messageKeys
            knownHistory = historyMessageKeys
            knownViewportHeight = viewportHeight
            anchoring = false
        }
    }
    val showMessage: (Int) -> Unit = { index ->
        if (index in messageKeys.indices) scope.launch { messageList.animateScrollToItem(index) }
    }
    Box(modifier.onSizeChanged { viewportHeight = it.height }.preserveReadingAnchor(messageList, enabled = !following)) {
        LazyColumn(Modifier.fillMaxSize().semantics { contentDescription = messagesLabel },
            state = messageList, verticalArrangement = verticalArrangement) {
            content(showMessage)
        }
        if (unreadMessages.any { it in messageKeys }) FilledTonalButton(onClick = { latestRequest++ },
            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.chat_new_messages))
        }
    }
}

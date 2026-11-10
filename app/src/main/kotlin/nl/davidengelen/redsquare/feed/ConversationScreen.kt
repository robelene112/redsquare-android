package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.ui.refreshAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.AppSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ConversationScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, selected: FeedPost,
    modifier: Modifier, onBack: () -> Unit, onIdentity: (String) -> Unit, onPost: (FeedPost) -> Unit,
    social: SocialState? = null, quotes: QuoteState? = null, onQuote: ((FeedPost) -> Unit)? = null,
    authorActions: PostAuthorActions? = null, view: ConversationViewState) {
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val cache = remember(backend, selected.threadId) { AndroidFeedCache(context, backend, selected.threadId) }
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
    var posts by remember(selected.id) { mutableStateOf(listOf(selected)) }
    var loading by remember(selected.id) { mutableStateOf(true) }
    var failed by remember(selected.id) { mutableStateOf(false) }
    var unreadable by remember(selected.id) { mutableStateOf(false) }
    var cacheWarning by remember(selected.id) { mutableStateOf(false) }
    var refresh by remember(selected.id) { mutableIntStateOf(0) }
    var pullRefreshing by remember(selected.id) { mutableStateOf(false) }
    var manualPullPending by remember(selected.id) { mutableStateOf(false) }
    var pullRefreshSucceeded by remember(selected.id) { mutableStateOf(false) }
    val position = rememberLazyListState()
    var historyInformation by rememberSaveable(selected.id) { mutableStateOf(false) }
    val replyTarget = view.replyingTo
    if (replyTarget != null && wallet != null) {
        PostComposerScreen(wallet, endpoint, backend, modifier, replyTarget) { view.replyingTo = null; refresh++ }
        return
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, selected.id, refresh) {
        if (wallet == null) { loading = false; pullRefreshing = false; manualPullPending = false; return@LaunchedEffect }
        loading = true
        val saved = withContext(Dispatchers.IO) { cache.load() }
        val cached = withContext(Dispatchers.IO) { revisions.apply(RedSquareFeed.newest(posts + saved.posts)) }
        posts = cached.posts.sortedBy { it.createdAt }
        cacheWarning = saved.unreadable || cached.unreadable
        try {
            val page = withContext(Dispatchers.IO) {
                val criteria = JsonObject().apply {
                    addProperty("request", "load")
                    addProperty("field1", "RedSquare")
                    addProperty("field5", selected.threadId)
                    addProperty("flagged_ne", 1)
                    addProperty("limit", 100)
                }
                val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                val known = posts.toList()
                SaitoPeer(wallet, endpoint).use { peer ->
                    RedSquareFeed.readConversation(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                        .toString(Charsets.UTF_8), selected.threadId).also { page ->
                        revisions.reconcileDeletions(peer, page.deletions, page.posts + known)
                    }
                }
            }
            social?.observe(page.posts)
            val applied = withContext(Dispatchers.IO) {
                page.posts.forEach(revisions::observe)
                revisions.apply(RedSquareFeed.newest(page.posts + posts))
            }
            posts = applied.posts.sortedBy { it.createdAt }
            cacheWarning = withContext(Dispatchers.IO) {
                try { cache.save(posts); applied.unreadable } catch (_: Exception) { true }
            }
            unreadable = page.ignored > 0
            failed = false
            if (manualPullPending) { manualPullPending = false; pullRefreshSucceeded = true }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { if (currentCoroutineContext().isActive) { loading = false; pullRefreshing = false } }
        // Thread failure must not prevent recovery from the separately verified parent cache.
        if (selected.parentId.isNotEmpty() && posts.none { it.id == selected.parentId })
            quotes?.load(selected.parentId, refresh = refresh > 0)
    }
    LaunchedEffect(pullRefreshSucceeded) { if (pullRefreshSucceeded) { kotlinx.coroutines.delay(2_500); pullRefreshSucceeded = false } }
    val parent = quotes?.originals?.get(selected.parentId)
    val available = RedSquareFeed.newest(posts + listOfNotNull(parent?.post)).sortedBy { it.createdAt }
    val parentMissing = selected.parentId.isNotEmpty() && available.none { it.id == selected.parentId }
    val visiblePosts = available.filter { personal?.hidesPosts(it.author) == false }
    val refreshContent: () -> Unit = {
        if (wallet != null && !loading) {
            pullRefreshing = true; manualPullPending = true; pullRefreshSucceeded = false; refresh++
        }
    }
    val parentLoading = parentMissing && parent?.loading == true
    val cacheLimited = cacheWarning || parent?.cacheWarning == true
    val summary = when {
        loading -> stringResource(R.string.conversation_loading)
        failed -> stringResource(R.string.conversation_refresh_error_summary)
        parentLoading -> stringResource(R.string.conversation_parent_loading)
        parentMissing -> stringResource(R.string.conversation_parent_summary)
        unreadable -> stringResource(R.string.conversation_unreadable_summary)
        cacheLimited -> stringResource(R.string.conversation_cache_summary)
        pullRefreshSucceeded -> stringResource(R.string.conversation_refresh_success)
        else -> null
    }
    val details = buildList {
        if (failed) add(stringResource(R.string.conversation_error))
        if (unreadable) add(stringResource(R.string.conversation_unreadable))
        if (cacheLimited) add(stringResource(R.string.feed_cache_warning))
        if (parentMissing && !loading) add(stringResource(when {
            parentLoading -> R.string.conversation_parent_loading
            failed || parent?.failed == true -> R.string.conversation_parent_error
            else -> R.string.conversation_parent_unavailable
        }))
    }
    val tone = when {
        loading -> StatusTone.WORKING
        failed -> StatusTone.ERROR
        parentLoading -> StatusTone.WORKING
        parentMissing || unreadable || cacheLimited -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    if (historyInformation) AlertDialog(onDismissRequest = { historyInformation = false },
        title = { Text(stringResource(R.string.conversation_history_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(stringResource(R.string.conversation_history), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.conversation_refresh_guidance), style = MaterialTheme.typography.bodyMedium)
            } },
        confirmButton = { TextButton(onClick = { historyInformation = false }) { Text(stringResource(R.string.done)) } })
    Column(modifier) {
        // Scrolled post touch regions can extend above their clipped viewport.
        // The fixed header owns this area for touch exploration as well as paint.
        Row(Modifier.fillMaxWidth().zIndex(1f), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), onClick = onBack)
            Text(stringResource(R.string.conversation_title), Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FrameIconButton(R.drawable.ic_info, stringResource(R.string.conversation_information),
                onClick = { historyInformation = true })
        }
        PersonalControlsStatus(personal)
        if (personal?.ready == true && personal.hidesPosts(selected.author))
            Text(stringResource(R.string.personal_post_hidden), style = MaterialTheme.typography.bodySmall)
        InlineStatus(summary, tone, details, modifier = Modifier.testTag("conversation-status"),
            actionLabel = if (failed || parentMissing || unreadable || cacheLimited) stringResource(R.string.retry) else null,
            actionEnabled = wallet != null && !loading && !parentLoading, onAction = refreshContent)
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = refreshContent,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(Modifier.fillMaxSize().testTag("conversation-post-list").refreshAction(wallet != null && !loading, refreshContent), state = position) {
                items(visiblePosts, key = { it.id }) { post ->
                    PostCard(post, backend, onIdentity, onPost, social, quotes, onQuote, authorActions,
                        bodyTextStyle = MaterialTheme.typography.bodyMedium, bodyVerticalPadding = 4.dp,
                        onReply = if (wallet != null) { target -> view.replyingTo = target } else null)
                    HorizontalDivider()
                }
            }
        }
    }
}

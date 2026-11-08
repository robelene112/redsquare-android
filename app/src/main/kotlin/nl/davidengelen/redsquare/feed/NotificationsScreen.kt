package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.tween
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.notifications.SocialNotificationPermissionPrompt
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.LocalRegistryAuthority
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.saito.*

private data class NotificationRow(val id: String, val author: String, val createdAt: Long,
    val post: FeedPost?, val caption: Int, val target: String = post?.id.orEmpty())

/** Group keys are presentation-only. Persistence and read commits always use signed event IDs. */
private data class NotificationItem(val key: String, val events: List<NotificationRow>, val like: Boolean = false) {
    val latest: NotificationRow get() = events.first()
    val authors: List<String> get() = events.map { it.author }.distinct()
    fun unread(readIds: Set<String>): Boolean = events.any { it.id !in readIds }
}

/** Addressing is a fallback only while the original's signed author is unknown. */
private fun referenceCaption(owner: String?, target: FeedPost?, recipients: List<String>,
    ownCaption: Int, neutralCaption: Int): Int? = when {
    owner != null && target?.author == owner -> ownCaption
    owner != null && target == null && owner in recipients -> neutralCaption
    else -> null
}

private data class NotificationPages(val likes: ArchiveLikePage, val replies: ArchiveFeedPage,
    val mentions: ArchiveFeedPage, val reposts: ArchiveActionPage, val quotes: List<FeedPost>, val contacts: List<FeedPost>)

private const val MAX_NOTIFICATION_READS = 33
private data class NotificationStream(val kind: String, val author: String? = null, val target: String? = null)
private data class NotificationRead(val stream: NotificationStream, val position: ActivityPageRead, val pages: NotificationPages?) {
    val before: Long get() = position.before
}
private data class NotificationCursor(val before: Long?, val warning: Boolean = false)

/** Strict Archive bounds overlap one timestamp; a repeated full tie makes coverage uncertain. */
private fun nextCursor(read: NotificationRead): NotificationCursor {
    val pages = requireNotNull(read.pages)
    val (count, oldest) = when (read.stream.kind) {
        "RedSquareLike" -> pages.likes.rowCount to pages.likes.oldestCreatedAt
        "RedSquareRetweet" -> pages.reposts.rowCount to pages.reposts.oldestCreatedAt
        else -> pages.replies.rowCount to pages.replies.oldestCreatedAt
    }
    if (count < 100) return NotificationCursor(null)
    if (oldest == null || oldest >= read.before) return NotificationCursor(read.before, warning = true)
    val overlap = oldest + 1
    return if (overlap < read.before) NotificationCursor(overlap)
        else NotificationCursor(oldest, warning = true)
}

private fun notificationStreams(followed: Set<String>, ownPosts: List<FeedPost>): List<NotificationStream> =
    listOf(NotificationStream("RedSquareLike"), NotificationStream("RedSquare"), NotificationStream("RedSquareRetweet")) +
        ownPosts.distinctBy { it.id }.map { NotificationStream("RedSquare", target = it.id) } +
        followed.sorted().flatMap { author ->
            listOf(NotificationStream("RedSquare", author), NotificationStream("RedSquareRetweet", author))
        }

private val NotificationPages.ignoredRows: Int
    get() = likes.ignored + replies.ignored + reposts.ignored

private val NotificationPages.envelopes: List<String>
    get() = likes.likes.map { it.envelope } + replies.posts.map { it.envelope } +
        mentions.posts.map { it.envelope } + reposts.actions.map { it.envelope } + quotes.map { it.envelope } + contacts.map { it.envelope }

private fun notificationPages(likes: String, posts: String, retweets: String, owner: String,
    name: SaitoRegisteredName?, registrar: String, followed: Set<String>): NotificationPages {
    val quotes = RedSquareFeed.readIncomingQuotes(retweets, owner).posts + RedSquareFeed.readIncomingQuotes(posts, owner).posts
    val contacts = if (followed.isEmpty()) emptyList() else
        (RedSquareFeed.readSearch(posts).posts + quotes).filter { it.author != owner && it.author in followed }
    return NotificationPages(RedSquareLikes.readIncoming(likes, owner), RedSquareFeed.readReplies(posts),
        RedSquareFeed.readMentions(posts, owner, name, registrar),
        RedSquareActions.readIncomingReposts(retweets, owner), quotes, contacts)
}

private fun cachedArchive(envelopes: List<String>, requests: Set<String>): String = com.google.gson.JsonArray().apply {
    envelopes.filter { SaitoEnvelope.decode(it).body.get("request").asString in requests }.take(100).forEach { envelope ->
        add(JsonObject().apply { addProperty("tx", envelope) })
    }
}.toString()

/** A successful smaller page does not erase verified activity already available on this phone. */
private fun <T> availableActivity(incoming: List<T>, retained: List<T>, id: (T) -> String,
    createdAt: (T) -> Long): List<T> = (incoming + retained).distinctBy(id)
    .sortedByDescending(createdAt).take(NotificationHistoryStore.MAX_EVENTS)

/** The selected filter and each reading position survive post/profile visits and recreation. */
internal class ActivityViewState {
    var mentionsOnly by mutableStateOf(false)
    val allPosition = LazyListState()
    val mentionPosition = LazyListState()
}

@Composable
internal fun NotificationsScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, knownPosts: List<FeedPost>, followed: Set<String>, lifecycle: Lifecycle,
    view: ActivityViewState, modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit, onIdentity: (String) -> Unit, onReference: (String) -> Unit) {
    val walletKey = wallet?.publicKey
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var events by remember(walletKey, backend, endpoint) { mutableStateOf<List<ProfileLike>>(emptyList()) }
    var replies by remember(walletKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var mentions by remember(walletKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var reposts by remember(walletKey, backend, endpoint) { mutableStateOf<List<RedSquareAction>>(emptyList()) }
    var quotes by remember(walletKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var contacts by remember(walletKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    val history = remember(walletKey, backend) { wallet?.let { NotificationHistoryStore(context, backend, it.publicKey) } }
    val readStore = remember(walletKey, backend) { wallet?.let { NotificationReadStore(context, backend, it.publicKey) } }
    val scope = rememberCoroutineScope()
    var readIds by remember(walletKey, backend) { mutableStateOf<Set<String>>(emptySet()) }
    var readReady by remember(walletKey, backend) { mutableStateOf(false) }
    var readWarning by remember(walletKey, backend) { mutableStateOf(false) }
    var markingRead by remember(walletKey, backend) { mutableStateOf(false) }
    var cacheWarning by remember(walletKey, backend) { mutableStateOf(false) }
    var loading by remember(walletKey, backend, endpoint) { mutableStateOf(true) }
    var historyReady by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var failed by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var completedRead by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var partialRead by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var ignored by remember(walletKey, backend, endpoint) { mutableIntStateOf(0) }
    var cursors by remember(walletKey, backend, endpoint) { mutableStateOf<Map<NotificationStream, Long?>>(emptyMap()) }
    var pending by remember(walletKey, backend, endpoint) { mutableStateOf<Map<NotificationStream, ActivityPageRead>>(emptyMap()) }
    var likeHistory by remember(walletKey, backend, endpoint) { mutableStateOf(ActivityLikeHistory()) }
    var likeHeadFailed by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var pagingWarning by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    var loadGeneration by remember(walletKey, backend, endpoint) { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var backgroundRead by remember(walletKey, backend, endpoint) { mutableStateOf(false) }
    val position = view.allPosition
    val mentionPosition = view.mentionPosition
    var followNewest by remember(walletKey, backend, endpoint) { mutableIntStateOf(0) }
    LaunchedEffect(followNewest) {
        if (followNewest > 0 && !position.isScrollInProgress) position.scrollToItem(0)
    }
    // Each completed request or read commit starts a fresh quiet interval. No overlapping reads or idle background polling.
    LaunchedEffect(walletKey, backend, endpoint, lifecycle, loading, markingRead) {
        if (wallet != null && !loading && !markingRead) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(10_000)
            if (!loading && !markingRead) {
                backgroundRead = true
                refresh++
            }
        }
    }
    val ownPosts = knownPosts.filter { it.author == walletKey }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, backend, endpoint, knownPosts, followed, refresh) {
        if (wallet == null) return@LaunchedEffect
        val generation = ++loadGeneration
        val streams = notificationStreams(followed, ownPosts)
        val background = backgroundRead
        val requested = if (background) {
            // Like-only newest reads have separate ownership from the reader's older-history continuation.
            mapOf(NotificationStream("RedSquareLike") to ActivityPageRead.Newest(System.currentTimeMillis() + 1))
        } else pending.filterKeys { it in streams }.ifEmpty {
            val before = System.currentTimeMillis() + 1
            (streams.filter { it.author == null && it.target == null } +
                streams.filter { it.target != null }.take(10) + streams.filter { it.author != null }.take(20))
                .associateWith { ActivityPageRead.Newest(before) }
        }
        cursors = cursors + requested.filterKeys { it !in cursors }.mapValues { it.value.before }
        if (!background) pending = requested
        loading = true
        if (!backgroundRead) partialRead = false
        // A background feed update must not overwrite an in-flight verified mark commit.
        if (!markingRead && (!backgroundRead || !readReady)) {
            readReady = false
            try {
                readIds = withContext(Dispatchers.IO) { readStore!!.load() }
                readReady = true
                readWarning = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readWarning = true }
        }
        val name = try { withContext(Dispatchers.IO) { RegisteredNameStore(context, wallet.publicKey, registrar).load()?.name } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        // Archive readers accept100 rows; device history can retain several pages.
        fun readAvailableHistory(envelopes: List<String>): List<NotificationPages> =
            envelopes.chunked(100).map { batch ->
                notificationPages(cachedArchive(batch, setOf("like tweet")),
                    cachedArchive(batch, setOf("create tweet")), cachedArchive(batch, setOf("retweet")),
                    wallet.publicKey, name, registrar, followed)
            }
        fun publish(pages: NotificationPages): Boolean {
            val priorIds = events.map { it.id }.toHashSet()
            val newLikes = pages.likes.likes.any { it.id !in priorIds }
            val returnToTop = !position.isScrollInProgress && position.firstVisibleItemIndex == 0 &&
                position.firstVisibleItemScrollOffset == 0 && newLikes
            events = availableActivity(pages.likes.likes, events, { it.id }, { it.createdAt })
            replies = availableActivity(pages.replies.posts.filter { it.author != wallet.publicKey && it.parentId.isNotEmpty() },
                replies, { it.id }, { it.createdAt })
            mentions = availableActivity(pages.mentions.posts, mentions, { it.id }, { it.createdAt })
            reposts = availableActivity(pages.reposts.actions, reposts, { it.id }, { it.createdAt })
            quotes = availableActivity(pages.quotes, quotes, { it.id }, { it.createdAt })
            contacts = availableActivity(pages.contacts, contacts, { it.id }, { it.createdAt })
            ignored = pages.ignoredRows
            if (returnToTop) followNewest++
            return newLikes
        }
        val saved = withContext(Dispatchers.IO) { history!!.load() }
        cacheWarning = saved.unreadable
        val cached = withContext(Dispatchers.IO) { readAvailableHistory(saved.envelopes) }
        cached.forEach { publish(it) }
        val available = withContext(Dispatchers.IO) { readAvailableHistory(knownPosts.map { it.envelope }) }
        available.forEach { publish(it) }
        historyReady = true
        val availableEnvelopes = available.flatMap { it.envelopes }
        if (availableEnvelopes.isNotEmpty()) {
            try { withContext(Dispatchers.IO) { history!!.save(availableEnvelopes) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cacheWarning = true }
        }
        try {
            val pages = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    suspend fun read(stream: NotificationStream, before: Long): String {
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", stream.kind)
                            when {
                                stream.target != null -> addProperty("field4", stream.target)
                                stream.author != null -> addProperty("field2", stream.author)
                                else -> addProperty("field3", wallet.publicKey)
                            }
                            addProperty("flagged_ne", 1)
                            addProperty("limit", 100); addProperty("created_earlier_than", before)
                        }
                        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        return peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                    }
                    coroutineScope {
                        requested.map { (stream, position) -> async {
                            val content = try {
                                val response = read(stream, position.before)
                                notificationPages(if (stream.kind == "RedSquareLike") response else "[]",
                                    if (stream.kind == "RedSquare") response else "[]",
                                    if (stream.kind == "RedSquareRetweet") response else "[]", wallet.publicKey, name, registrar, followed)
                            } catch (_: Exception) {
                                // A request's own deadline is a read failure. Parent cancellation must propagate.
                                currentCoroutineContext().ensureActive()
                                null
                            }
                            NotificationRead(stream, position, content)
                        } }.awaitAll()
                    }
                }
            }
            pages.forEach { result ->
                val like = result.stream.kind == "RedSquareLike"
                val newestLike = like && (result.position is ActivityPageRead.Newest || !likeHistory.observed)
                if (newestLike) likeHeadFailed = result.pages == null
                result.pages?.let { content ->
                    val newLikes = publish(content)
                    val next = nextCursor(result)
                    if (like) {
                        likeHistory = likeHistory.observe(result.position, content.likes, next.before, newLikes)
                        cursors = cursors + (result.stream to likeHistory.continuation?.before)
                    } else {
                        cursors = cursors + (result.stream to next.before)
                    }
                    pagingWarning = pagingWarning || next.warning
                    val waiting = pending[result.stream]
                    val supersededHead = waiting is ActivityPageRead.Newest && result.position is ActivityPageRead.Newest &&
                        result.before >= waiting.before
                    val exhaustiveHead = like && result.position is ActivityPageRead.Newest && next.before == null &&
                        waiting != null && result.before >= waiting.before
                    if (waiting == result.position || supersededHead || exhaustiveHead) pending = pending - result.stream
                }
            }
            if (background) pages.filter { it.pages == null && it.stream !in pending }.forEach {
                pending = pending + (it.stream to it.position)
            }
            val unreadableCount = available.sumOf { it.ignoredRows } + pages.mapNotNull { it.pages }.sumOf { it.ignoredRows }
            // A Like-only check cannot clear an unresolved warning learned from another stream.
            ignored = if (background) maxOf(ignored, unreadableCount) else unreadableCount
            try { withContext(Dispatchers.IO) {
                history!!.save(pages.mapNotNull { it.pages }.flatMap { it.envelopes })
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cacheWarning = true }
            failed = likeHeadFailed || pages.any { it.pages == null } || pending.isNotEmpty()
            partialRead = failed && (pages.any { it.pages != null } || (background && partialRead))
            if (!failed) completedRead = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            failed = true
            if (!background) partialRead = false
            if (requested.any { (stream, position) -> stream.kind == "RedSquareLike" &&
                    (position is ActivityPageRead.Newest || !likeHistory.observed) }) likeHeadFailed = true
        }
        finally { if (generation == loadGeneration) { loading = false; backgroundRead = false } }
    }
    fun markObserved(observed: List<String>) {
        if (!readReady || markingRead) return
        markingRead = true
        scope.launch {
            try {
                readIds = withContext(Dispatchers.IO) { readStore!!.mark(observed) }
                readWarning = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { readWarning = true }
            finally { markingRead = false }
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), onClick = onBack)
            Text(stringResource(R.string.navigation_activity), Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall)
            FrameIconButton(R.drawable.ic_refresh, stringResource(R.string.feed_refresh),
                enabled = wallet != null && !loading && !markingRead) {
                if (!loading && !markingRead) { backgroundRead = false; pending = emptyMap(); pagingWarning = false; refresh++ }
            }
        }
        nl.davidengelen.redsquare.ui.DesignTabs(
            listOf(stringResource(R.string.activity_filter_all), stringResource(R.string.activity_filter_mentions)),
            if (view.mentionsOnly) 1 else 0, onSelect = { view.mentionsOnly = it == 1 })
        PersonalControlsStatus(personal)
        val repostRows = reposts.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(walletKey, target, event.recipients,
                R.string.notifications_repost_own, R.string.notifications_repost)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val quoteRows = quotes.mapNotNull { quote ->
            val target = quote.quoted ?: knownPosts.firstOrNull { it.id == quote.quoteTarget }
            referenceCaption(walletKey, target, quote.recipients,
                R.string.notifications_quote_own, R.string.notifications_quote)?.let { caption ->
                NotificationRow(quote.id, quote.author, quote.createdAt, quote, caption)
            }
        }
        val likeRows = events.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(walletKey, target, event.recipients,
                R.string.notifications_like_own, R.string.notifications_like)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val replyRows = replies.mapNotNull { reply ->
            val target = knownPosts.firstOrNull { it.id == reply.parentId }
            referenceCaption(walletKey, target, reply.recipients,
                R.string.notifications_reply_own, R.string.notifications_reply)?.let { caption ->
                NotificationRow(reply.id, reply.author, reply.createdAt, reply, caption)
            }
        }
        val contactRows = contacts.filter { it.author in followed }.map { post ->
            NotificationRow(post.id, post.author, post.createdAt, post, R.string.notifications_posted)
        }
        val mentionRows = mentions.map { mention ->
            NotificationRow(mention.id, mention.author, mention.createdAt, mention, R.string.notifications_mention)
        }.filter { personal?.hidesPosts(it.author) == false }
        val individual = (quoteRows + repostRows + replyRows + mentionRows + contactRows)
            .distinctBy { it.id }.filter { personal?.hidesPosts(it.author) == false }
            .map { NotificationItem(it.id, listOf(it)) }
        val groupedLikes = likeRows.filter { personal?.hidesPosts(it.author) == false }
            .groupBy { it.target }.map { (target, members) ->
                NotificationItem("like:$target", members.distinctBy { it.id }.sortedWith(compareByDescending<NotificationRow> { it.createdAt }.thenByDescending { it.id }), like = true)
            }
        val visible = (if (view.mentionsOnly) mentionRows.map { NotificationItem(it.id, listOf(it)) } else individual + groupedLikes).sortedWith(compareByDescending<NotificationItem> { it.latest.createdAt }.thenBy { it.key })
        val previews = rememberActivityPreviews(visible.mapNotNull { it.latest.post }.distinctBy { it.id }.sortedBy { it.id }, backend)
        val unread = visible.count { it.unread(readIds) }
        val eligibleStreams = notificationStreams(followed, ownPosts)
        val moreHistory = eligibleStreams.any { it !in cursors || cursors[it] != null }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (readReady && (visible.isNotEmpty() || (completedRead && (!loading || backgroundRead) && !failed && ignored == 0))) {
                Column(Modifier.weight(1f)) {
                    Text(if (unread == 0) stringResource(R.string.notifications_read_none)
                        else stringResource(R.string.notifications_unread_count, unread),
                        style = MaterialTheme.typography.labelLarge)
                    if ((loading && !backgroundRead) || failed || ignored > 0 || pagingWarning || cacheWarning || moreHistory)
                        Text(stringResource(R.string.activity_available_scope),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Spacer(Modifier.weight(1f))
            FrameIconButton(R.drawable.ic_read_all, stringResource(R.string.notifications_mark_read),
                enabled = readReady && !markingRead && unread > 0) { markObserved(visible.flatMap { item -> item.events.map { it.id } }) }
        }
        val retainedCount = (events.map { it.id } + replies.map { it.id } + mentions.map { it.id } +
            reposts.map { it.id } + quotes.map { it.id } + contacts.map { it.id }).distinct().size
        val notices = buildList {
            if (readWarning) add(R.string.notifications_read_status_warning to R.string.notifications_read_status_warning_detail)
            if (cacheWarning || previews.unreadable) add(R.string.notifications_cache_warning to R.string.notifications_cache_warning_detail)
            if (ignored > 0) add(R.string.notifications_unverified to R.string.notifications_unverified)
            if (pagingWarning) add(R.string.notifications_paging_warning to R.string.notifications_paging_warning_detail)
            if (retainedCount >= NotificationHistoryStore.MAX_EVENTS)
                add(R.string.notifications_history_limit to R.string.notifications_history_limit_detail)
        }
        val status = when {
            markingRead -> R.string.activity_saving_read
            loading && !backgroundRead -> R.string.notifications_loading
            partialRead -> R.string.activity_partial
            failed && visible.isEmpty() && !completedRead -> R.string.activity_unavailable
            failed -> R.string.notifications_read_error
            completedRead && visible.isEmpty() && moreHistory -> R.string.activity_more_history
            else -> null
        }
        InlineStatus(status?.let { stringResource(it) }, when {
            (loading && !backgroundRead) || markingRead -> StatusTone.WORKING
            partialRead -> StatusTone.WARNING
            failed -> StatusTone.ERROR
            else -> StatusTone.INFO
        }, details = if (failed && (!loading || backgroundRead)) listOf(stringResource(R.string.notifications_read_error_detail)) else emptyList(),
            actionLabel = if (failed && (!loading || backgroundRead)) stringResource(R.string.retry) else null,
            actionEnabled = wallet != null && !loading && !markingRead,
            onAction = { if (!loading && !markingRead) { backgroundRead = false; refresh++ } })
        // Several independent warnings remain individually discoverable without consuming the entire inbox.
        if (notices.isNotEmpty()) Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
            notices.forEach { (summary, detail) ->
                InlineStatus(stringResource(summary), StatusTone.WARNING,
                    details = if (summary == detail) emptyList() else listOf(stringResource(detail)))
            }
        }
        val undiscovered = eligibleStreams.filter { it !in cursors }.take(20)
        val older = cursors.filter { (stream, before) -> stream in eligibleStreams && before != null &&
            (retainedCount < NotificationHistoryStore.MAX_EVENTS || (stream.kind == "RedSquareLike" && likeHistory.catchUp != null)) }
            .mapValues { (stream, before) ->
                if (stream.kind == "RedSquareLike") likeHistory.continuation ?: ActivityPageRead.Older(requireNotNull(before))
                else ActivityPageRead.Older(requireNotNull(before))
            }
        nl.davidengelen.redsquare.ui.SingleOwnerReveal(view.mentionsOnly, Modifier.weight(1f).fillMaxWidth()) {
        // Do not measure a temporary empty history over a retained reading position.
        if (historyReady && personal?.ready == true) LazyColumn(Modifier.fillMaxSize(), state = if (view.mentionsOnly) mentionPosition else position) {
            if (visible.isEmpty() && completedRead && (!loading || backgroundRead) && !failed && ignored == 0 &&
                !cacheWarning && !pagingWarning && !moreHistory) item(key = "empty-activity") {
                Text(stringResource(R.string.activity_empty), Modifier.fillMaxWidth().padding(vertical = AppSpacing.gap),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visible, key = { it.key }) { item ->
                val event = item.latest
                val available = event.post?.id?.let(previews.posts::get)
                Box(Modifier.animateItem(fadeInSpec = tween(AppSpacing.transitionMillis),
                    placementSpec = tween(AppSpacing.transitionMillis), fadeOutSpec = tween(AppSpacing.transitionMillis))) {
                ActivityRow(item.authors, event.createdAt, event.caption, available, previewLoading = previews.loading,
                    likeCount = if (item.like) item.events.size else null,
                    unread = if (readReady) item.unread(readIds) else null,
                    canMark = readReady && !markingRead && item.unread(readIds),
                    onMark = { markObserved(item.events.map { it.id }) },
                    onIdentity = onIdentity,
                    onOpen = { available?.let(onPost) ?: onReference(event.target) })
                }
            }
        }
        }
        TextButton(enabled = wallet != null && !loading && !markingRead &&
            (undiscovered.isNotEmpty() || older.isNotEmpty()), onClick = {
            if (!loading && !markingRead) {
                backgroundRead = false
                val before = System.currentTimeMillis() + 1
                // Discover remaining known parents and contacts before spending the remaining budget on older pages.
                pending = (undiscovered.associateWith { ActivityPageRead.Newest(before) } + older).entries.take(MAX_NOTIFICATION_READS)
                    .associate { it.key to it.value }
                cursors = cursors + undiscovered.associateWith { before }
                if (pending.isNotEmpty()) refresh++
            }
        }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.notifications_more), style = MaterialTheme.typography.labelMedium)
        }
    }
    walletKey?.let { SocialNotificationPermissionPrompt(it) }
}

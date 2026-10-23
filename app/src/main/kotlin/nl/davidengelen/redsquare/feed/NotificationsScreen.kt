package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.LocalRegistryAuthority
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.saito.*

private data class NotificationRow(val id: String, val author: String, val createdAt: Long,
    val post: FeedPost?, val caption: Int, val target: String = post?.id.orEmpty())

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
private data class NotificationRead(val stream: NotificationStream, val before: Long, val pages: NotificationPages?)
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

@Composable
internal fun NotificationsScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, knownPosts: List<FeedPost>, followed: Set<String>,
    modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit, onIdentity: (String) -> Unit, onReference: (String) -> Unit) {
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var events by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<ProfileLike>>(emptyList()) }
    var replies by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var mentions by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var reposts by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<RedSquareAction>>(emptyList()) }
    var quotes by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var contacts by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    val history = remember(wallet?.publicKey, backend) { wallet?.let { NotificationHistoryStore(context, backend, it.publicKey) } }
    val readStore = remember(wallet?.publicKey, backend) { wallet?.let { NotificationReadStore(context, backend, it.publicKey) } }
    val scope = rememberCoroutineScope()
    var readIds by remember(wallet?.publicKey, backend) { mutableStateOf<Set<String>>(emptySet()) }
    var readReady by remember(wallet?.publicKey, backend) { mutableStateOf(false) }
    var readWarning by remember(wallet?.publicKey, backend) { mutableStateOf(false) }
    var markingRead by remember(wallet?.publicKey, backend) { mutableStateOf(false) }
    var cacheWarning by remember(wallet?.publicKey, backend) { mutableStateOf(false) }
    var loading by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf(true) }
    var failed by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf(false) }
    var ignored by remember(wallet?.publicKey, backend, endpoint) { mutableIntStateOf(0) }
    var cursors by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<Map<NotificationStream, Long?>>(emptyMap()) }
    var pending by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<Map<NotificationStream, Long>>(emptyMap()) }
    var pagingWarning by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf(false) }
    var loadGeneration by remember(wallet?.publicKey, backend, endpoint) { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val ownPosts = knownPosts.filter { it.author == wallet?.publicKey }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, backend, endpoint, knownPosts, followed, refresh) {
        if (wallet == null) return@LaunchedEffect
        val generation = ++loadGeneration
        val streams = notificationStreams(followed, ownPosts)
        val requested = pending.filterKeys { it in streams }.ifEmpty {
            val before = System.currentTimeMillis() + 1
            (streams.filter { it.author == null && it.target == null } +
                streams.filter { it.target != null }.take(10) + streams.filter { it.author != null }.take(20))
                .associateWith { before }.also { cursors = it }
        }
        pending = requested
        loading = true
        // A background feed update must not overwrite an in-flight verified mark commit.
        if (!markingRead) {
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
        fun publish(pages: NotificationPages) {
            events = availableActivity(pages.likes.likes, events, { it.id }, { it.createdAt })
            replies = availableActivity(pages.replies.posts.filter { it.author != wallet.publicKey && it.parentId.isNotEmpty() },
                replies, { it.id }, { it.createdAt })
            mentions = availableActivity(pages.mentions.posts, mentions, { it.id }, { it.createdAt })
            reposts = availableActivity(pages.reposts.actions, reposts, { it.id }, { it.createdAt })
            quotes = availableActivity(pages.quotes, quotes, { it.id }, { it.createdAt })
            contacts = availableActivity(pages.contacts, contacts, { it.id }, { it.createdAt })
            ignored = pages.ignoredRows
        }
        val saved = withContext(Dispatchers.IO) { history!!.load() }
        cacheWarning = saved.unreadable
        val cached = withContext(Dispatchers.IO) { readAvailableHistory(saved.envelopes) }
        cached.forEach(::publish)
        val available = withContext(Dispatchers.IO) { readAvailableHistory(knownPosts.map { it.envelope }) }
        available.forEach(::publish)
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
                        requested.map { (stream, before) -> async {
                            val content = try {
                                val response = read(stream, before)
                                notificationPages(if (stream.kind == "RedSquareLike") response else "[]",
                                    if (stream.kind == "RedSquare") response else "[]",
                                    if (stream.kind == "RedSquareRetweet") response else "[]", wallet.publicKey, name, registrar, followed)
                            } catch (_: Exception) {
                                // A request's own deadline is a read failure. Parent cancellation must propagate.
                                currentCoroutineContext().ensureActive()
                                null
                            }
                            NotificationRead(stream, before, content)
                        } }.awaitAll()
                    }
                }
            }
            pages.forEach { result -> result.pages?.let { content ->
                publish(content)
                val next = nextCursor(result)
                cursors = cursors + (result.stream to next.before)
                pagingWarning = pagingWarning || next.warning
                pending = pending - result.stream
            } }
            ignored = available.sumOf { it.ignoredRows } + pages.mapNotNull { it.pages }.sumOf { it.ignoredRows }
            try { withContext(Dispatchers.IO) {
                history!!.save(pages.mapNotNull { it.pages }.flatMap { it.envelopes })
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cacheWarning = true }
            failed = pages.any { it.pages == null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { if (generation == loadGeneration) loading = false }
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
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.notifications_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null && !loading && !markingRead, onClick = {
                if (!loading && !markingRead) { pending = emptyMap(); pagingWarning = false; refresh++ }
            }) { Text(stringResource(R.string.feed_refresh)) }
        }
        PersonalControlsStatus(personal)
        if (loading) Text(stringResource(R.string.notifications_loading))
        if (failed) Text(stringResource(R.string.notifications_read_error))
        if (cacheWarning) Text(stringResource(R.string.notifications_cache_warning))
        if (readWarning) Text(stringResource(R.string.notifications_read_status_warning))
        if (ignored > 0) Text(stringResource(R.string.notifications_unverified))
        val repostRows = reposts.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(wallet?.publicKey, target, event.recipients,
                R.string.notifications_repost_own, R.string.notifications_repost)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val quoteRows = quotes.mapNotNull { quote ->
            val target = quote.quoted ?: knownPosts.firstOrNull { it.id == quote.quoteTarget }
            referenceCaption(wallet?.publicKey, target, quote.recipients,
                R.string.notifications_quote_own, R.string.notifications_quote)?.let { caption ->
                NotificationRow(quote.id, quote.author, quote.createdAt, quote, caption)
            }
        }
        val likeRows = events.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(wallet?.publicKey, target, event.recipients,
                R.string.notifications_like_own, R.string.notifications_like)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val replyRows = replies.mapNotNull { reply ->
            val target = knownPosts.firstOrNull { it.id == reply.parentId }
            referenceCaption(wallet?.publicKey, target, reply.recipients,
                R.string.notifications_reply_own, R.string.notifications_reply)?.let { caption ->
                NotificationRow(reply.id, reply.author, reply.createdAt, reply, caption)
            }
        }
        val contactRows = contacts.filter { it.author in followed }.map { post ->
            NotificationRow(post.id, post.author, post.createdAt, post, R.string.notifications_posted)
        }
        val rows = (quoteRows + repostRows + likeRows + replyRows +
            mentions.map { mention -> NotificationRow(mention.id, mention.author, mention.createdAt, mention, R.string.notifications_mention) } + contactRows)
            .distinctBy { it.id }
        val visible = rows.filter { personal?.hidesPosts(it.author) == false }.sortedByDescending { it.createdAt }
        val unread = visible.count { it.id !in readIds }
        Row {
            if (readReady) Text(if (unread == 0) stringResource(R.string.notifications_read_none)
                else stringResource(R.string.notifications_unread_count, unread), Modifier.weight(1f))
            TextButton(enabled = readReady && !markingRead && unread > 0, onClick = {
                markObserved(visible.map { it.id })
            }) { Text(stringResource(R.string.notifications_mark_read)) }
        }
        val retainedCount = (events.map { it.id } + replies.map { it.id } + mentions.map { it.id } +
            reposts.map { it.id } + quotes.map { it.id } + contacts.map { it.id }).distinct().size
        if (pagingWarning) Text(stringResource(R.string.notifications_paging_warning))
        if (retainedCount >= NotificationHistoryStore.MAX_EVENTS) Text(stringResource(R.string.notifications_history_limit))
        val eligibleStreams = notificationStreams(followed, ownPosts)
        val undiscovered = eligibleStreams.filter { it !in cursors }.take(20)
        val older = if (retainedCount < NotificationHistoryStore.MAX_EVENTS)
            cursors.filter { (stream, before) -> stream in eligibleStreams && before != null }
                .mapValues { requireNotNull(it.value) } else emptyMap()
        TextButton(enabled = wallet != null && !loading && !markingRead &&
            (undiscovered.isNotEmpty() || older.isNotEmpty()), onClick = {
            if (!loading && !markingRead) {
                val before = System.currentTimeMillis() + 1
                // Discover remaining known parents and contacts before spending the remaining budget on older pages.
                pending = (undiscovered.associateWith { before } + older).entries.take(MAX_NOTIFICATION_READS)
                    .associate { it.key to it.value }
                cursors = cursors + undiscovered.associateWith { before }
                if (pending.isNotEmpty()) refresh++
            }
        }) { Text(stringResource(R.string.notifications_more)) }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.id }) { event ->
                ActivityRow(event.author, event.createdAt, stringResource(event.caption),
                    unread = if (readReady) event.id !in readIds else null,
                    canMark = readReady && !markingRead && event.id !in readIds,
                    onMark = { markObserved(listOf(event.id)) },
                    onIdentity = { onIdentity(event.author) },
                    onOpen = { event.post?.let(onPost) ?: onReference(event.target) })
            }
        }
    }
    wallet?.publicKey?.let { SocialNotificationPermissionPrompt(it) }
}

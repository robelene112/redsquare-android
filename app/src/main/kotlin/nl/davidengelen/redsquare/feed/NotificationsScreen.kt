package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import nl.davidengelen.redsquare.NotificationPermissionPrompt
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity
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
    val mentions: ArchiveFeedPage, val reposts: ArchiveActionPage, val quotes: List<FeedPost>)

private fun notificationPages(likes: String, posts: String, retweets: String, owner: String,
    name: SaitoRegisteredName?, registrar: String): NotificationPages = NotificationPages(
    RedSquareLikes.readIncoming(likes, owner), RedSquareFeed.readReplies(posts),
    RedSquareFeed.readMentions(posts, owner, name, registrar),
    RedSquareActions.readIncomingReposts(retweets, owner),
    RedSquareFeed.readIncomingQuotes(retweets, owner).posts + RedSquareFeed.readIncomingQuotes(posts, owner).posts)

private fun cachedArchive(envelopes: List<String>, requests: Set<String>): String = com.google.gson.JsonArray().apply {
    envelopes.filter { SaitoEnvelope.decode(it).body.get("request").asString in requests }.take(100).forEach { envelope ->
        add(JsonObject().apply { addProperty("tx", envelope) })
    }
}.toString()

/** A successful smaller page does not erase verified activity already available on this phone. */
private fun <T> availableActivity(incoming: List<T>, retained: List<T>, id: (T) -> String,
    createdAt: (T) -> Long): List<T> = (incoming + retained).distinctBy(id)
    .sortedByDescending(createdAt).take(300)

@Composable
internal fun NotificationsScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, knownPosts: List<FeedPost>,
    modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit, onIdentity: (String) -> Unit, onReference: (String) -> Unit) {
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var events by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<ProfileLike>>(emptyList()) }
    var replies by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var mentions by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var reposts by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<RedSquareAction>>(emptyList()) }
    var quotes by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    val history = remember(wallet?.publicKey, backend) { wallet?.let { NotificationHistoryStore(context, backend, it.publicKey) } }
    var cacheWarning by remember(wallet?.publicKey, backend) { mutableStateOf(false) }
    var loading by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf(true) }
    var failed by remember(wallet?.publicKey, backend, endpoint) { mutableStateOf(false) }
    var ignored by remember(wallet?.publicKey, backend, endpoint) { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, backend, endpoint, refresh) {
        if (wallet == null) return@LaunchedEffect
        loading = true
        val name = try { withContext(Dispatchers.IO) { RegisteredNameStore(context, wallet.publicKey, registrar).load()?.name } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        fun publish(pages: NotificationPages) {
            events = availableActivity(pages.likes.likes, events, { it.id }, { it.createdAt })
            replies = availableActivity(pages.replies.posts.filter { it.author != wallet.publicKey && it.parentId.isNotEmpty() },
                replies, { it.id }, { it.createdAt })
            mentions = availableActivity(pages.mentions.posts, mentions, { it.id }, { it.createdAt })
            reposts = availableActivity(pages.reposts.actions, reposts, { it.id }, { it.createdAt })
            quotes = availableActivity(pages.quotes, quotes, { it.id }, { it.createdAt })
            ignored = pages.likes.ignored + pages.replies.ignored + pages.reposts.ignored
        }
        val saved = withContext(Dispatchers.IO) { history!!.load() }
        cacheWarning = saved.unreadable
        val cached = withContext(Dispatchers.IO) {
            notificationPages(cachedArchive(saved.envelopes, setOf("like tweet")),
                cachedArchive(saved.envelopes, setOf("create tweet")), cachedArchive(saved.envelopes, setOf("retweet")),
                wallet.publicKey, name, registrar)
        }
        publish(cached)
        try {
            val pages = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    suspend fun read(kind: String): String {
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", kind)
                            addProperty("field3", wallet.publicKey); addProperty("flagged_ne", 1)
                            addProperty("limit", 100); addProperty("created_earlier_than", System.currentTimeMillis() + 1)
                        }
                        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        return peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                    }
                    val likes = read("RedSquareLike")
                    val posts = read("RedSquare")
                    val retweets = read("RedSquareRetweet")
                    notificationPages(likes, posts, retweets, wallet.publicKey, name, registrar)
                }
            }
            publish(pages)
            try { withContext(Dispatchers.IO) {
                history!!.save(pages.likes.likes.map { it.envelope } + pages.replies.posts.map { it.envelope } +
                    pages.mentions.posts.map { it.envelope } + pages.reposts.actions.map { it.envelope } + pages.quotes.map { it.envelope })
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cacheWarning = true }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.notifications_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
        }
        PersonalControlsStatus(personal)
        if (loading) Text(stringResource(R.string.notifications_loading))
        if (failed) Text(stringResource(R.string.notifications_read_error))
        if (cacheWarning) Text(stringResource(R.string.notifications_cache_warning))
        if (ignored > 0) Text(stringResource(R.string.notifications_unverified))
        val repostRows = reposts.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(wallet?.publicKey, target, event.recipients,
                R.string.notifications_repost_own, R.string.notifications_repost)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val quoteRows = quotes.filter { quote ->
            quote.quoted?.author == wallet?.publicKey ||
                knownPosts.any { it.id == quote.quoteTarget && it.author == wallet?.publicKey }
        }.map { quote -> NotificationRow(quote.id, quote.author, quote.createdAt, quote, R.string.notifications_quote_own) }
        val likeRows = events.mapNotNull { event ->
            val target = knownPosts.firstOrNull { it.id == event.target }
            referenceCaption(wallet?.publicKey, target, event.recipients,
                R.string.notifications_like_own, R.string.notifications_like)?.let { caption ->
                NotificationRow(event.id, event.author, event.createdAt, target, caption, event.target)
            }
        }
        val rows = (quoteRows + repostRows + likeRows + replies.filter { reply ->
            knownPosts.any { it.id == reply.parentId && it.author == wallet?.publicKey }
        }.map { reply -> NotificationRow(reply.id, reply.author, reply.createdAt, reply, R.string.notifications_reply_own) } +
            mentions.map { mention -> NotificationRow(mention.id, mention.author, mention.createdAt, mention, R.string.notifications_mention) })
            .distinctBy { it.id }
        LazyColumn(Modifier.weight(1f)) {
            items(rows.filter { personal?.hidesPosts(it.author) == false }.sortedByDescending { it.createdAt }, key = { it.id }) { event ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(registeredIdentity(event.author).name, Modifier.clickable { onIdentity(event.author) })
                    Text(event.author, Modifier.clickable { onIdentity(event.author) }, style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(event.caption), Modifier.clickable { event.post?.let(onPost) ?: onReference(event.target) }
                        .padding(vertical = 8.dp))
                    HorizontalDivider()
                }
            }
        }
    }
    NotificationPermissionPrompt()
}

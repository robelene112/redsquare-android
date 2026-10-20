package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.AuthorActionKind

@Composable
internal fun ConversationScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, selected: FeedPost,
    modifier: Modifier, onBack: () -> Unit, onIdentity: (String) -> Unit, onPost: (FeedPost) -> Unit,
    social: SocialState? = null, quotes: QuoteState? = null, onQuote: ((FeedPost) -> Unit)? = null) {
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
    var replying by rememberSaveable(selected.id) { mutableStateOf(false) }
    var editing by rememberSaveable(selected.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(selected.id) { mutableStateOf(false) }
    if ((editing || deleting) && wallet != null && selected.author == wallet.publicKey) {
        AuthorEditScreen(wallet, endpoint, backend, posts.firstOrNull { it.id == selected.id } ?: selected, modifier,
            if (deleting) AuthorActionKind.DELETE else AuthorActionKind.EDIT) {
            editing = false; deleting = false; refresh++
        }
        return
    }
    if (replying && wallet != null) {
        PostComposerScreen(wallet, endpoint, backend, modifier, selected) { replying = false; refresh++ }
        return
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, selected.id, refresh) {
        if (wallet == null) { loading = false; return@LaunchedEffect }
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
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
        // Thread failure must not prevent recovery from the separately verified parent cache.
        if (selected.parentId.isNotEmpty() && posts.none { it.id == selected.parentId })
            quotes?.load(selected.parentId, refresh = refresh > 0)
    }
    val parent = quotes?.originals?.get(selected.parentId)
    val available = RedSquareFeed.newest(posts + listOfNotNull(parent?.post)).sortedBy { it.createdAt }
    val parentMissing = selected.parentId.isNotEmpty() && available.none { it.id == selected.parentId }
    val visiblePosts = available.filter { personal?.hidesPosts(it.author) == false }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.conversation_title), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        Row {
            TextButton(enabled = wallet != null && posts.any { it.id == selected.id }, onClick = { replying = true }) { Text(stringResource(R.string.post_reply)) }
            if (wallet != null && selected.author == wallet.publicKey && posts.any { it.id == selected.id }) {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.author_edit)) }
                TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.author_delete)) }
            }
        }
        PersonalControlsStatus(personal)
        if (personal?.ready == true && personal.hidesPosts(selected.author))
            Text(stringResource(R.string.personal_post_hidden), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.conversation_history), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.conversation_loading))
        if (failed) Text(stringResource(R.string.conversation_error))
        if (unreadable) Text(stringResource(R.string.conversation_unreadable))
        if (cacheWarning || parent?.cacheWarning == true) Text(stringResource(R.string.feed_cache_warning), style = MaterialTheme.typography.bodySmall)
        if (parentMissing) {
            if (parent?.loading == true) Text(stringResource(R.string.conversation_parent_loading))
            else if (!loading) Text(stringResource(if (failed || parent?.failed == true)
                R.string.conversation_parent_error else R.string.conversation_parent_unavailable))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(visiblePosts, key = { it.id }) { post ->
                PostCard(post, backend, onIdentity, onPost, social, quotes, onQuote)
                HorizontalDivider()
            }
        }
    }
}

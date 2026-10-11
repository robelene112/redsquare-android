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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*

/** Retained by the wallet/backend navigation memory, never by an old coroutine scope. */
internal class SearchViewState {
    var query by mutableStateOf("")
    var searchedQuery by mutableStateOf("")
    var request by mutableIntStateOf(0)
    var completed = 0
    var posts by mutableStateOf<List<FeedPost>>(emptyList())
    var failed by mutableStateOf(false)
    var unreadable by mutableStateOf(false)
    var cursors by mutableStateOf<Map<String, Long?>>(emptyMap())
    var pending by mutableStateOf<List<String>>(emptyList())
    var pagingUnavailable by mutableStateOf(false)
    var limitReached by mutableStateOf(false)
    var saveError by mutableStateOf(false)

    fun search() {
        searchedQuery = query.trim()
        val before = System.currentTimeMillis() + 1
        cursors = listOf("RedSquare", "RedSquareRetweet").associateWith { before }
        pending = cursors.keys.toList()
        pagingUnavailable = false
        limitReached = false
        request++
    }

    fun loadMore() {
        pending = cursors.filterValues { it != null }.keys.toList()
        request++
    }
}

@Composable
internal fun SearchScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend,
    view: SearchViewState, modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit,
    onIdentity: (String) -> Unit, social: SocialState?, quotes: QuoteState?, onQuote: (FeedPost) -> Unit) {
    val context = LocalContext.current.applicationContext
    val keyboard = LocalSoftwareKeyboardController.current
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
    val cache = remember(backend) { AndroidSearchCache(context, backend) }
    val revision by AndroidPostRevisions.changes.collectAsState()
    var loading by remember(view) { mutableStateOf(false) }
    var cacheReady by remember(view) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(view, cache) {
        val saved = withContext(Dispatchers.IO) { cache.load() }
        // A retained view or newer proof can change while disk validation is suspended.
        while (true) {
            val known = view.posts
            val checkedRevision = AndroidPostRevisions.changes.value
            val retained = withContext(Dispatchers.IO) { cache.retain(known + saved.posts, view.searchedQuery) }
            if (view.posts !== known || AndroidPostRevisions.changes.value != checkedRevision) continue
            view.posts = retained.posts
            view.unreadable = view.unreadable || saved.unreadable || retained.unreadable
            cacheReady = true
            break
        }
    }
    LaunchedEffect(view, revision, view.posts) {
        val known = view.posts
        val checkedRevision = AndroidPostRevisions.changes.value
        val applied = withContext(Dispatchers.IO) { revisions.apply(known) }
        // A newer archive page or proof can arrive while disk validation is suspended.
        if (view.posts === known && AndroidPostRevisions.changes.value == checkedRevision) {
            view.posts = applied.posts
            view.unreadable = view.unreadable || applied.unreadable
        }
    }
    LaunchedEffect(wallet, endpoint, view.request, cacheReady) {
        if (wallet == null || !cacheReady || view.request == 0 || view.completed == view.request) return@LaunchedEffect
        val request = view.request
        val pages = view.pending.mapNotNull { stream -> view.cursors[stream]?.let { stream to it } }
        loading = true
        view.failed = false
        try {
            withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    for ((stream, before) in pages) {
                        if (withContext(Dispatchers.Main.immediate) { view.limitReached }) break
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", stream)
                            addProperty("flagged_ne", 1); addProperty("limit", 100)
                            addProperty("created_earlier_than", before)
                        }
                        val query = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                        val page = if (stream == "RedSquare") RedSquareFeed.readSearch(response)
                            else RedSquareFeed.readCurrentQuotes(response)
                        val known = withContext(Dispatchers.Main.immediate) { view.posts.toList() }
                        revisions.reconcileDeletions(peer, page.deletions, page.posts + known)
                        page.posts.forEach(revisions::observe)
                        val applied = cache.retain(page.posts + known, withContext(Dispatchers.Main.immediate) { view.searchedQuery })
                        withContext(Dispatchers.Main.immediate) {
                            social?.observe(page.posts)
                            view.posts = applied.posts
                            view.unreadable = view.unreadable || applied.unreadable || page.ignored > 0
                            val next = page.oldestCreatedAt?.takeIf { page.rowCount == 100 && it < before }
                            view.pagingUnavailable = view.pagingUnavailable || (page.rowCount == 100 && next == null)
                            view.cursors = view.cursors + (stream to next)
                            view.pending = view.pending - stream
                            if (applied.limited) {
                                view.limitReached = true
                                view.cursors = view.cursors.mapValues { null }
                                view.pending = emptyList()
                            }
                        }
                        val retained = withContext(Dispatchers.Main.immediate) { view.posts to view.searchedQuery }
                        val saved = try { cache.save(retained.first, retained.second); true }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                        withContext(Dispatchers.Main.immediate) { view.saveError = !saved }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            view.failed = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { view.failed = true }
        finally {
            if (currentCoroutineContext().isActive) { view.completed = request; loading = false }
        }
    }
    val matches = view.posts.filter { it.text.contains(view.searchedQuery, ignoreCase = true) }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.search_title), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = view.query, onValueChange = { if (it.length <= 256) view.query = it },
            label = { Text(stringResource(R.string.search_query)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(modifier = Modifier.weight(1f), enabled = wallet != null && cacheReady && !loading && view.query.isNotBlank(), onClick = {
                keyboard?.hide()
                loading = true
                view.search()
            }) { Text(stringResource(R.string.search_history)) }
            if (view.failed) {
                TextButton(modifier = Modifier.weight(1f), enabled = wallet != null && !loading && view.pending.isNotEmpty(),
                    onClick = { loading = true; view.request++ }) { Text(stringResource(R.string.search_retry)) }
            } else {
                TextButton(modifier = Modifier.weight(1f), enabled = wallet != null && !loading && view.cursors.values.any { it != null },
                    onClick = { loading = true; view.loadMore() }) { Text(stringResource(R.string.search_more)) }
            }
        }
        Text(stringResource(R.string.search_coverage), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.search_loading))
        if (view.failed) Text(stringResource(R.string.search_error))
        if (view.unreadable) Text(stringResource(R.string.search_unreadable), style = MaterialTheme.typography.bodySmall)
        if (view.pagingUnavailable) Text(stringResource(R.string.search_paging_unavailable), style = MaterialTheme.typography.bodySmall)
        if (view.limitReached) Text(stringResource(R.string.search_limit), style = MaterialTheme.typography.bodySmall)
        if (view.saveError) Text(stringResource(R.string.search_save_error), style = MaterialTheme.typography.bodySmall)
        if (view.request > 0 && !loading && !view.failed && !view.limitReached && matches.isEmpty()) Text(stringResource(R.string.search_empty))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (view.request > 0) items(matches, key = { it.id }) { post ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(registeredIdentity(post.author).name, Modifier.clickable { onIdentity(post.author) },
                        style = MaterialTheme.typography.titleMedium)
                    Text(post.text, Modifier.clickable { onPost(post) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                    PostImages(post.images, backend)
                    PostVideo(post, backend)
                    QuoteContent(post, quotes, backend)
                    SocialActions(post, social, onQuote)
                }
                HorizontalDivider()
            }
        }
    }
}

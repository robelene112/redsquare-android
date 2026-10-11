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
}

@Composable
internal fun SearchScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend,
    view: SearchViewState, modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit,
    onIdentity: (String) -> Unit, social: SocialState?, quotes: QuoteState?, onQuote: (FeedPost) -> Unit) {
    val context = LocalContext.current.applicationContext
    val keyboard = LocalSoftwareKeyboardController.current
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
    val revision by AndroidPostRevisions.changes.collectAsState()
    var loading by remember(view) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(view, revision, view.posts) {
        val known = view.posts
        val applied = withContext(Dispatchers.IO) { revisions.apply(known) }
        view.posts = applied.posts
        view.unreadable = view.unreadable || applied.unreadable
    }
    LaunchedEffect(wallet, endpoint, view.request) {
        if (wallet == null || view.request == 0 || view.completed == view.request) return@LaunchedEffect
        val request = view.request
        loading = true
        view.failed = false
        val before = System.currentTimeMillis() + 1
        try {
            withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    for (stream in listOf("RedSquare", "RedSquareRetweet")) {
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
                        val applied = revisions.apply(RedSquareFeed.newest(page.posts + known))
                        withContext(Dispatchers.Main.immediate) {
                            social?.observe(page.posts)
                            view.posts = applied.posts.sortedByDescending { it.createdAt }.take(1000)
                            view.unreadable = view.unreadable || applied.unreadable || page.ignored > 0
                        }
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
        TextButton(enabled = wallet != null && !loading && view.query.isNotBlank(), onClick = {
            keyboard?.hide()
            view.searchedQuery = view.query.trim()
            view.request++
        }) { Text(stringResource(R.string.search_history)) }
        Text(stringResource(R.string.search_coverage), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.search_loading))
        if (view.failed) Text(stringResource(R.string.search_error))
        if (view.unreadable) Text(stringResource(R.string.search_unreadable), style = MaterialTheme.typography.bodySmall)
        if (view.request > 0 && !loading && !view.failed && matches.isEmpty()) Text(stringResource(R.string.search_empty))
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

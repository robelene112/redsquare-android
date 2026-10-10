package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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

@Composable
internal fun ConversationScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, selected: FeedPost,
    modifier: Modifier, onBack: () -> Unit, onIdentity: (String) -> Unit, onPost: (FeedPost) -> Unit,
    social: SocialState? = null) {
    val context = LocalContext.current.applicationContext
    val cache = remember(backend, selected.threadId) { AndroidFeedCache(context, backend, selected.threadId) }
    var posts by remember(selected.id) { mutableStateOf(listOf(selected)) }
    var loading by remember(selected.id) { mutableStateOf(true) }
    var failed by remember(selected.id) { mutableStateOf(false) }
    var unreadable by remember(selected.id) { mutableStateOf(false) }
    var cacheWarning by remember(selected.id) { mutableStateOf(false) }
    var refresh by remember(selected.id) { mutableIntStateOf(0) }
    var replying by rememberSaveable(selected.id) { mutableStateOf(false) }
    if (replying && wallet != null) {
        PostComposerScreen(wallet, endpoint, backend, modifier, selected) { replying = false; refresh++ }
        return
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, selected.id, refresh) {
        if (wallet == null) { loading = false; return@LaunchedEffect }
        loading = true
        val saved = withContext(Dispatchers.IO) { cache.load() }
        posts = (posts + saved.posts).distinctBy { it.id }.sortedBy { it.createdAt }
        cacheWarning = saved.unreadable
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
                SaitoPeer(wallet, endpoint).use { peer ->
                    RedSquareFeed.readConversation(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                        .toString(Charsets.UTF_8), selected.threadId)
                }
            }
            posts = (posts + page.posts).distinctBy { it.id }.sortedBy { it.createdAt }
            cacheWarning = withContext(Dispatchers.IO) {
                try { cache.save(posts); false } catch (_: Exception) { true }
            }
            unreadable = page.ignored > 0
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.conversation_title), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null, onClick = { replying = true }) { Text(stringResource(R.string.post_reply)) }
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        Text(stringResource(R.string.conversation_history), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.conversation_loading))
        if (failed) Text(stringResource(R.string.conversation_error))
        if (unreadable) Text(stringResource(R.string.conversation_unreadable))
        if (cacheWarning) Text(stringResource(R.string.feed_cache_warning), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(posts, key = { it.id }) { post ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Anon-${post.author.take(6)}", Modifier.clickable { onIdentity(post.author) },
                        style = MaterialTheme.typography.titleMedium)
                    Text(post.text, Modifier.clickable { onPost(post) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                    SocialActions(post, social)
                }
                HorizontalDivider()
            }
        }
    }
}

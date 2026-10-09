package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

@Composable
internal fun ConversationScreen(wallet: SaitoWallet?, endpoint: String, selected: FeedPost,
    modifier: Modifier, onBack: () -> Unit) {
    var posts by remember(selected.id) { mutableStateOf(listOf(selected)) }
    var loading by remember(selected.id) { mutableStateOf(true) }
    var failed by remember(selected.id) { mutableStateOf(false) }
    var unreadable by remember(selected.id) { mutableStateOf(false) }
    var refresh by remember(selected.id) { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, selected.id, refresh) {
        if (wallet == null) { loading = false; return@LaunchedEffect }
        loading = true
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
            posts = (listOf(selected) + page.posts).distinctBy { it.id }.sortedBy { it.createdAt }
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
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        Text(stringResource(R.string.conversation_history), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.conversation_loading))
        if (failed) Text(stringResource(R.string.conversation_error))
        if (unreadable) Text(stringResource(R.string.conversation_unreadable))
        LazyColumn(Modifier.fillMaxSize()) {
            items(posts, key = { it.id }) { post ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Anon-${post.author.take(6)}", style = MaterialTheme.typography.titleMedium)
                    Text(post.text, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }
}

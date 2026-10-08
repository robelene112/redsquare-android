package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FeedScreen(wallet: SaitoWallet?, walletUnavailable: Boolean, endpoint: String, modifier: Modifier, onDrawn: () -> Unit) {
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(wallet, refresh) {
        if (wallet == null) return@LaunchedEffect
        loading = true
        failed = false
        try {
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val criteria = """{"request":"archive","data":{"request":"load","field1":"RedSquare","field4":"","flagged_ne":1,"limit":20,"created_earlier_than":${System.currentTimeMillis()}}}"""
                    RedSquareFeed.readArchive(peer.request(criteria.toByteArray()).toString(Charsets.UTF_8))
                }
            }
            posts = page.posts.sortedByDescending { it.createdAt }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(posts.isNotEmpty()) {
        if (posts.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            onDrawn()
        }
    }
    Column(modifier) {
        Row {
            Text(stringResource(R.string.feed_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { refresh++ }, enabled = wallet != null && !loading) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        when {
            failed -> Text(stringResource(R.string.feed_error))
            loading && !walletUnavailable -> Text(stringResource(R.string.feed_loading))
            posts.isEmpty() && wallet != null -> Text(stringResource(R.string.feed_empty))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(posts, key = { it.id }) { post ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("Anon-${post.author.take(6)}", style = MaterialTheme.typography.titleMedium)
                    Text(post.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text(post.text, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH)
                        .format(Date(post.createdAt)), style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
        }
    }
}

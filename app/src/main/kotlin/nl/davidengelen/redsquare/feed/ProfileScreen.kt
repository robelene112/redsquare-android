package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import nl.davidengelen.redsquare.saito.*

private enum class ProfileTab(val label: Int) {
    POSTS(R.string.profile_posts), REPLIES(R.string.profile_replies), LIKES(R.string.profile_likes)
}

@Composable
internal fun ProfileScreen(wallet: SaitoWallet?, endpoint: String, author: String, modifier: Modifier,
    onBack: () -> Unit, onPost: (FeedPost) -> Unit) {
    var tab by remember(author) { mutableStateOf(ProfileTab.POSTS) }
    var posts by remember(author, tab) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember(author, tab) { mutableStateOf(true) }
    var failed by remember(author, tab) { mutableStateOf(false) }
    var unreadable by remember(author, tab) { mutableStateOf(false) }
    var unavailableLikes by remember(author, tab) { mutableStateOf(false) }
    var refresh by remember(author) { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, author, tab, refresh) {
        if (wallet == null) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    suspend fun archive(criteria: JsonObject): String {
                        criteria.addProperty("request", "load")
                        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        return peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                    }
                    val criteria = JsonObject().apply {
                        addProperty("field1", if (tab == ProfileTab.LIKES) "RedSquareLike" else "RedSquare")
                        addProperty("field2", author)
                        addProperty("created_earlier_than", System.currentTimeMillis())
                        addProperty("limit", 20)
                        if (tab != ProfileTab.LIKES) addProperty("flagged_ne", 1)
                    }
                    if (tab != ProfileTab.LIKES) {
                        val page = RedSquareFeed.readAuthor(archive(criteria), author)
                        Triple(page.posts.filter { (it.parentId.isEmpty()) == (tab == ProfileTab.POSTS) }, page.ignored > 0, false)
                    } else {
                        val page = RedSquareLikes.readArchive(archive(criteria), author)
                        var missing = false
                        var ignored = page.ignored > 0
                        val targets = mutableListOf<FeedPost>()
                        for (target in page.likes.map { it.target }.distinct()) {
                            val query = JsonObject().apply {
                                addProperty("field1", "RedSquare"); addProperty("sig", target); addProperty("flagged_ne", 1)
                            }
                            val targetPage = RedSquareFeed.readTarget(archive(query), target)
                            targets.addAll(targetPage.posts)
                            if (targetPage.posts.isEmpty()) missing = true
                            if (targetPage.ignored > 0) ignored = true
                        }
                        Triple(targets, ignored, missing)
                    }
                }
            }
            posts = (result.first + posts).distinctBy { it.id }.sortedByDescending { it.createdAt }
            unreadable = result.second
            unavailableLikes = result.third
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.profile_title), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
        }
        Text("Anon-${author.take(6)}", style = MaterialTheme.typography.titleMedium)
        Text(author, style = MaterialTheme.typography.labelSmall)
        Row {
            ProfileTab.entries.forEach { item ->
                TextButton(onClick = { tab = item }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(item.label), color = if (item == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(stringResource(R.string.profile_history), style = MaterialTheme.typography.bodySmall)
        if (loading) Text(stringResource(R.string.profile_loading))
        if (failed) Text(stringResource(R.string.profile_error))
        if (unreadable) Text(stringResource(R.string.profile_unreadable), style = MaterialTheme.typography.bodySmall)
        if (unavailableLikes) Text(stringResource(R.string.profile_likes_unavailable), style = MaterialTheme.typography.bodySmall)
        if (!loading && !failed && posts.isEmpty()) Text(stringResource(R.string.profile_empty))
        LazyColumn(Modifier.fillMaxSize()) {
            items(posts, key = { it.id }) { post ->
                Column(Modifier.clickable { onPost(post) }.padding(vertical = 12.dp)) {
                    Text("Anon-${post.author.take(6)}", style = MaterialTheme.typography.titleMedium)
                    Text(post.text, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }
}

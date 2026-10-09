package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private sealed interface BrowseDestination {
    data class Conversation(val post: FeedPost) : BrowseDestination
    class Profile(val author: String, val view: ProfileViewState = ProfileViewState()) : BrowseDestination
}

@Composable
fun FeedScreen(wallet: SaitoWallet?, walletUnavailable: Boolean, endpoint: String, backend: SaitoBackend, lifecycle: Lifecycle, modifier: Modifier, onDrawn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val following = remember(wallet?.publicKey) { wallet?.let { FollowingState(context, it.publicKey, scope) } }
    LaunchedEffect(following) { following?.load() }
    var followingView by rememberSaveable { mutableStateOf(false) }
    val followingOwner = if (followingView) wallet?.publicKey else null
    val cache = remember(backend, followingOwner) { AndroidFeedCache(context, backend, followingOwner = followingOwner) }
    val connection = remember { AndroidInternetStatus(context) }
    DisposableEffect(connection) { onDispose { connection.close() } }
    val observedInternet by connection.state.collectAsState()
    // A controlled loopback peer does not depend on the emulator's public internet.
    val internet = if (endpoint.startsWith("ws://127.0.0.1:")) InternetStatus(true, true, 0) else observedInternet
    var posts by remember(cache) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var cacheReady by remember(cache) { mutableStateOf(false) }
    var cacheWarning by remember(cache) { mutableStateOf(false) }
    var loading by remember(cache) { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var unreadablePage by remember { mutableStateOf(false) }
    var recovered by remember { mutableStateOf(false) }
    var hadOutage by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var composing by rememberSaveable { mutableStateOf(false) }
    val navigation = remember { mutableStateListOf<BrowseDestination>() }
    fun openProfile(author: String) {
        if ((navigation.lastOrNull() as? BrowseDestination.Profile)?.author != author) {
            navigation.add(BrowseDestination.Profile(author))
        }
    }
    fun back() { if (navigation.isNotEmpty()) navigation.removeAt(navigation.lastIndex) }
    val position = rememberLazyListState()

    LaunchedEffect(cache) {
        val saved = withContext(Dispatchers.IO) { cache.load() }
        posts = saved.posts
        cacheWarning = saved.unreadable
        cacheReady = true
    }
    LaunchedEffect(wallet, cacheReady, refresh, internet, followingView, following?.accounts, following?.ready) {
        if (!internet.available) { hadOutage = true; recovered = false }
        if (wallet == null || !cacheReady) return@LaunchedEffect
        if (followingView && following?.ready != true) { loading = false; return@LaunchedEffect }
        if (followingView && following?.accounts?.isEmpty() == true) { loading = false; return@LaunchedEffect }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                if (!internet.available) {
                    loading = false
                    hadOutage = true
                    recovered = false
                    delay(1000)
                    continue
                }
                loading = true
                try {
                    withContext(Dispatchers.IO) {
                        SaitoPeer(wallet, endpoint).use { peer ->
                            val authors = if (followingView) following!!.accounts.sorted() else listOf<String?>(null)
                            var rowCount = 0
                            var validCount = 0
                            for (author in authors) {
                                val criteria = com.google.gson.JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", "RedSquare")
                                    addProperty("field4", ""); addProperty("flagged_ne", 1); addProperty("limit", 20)
                                    addProperty("created_earlier_than", System.currentTimeMillis())
                                    if (author != null) addProperty("field2", author)
                                }
                                val request = com.google.gson.JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                                val response = peer.request(request.toString().toByteArray()).toString(Charsets.UTF_8)
                                val page = RedSquareFeed.readArchive(response).let { page ->
                                    if (author == null) page else page.copy(posts = page.posts.filter { it.author == author })
                                }
                                rowCount += page.rowCount
                                validCount += page.posts.size
                                withContext(Dispatchers.Main.immediate) {
                                    val merged = (page.posts + posts).distinctBy { it.id }
                                        .filter { !followingView || it.author in following!!.accounts }
                                        .sortedByDescending { it.createdAt }
                                        .take(AndroidFeedCache.MAX_POSTS)
                                    cacheWarning = withContext(Dispatchers.IO) {
                                        try { cache.save(merged); false } catch (_: Exception) { true }
                                    }
                                    posts = merged
                                    unreadablePage = rowCount > 0 && validCount == 0
                                }
                            }
                        }
                    }
                    failed = false
                    if (hadOutage) { recovered = true; hadOutage = false }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; hadOutage = true; recovered = false }
                finally { if (currentCoroutineContext().isActive) loading = false }
                delay(if (failed) 5_000 else 30_000)
            }
        }
    }
    LaunchedEffect(recovered) { if (recovered) { delay(3_000); recovered = false } }
    LaunchedEffect(posts.isNotEmpty()) {
        if (posts.isNotEmpty()) { withFrameNanos { }; withFrameNanos { }; onDrawn() }
    }
    if (composing && wallet != null) {
        PostComposerScreen(wallet.publicKey, backend, modifier) { composing = false }
        return
    }
    when (val destination = navigation.lastOrNull()) {
        is BrowseDestination.Profile -> {
            ProfileScreen(wallet, endpoint, backend, destination.author, destination.view, following, modifier,
                onBack = { back() }, onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                onIdentity = { openProfile(it) })
            return
        }
        is BrowseDestination.Conversation -> {
            ConversationScreen(wallet, endpoint, backend, destination.post, modifier,
                onBack = { back() }, onIdentity = { openProfile(it) })
            return
        }
        null -> Unit
    }
    Column(modifier) {
        val noInternet = !internet.available || (failed && !internet.validated)
        when {
            noInternet -> ConnectionBanner(stringResource(R.string.no_internet), false)
            failed -> ConnectionBanner(stringResource(R.string.server_unavailable), false)
            recovered -> ConnectionBanner(stringResource(R.string.back_online), true)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { followingView = false }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.feed_title), maxLines = 1)
            }
            TextButton(onClick = { followingView = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.following_title), maxLines = 1)
            }
            TextButton(onClick = { refresh++ }, enabled = wallet != null && !loading && internet.available) {
                Text(stringResource(R.string.feed_refresh))
            }
        }
        TextButton(enabled = wallet != null, onClick = { composing = true }) { Text(stringResource(R.string.post_new)) }
        val visiblePosts = if (followingView) posts.filter { it.author in following?.accounts.orEmpty() } else posts
        FollowingStatus(following)
        if (followingView) {
            Text(stringResource(R.string.following_local), style = MaterialTheme.typography.bodySmall)
            if (following == null || following.loading) Text(stringResource(R.string.following_loading))
            else if (following.ready && following.accounts.isEmpty()) Text(stringResource(R.string.following_empty))
            else if (following.ready && visiblePosts.isEmpty() && !loading) Text(stringResource(R.string.following_no_posts))
        }
        if (cacheWarning) Text(stringResource(R.string.feed_cache_warning), style = MaterialTheme.typography.bodySmall)
        when {
            failed -> Text(stringResource(R.string.feed_error), style = MaterialTheme.typography.bodySmall)
            posts.isEmpty() && loading && !walletUnavailable -> Text(stringResource(R.string.feed_loading))
            posts.isEmpty() && !internet.available -> Text(stringResource(R.string.feed_offline_empty))
            unreadablePage -> Text(stringResource(R.string.feed_unreadable))
            posts.isEmpty() && !loading && wallet != null -> Text(stringResource(R.string.feed_empty))
        }
        // Keep a restored list position until the asynchronous cache read has finished.
        if (cacheReady) LazyColumn(Modifier.fillMaxSize(), state = position) {
            items(visiblePosts, key = { it.id }) { post ->
                Column(Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 12.dp)) {
                    Text("Anon-${post.author.take(6)}", Modifier.clickable { openProfile(post.author) }, style = MaterialTheme.typography.titleMedium)
                    Text(post.author, Modifier.clickable { openProfile(post.author) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text(post.text, Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH)
                        .format(Date(post.createdAt)), style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ConnectionBanner(message: String, online: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (online) Color(0xff168039) else MaterialTheme.colorScheme.error, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.labelMedium)
    }
}

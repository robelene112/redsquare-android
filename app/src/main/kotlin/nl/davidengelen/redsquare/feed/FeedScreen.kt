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

@Composable
fun FeedScreen(wallet: SaitoWallet?, walletUnavailable: Boolean, endpoint: String, backend: SaitoBackend, lifecycle: Lifecycle, modifier: Modifier, onDrawn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val cache = remember(backend) { AndroidFeedCache(context, backend) }
    val connection = remember { AndroidInternetStatus(context) }
    DisposableEffect(connection) { onDispose { connection.close() } }
    val observedInternet by connection.state.collectAsState()
    // A controlled loopback peer does not depend on the emulator's public internet.
    val internet = if (endpoint.startsWith("ws://127.0.0.1:")) InternetStatus(true, true, 0) else observedInternet
    var posts by remember { mutableStateOf<List<FeedPost>>(emptyList()) }
    var cacheReady by remember { mutableStateOf(false) }
    var cacheWarning by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var unreadablePage by remember { mutableStateOf(false) }
    var recovered by remember { mutableStateOf(false) }
    var hadOutage by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var conversation by remember { mutableStateOf<FeedPost?>(null) }
    val position = rememberLazyListState()

    LaunchedEffect(cache) {
        val saved = withContext(Dispatchers.IO) { cache.load() }
        posts = saved.posts
        cacheWarning = saved.unreadable
        cacheReady = true
    }
    LaunchedEffect(wallet, cacheReady, refresh, internet) {
        if (!internet.available) { hadOutage = true; recovered = false }
        if (wallet == null || !cacheReady) return@LaunchedEffect
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
                    val page = withContext(Dispatchers.IO) {
                        SaitoPeer(wallet, endpoint).use { peer ->
                            val criteria = """{"request":"archive","data":{"request":"load","field1":"RedSquare","field4":"","flagged_ne":1,"limit":20,"created_earlier_than":${System.currentTimeMillis()}}}"""
                            RedSquareFeed.readArchive(peer.request(criteria.toByteArray()).toString(Charsets.UTF_8))
                        }
                    }
                    val merged = (page.posts + posts).distinctBy { it.id }.sortedByDescending { it.createdAt }
                        .take(AndroidFeedCache.MAX_POSTS)
                    cacheWarning = withContext(Dispatchers.IO) {
                        try { cache.save(merged); false } catch (_: Exception) { true }
                    }
                    posts = merged
                    unreadablePage = page.rowCount > 0 && page.posts.isEmpty()
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
    conversation?.let { selected ->
        ConversationScreen(wallet, endpoint, backend, selected, modifier) { conversation = null }
        return
    }
    Column(modifier) {
        val noInternet = !internet.available || (failed && !internet.validated)
        when {
            noInternet -> ConnectionBanner(stringResource(R.string.no_internet), false)
            failed -> ConnectionBanner(stringResource(R.string.server_unavailable), false)
            recovered -> ConnectionBanner(stringResource(R.string.back_online), true)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.feed_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { refresh++ }, enabled = wallet != null && !loading && internet.available) {
                Text(stringResource(R.string.feed_refresh))
            }
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
            items(posts, key = { it.id }) { post ->
                Column(Modifier.clickable { conversation = post }.padding(vertical = 12.dp)) {
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

@Composable
private fun ConnectionBanner(message: String, online: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (online) Color(0xff168039) else MaterialTheme.colorScheme.error, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.labelMedium)
    }
}

package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.ViewModelProvider
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
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val context = activity.applicationContext
    val scope = rememberCoroutineScope()
    val personal = LocalPersonalControls.current
    val social = remember(wallet, backend, endpoint) { wallet?.let { SocialState(context, it, backend, endpoint, scope) } }
    val quotes = remember(wallet, backend, endpoint) { QuoteState(context, wallet, backend, endpoint, scope) }
    val following = remember(wallet?.publicKey) { wallet?.let { FollowingState(context, it.publicKey, scope) } }
    LaunchedEffect(following) { following?.load() }
    var followingView by rememberSaveable { mutableStateOf(false) }
    val followingOwner = if (followingView) wallet?.publicKey else null
    val cache = remember(backend, followingOwner) { AndroidFeedCache(context, backend, followingOwner = followingOwner) }
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
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
    var reviewingQueue by rememberSaveable { mutableStateOf(false) }
    var reviewingChanges by rememberSaveable { mutableStateOf(false) }
    val navigationMemory = remember(activity, wallet?.publicKey, backend) {
        ViewModelProvider(activity)["browse:${wallet?.publicKey.orEmpty()}:${backend.id}", FeedNavigationMemory::class.java]
    }
    val navigation = navigationMemory.destinations
    var queueWarning by remember { mutableStateOf(false) }
    LaunchedEffect(wallet, backend, endpoint) {
        if (wallet == null) return@LaunchedEffect
        try { withContext(Dispatchers.IO) { PostQueueScheduling.enqueue(context, wallet.publicKey, backend, endpoint) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { queueWarning = true }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                queueWarning = try {
                    withContext(Dispatchers.IO) { resumeQueuedPosts(context, wallet, backend, endpoint) }
                    false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { true }
                delay(5_000)
            }
        }
    }
    fun openProfile(author: String) {
        if (author !in navigationMemory.visitedProfiles) {
            if (navigationMemory.visitedProfiles.size == 1000) navigationMemory.visitedProfiles.removeAt(0)
            navigationMemory.visitedProfiles.add(author)
        }
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
                                suspend fun publish(page: ArchiveFeedPage) {
                                    val accepted = if (author == null) page else page.copy(posts = page.posts.filter { it.author == author })
                                    rowCount += accepted.rowCount
                                    validCount += accepted.posts.size
                                    val known = withContext(Dispatchers.Main.immediate) { posts.toList() }
                                    revisions.reconcileDeletions(peer, accepted.deletions, accepted.posts + known)
                                    withContext(Dispatchers.Main.immediate) {
                                        social?.observe(accepted.posts)
                                        val applied = withContext(Dispatchers.IO) {
                                            accepted.posts.forEach(revisions::observe)
                                            revisions.apply(RedSquareFeed.newest(accepted.posts + posts))
                                        }
                                        val merged = applied.posts
                                            .filter { !followingView || it.author in following!!.accounts }
                                            .sortedByDescending { it.createdAt }.take(AndroidFeedCache.MAX_POSTS)
                                        cacheWarning = withContext(Dispatchers.IO) {
                                            try { cache.save(merged); applied.unreadable } catch (_: Exception) { true }
                                        }
                                        posts = merged
                                        unreadablePage = rowCount > 0 && validCount == 0
                                    }
                                }
                                publish(RedSquareFeed.readArchive(response))
                                val quoteCriteria = criteria.deepCopy().apply {
                                    addProperty("field1", "RedSquareRetweet"); remove("field4")
                                }
                                val quoteRequest = com.google.gson.JsonObject().apply { addProperty("request", "archive"); add("data", quoteCriteria) }
                                val quoteResponse = peer.request(quoteRequest.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                                publish(RedSquareFeed.readCurrentQuotes(quoteResponse))
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
    LaunchedEffect(posts.isNotEmpty(), personal?.ready) {
        if (posts.isNotEmpty() && personal?.ready == true) { withFrameNanos { }; withFrameNanos { }; onDrawn() }
    }
    if (reviewingQueue && wallet != null) {
        QueuedPostsScreen(wallet.publicKey, backend, modifier) { reviewingQueue = false }
        return
    }
    if (reviewingChanges && wallet != null) {
        SavedAuthorActionsScreen(wallet, endpoint, backend, modifier) { reviewingChanges = false; refresh++ }
        return
    }
    if (composing && wallet != null) {
        PostComposerScreen(wallet, endpoint, backend, modifier) { composing = false; refresh++ }
        return
    }
    when (val destination = navigation.lastOrNull()) {
        is BrowseDestination.Notifications -> {
            NotificationsScreen(wallet, endpoint, backend, posts, modifier, onBack = { back() },
                onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                onIdentity = { openProfile(it) },
                onReference = { navigation.add(BrowseDestination.Reference(it)) })
            return
        }
        is BrowseDestination.Search -> {
            SearchScreen(wallet, endpoint, backend, destination.view, modifier,
                knownAccounts = following?.accounts.orEmpty() + navigationMemory.visitedProfiles,
                onBack = { back() }, onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                onIdentity = { openProfile(it) }, social = social, quotes = quotes,
                onQuote = { navigation.add(BrowseDestination.Quote(it)) })
            return
        }
        is BrowseDestination.Profile -> {
            ProfileScreen(wallet, endpoint, backend, destination.author, destination.view, following, modifier,
                onBack = { back() }, onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                onIdentity = { openProfile(it) }, social = social, quotes = quotes, onQuote = { navigation.add(BrowseDestination.Quote(it)) })
            return
        }
        is BrowseDestination.Reference -> {
            NotificationReferenceScreen(wallet, endpoint, backend, destination.target, quotes, modifier,
                onBack = { back() }, onIdentity = { openProfile(it) },
                onPost = { navigation.add(BrowseDestination.Conversation(it)) }, social = social,
                onQuote = { navigation.add(BrowseDestination.Quote(it)) })
            return
        }
        is BrowseDestination.Conversation -> {
            ConversationScreen(wallet, endpoint, backend, destination.post, modifier,
                onBack = { back(); refresh++ }, onIdentity = { openProfile(it) },
                onPost = { navigation.add(BrowseDestination.Conversation(it)) }, social = social, quotes = quotes, onQuote = { navigation.add(BrowseDestination.Quote(it)) })
            return
        }
        is BrowseDestination.Quote -> {
            if (wallet != null) {
                PostComposerScreen(wallet, endpoint, backend, modifier, quote = destination.post) { back(); refresh++ }
                return
            }
        }
        null -> Unit
    }
    Column(modifier) {
        if (queueWarning) Text(stringResource(R.string.post_queue_storage_error))
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
        Row {
            TextButton(enabled = wallet != null, onClick = { composing = true }) { Text(stringResource(R.string.post_new)) }
            TextButton(enabled = wallet != null, onClick = { wallet?.let { openProfile(it.publicKey) } }) {
                Text(stringResource(R.string.profile_own))
            }
            TextButton(enabled = wallet != null, onClick = { navigation.add(BrowseDestination.Search()) }) {
                Text(stringResource(R.string.search_title))
            }
        }
        TextButton(enabled = wallet != null, onClick = { reviewingQueue = true }) { Text(stringResource(R.string.post_queue_title)) }
        TextButton(enabled = wallet != null, onClick = { navigation.add(BrowseDestination.Notifications) }) {
            Text(stringResource(R.string.notifications_title))
        }
        TextButton(enabled = wallet != null, onClick = { reviewingChanges = true }) { Text(stringResource(R.string.author_saved_changes)) }
        val visiblePosts = posts.filter { post -> personal?.hidesPosts(post.author) == false &&
            (!followingView || post.author in following?.accounts.orEmpty()) }
        PersonalControlsStatus(personal)
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
        if (cacheReady && personal?.ready == true) LazyColumn(Modifier.fillMaxSize(), state = position) {
            items(visiblePosts, key = { it.id }) { post ->
                Column(Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 12.dp)) {
                    Text(registeredIdentity(post.author).name, Modifier.clickable { openProfile(post.author) }, style = MaterialTheme.typography.titleMedium)
                    Text(post.author, Modifier.clickable { openProfile(post.author) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    Text(post.text, Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH)
                        .format(Date(post.createdAt)), style = MaterialTheme.typography.labelSmall)
                    PostImages(post.images, backend)
                    PostVideo(post, backend)
                    QuoteContent(post, quotes, backend)
                    SocialActions(post, social) { navigation.add(BrowseDestination.Quote(it)) }
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

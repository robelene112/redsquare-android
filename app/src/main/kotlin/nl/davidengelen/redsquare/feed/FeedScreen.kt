package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.registry.LocalRegistryAuthority
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import nl.davidengelen.redsquare.notifications.SocialPush
import nl.davidengelen.redsquare.notifications.NotificationRoutes
import nl.davidengelen.redsquare.ui.AppNavigation
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.PrimaryDestination
import nl.davidengelen.redsquare.ui.FeedMoreActions
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FeedScreen(wallet: SaitoWallet?, walletUnavailable: Boolean, endpoint: String, backend: SaitoBackend,
    lifecycle: Lifecycle, modifier: Modifier, canReportDrawn: Boolean,
    notificationRoutes: NotificationRoutes? = null, allowPushRoute: Boolean = true,
    onHomeVisibility: (Boolean) -> Unit = {},
    onWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit,
    onDrawn: () -> Unit) {
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
    var reviewingChat by rememberSaveable { mutableStateOf(false) }
    // Wallet loading is transient. Keep the bound reference until identity can be checked.
    var chatAlertUri by rememberSaveable { mutableStateOf<String?>(null) }
    val restoredChatAlert = nl.davidengelen.redsquare.chat.ChatAlertReference.parse(chatAlertUri?.let(android.net.Uri::parse))
    val chatAlert = restoredChatAlert?.takeIf { it.owner == wallet?.publicKey && it.backend == backend }
    var chatAlertGeneration by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(wallet?.publicKey, backend, chatAlertUri) {
        if (wallet != null && chatAlertUri != null && chatAlert == null) chatAlertUri = null
    }
    val navigationMemory = remember(activity, wallet?.publicKey, backend) {
        ViewModelProvider(activity)["browse:${wallet?.publicKey.orEmpty()}:${backend.id}", FeedNavigationMemory::class.java]
    }
    val navigation = navigationMemory.destinations
    var queueWarning by remember { mutableStateOf(false) }
    LaunchedEffect(wallet, backend, endpoint) {
        if (wallet == null) return@LaunchedEffect
        try { withContext(Dispatchers.IO) { PostQueueScheduling.enqueue(context, wallet.publicKey, backend, endpoint)
            nl.davidengelen.redsquare.chat.ChatQueueScheduling.enqueue(context,wallet,backend,endpoint) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { queueWarning = true }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                queueWarning = try {
                    withContext(Dispatchers.IO) { resumeQueuedPosts(context, wallet, backend, endpoint)
                        nl.davidengelen.redsquare.chat.resumeQueuedChats(context,wallet,backend,endpoint) }
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
    val formNavigation = requireNotNull(LocalPostNavigation.current)
    val registrar = LocalRegistryAuthority.current
    val pendingTap by notificationRoutes?.pending?.collectAsState() ?: remember { mutableStateOf<android.os.Bundle?>(null) }
    var tapRetry by remember { mutableIntStateOf(0) }
    var tapFailed by remember(pendingTap) { mutableStateOf(false) }
    LaunchedEffect(pendingTap, wallet?.publicKey, personal?.ready, personal?.saving, allowPushRoute,
        formNavigation.saveCurrent, registrar, tapRetry) {
        val delivery = pendingTap ?: return@LaunchedEffect
        val routes = notificationRoutes ?: return@LaunchedEffect
        val currentWallet = wallet ?: return@LaunchedEffect
        if (!allowPushRoute || personal?.ready != true || personal.saving) return@LaunchedEffect
        val chatReference = NotificationRoutes.chatReference(delivery)
        if (chatReference != null) {
            tapFailed = false
            try {
                if (chatReference.owner != currentWallet.publicKey || chatReference.backend != backend) {
                    chatRouteObservation("binding refused")
                    tapFailed = true
                    return@LaunchedEffect
                }
                // A restarted effect can arrive before the cancelled attempt's finally releases navigation.
                snapshotFlow { formNavigation.transitioning }.first { !it }
                currentCoroutineContext().ensureActive()
                if (!routes.current(delivery)) return@LaunchedEffect
                chatRouteObservation("entry busy=" + formNavigation.transitioning +
                    " saver=" + (formNavigation.saveCurrent?.javaClass?.name ?: "none") +
                    " guarded=" + (formNavigation.guardedCurrent != null))
                val opened = formNavigation.navigate {
                    val permitted = withContext(Dispatchers.IO) { chatReference.canOpen(context) }
                    currentCoroutineContext().ensureActive()
                    if (!permitted || !routes.current(delivery) || !allowPushRoute || personal.saving) {
                        chatRouteObservation(if (!permitted) "retained original or policy refused"
                            else if (!routes.current(delivery)) "delivery superseded" else "presentation busy")
                        false
                    } else {
                        composing = false; reviewingQueue = false; reviewingChanges = false
                        chatAlertUri = chatReference.uri().toString()
                        chatAlertGeneration = delivery.getLong("generation")
                        reviewingChat = true
                        routes.consume(delivery)
                        true
                    }
                }
                chatRouteObservation(if (opened) "destination opened" else "navigation refused")
                tapFailed = !opened
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                chatRouteObservation("exception " + failure.javaClass.simpleName)
                tapFailed = true
            }
            return@LaunchedEffect
        }
        val reference = NotificationRoutes.socialReference(delivery) ?: return@LaunchedEffect
        tapFailed = false
        try {
            if (reference.owner != currentWallet.publicKey || reference.backend != backend) {
                tapFailed = true
                return@LaunchedEffect
            }
            val target = SocialPush.resolveTap(context, reference, endpoint,
                fixtureRegistrar = registrar != SaitoRegistry.REGISTRAR)
            if (target == null || !routes.current(delivery)) { tapFailed = true; return@LaunchedEffect }
            val opened = formNavigation.navigate {
                currentCoroutineContext().ensureActive()
                if (!routes.current(delivery) || !allowPushRoute || personal.saving || personal.hidesPosts(target.author)) false
                else {
                    val permitted = withContext(Dispatchers.IO) { SocialPush.canOpen(context, reference, target) }
                    currentCoroutineContext().ensureActive()
                    if (!permitted || !routes.current(delivery) || personal.saving || personal.hidesPosts(target.author)) false
                    else {
                        composing = false; reviewingChat = false; chatAlertUri = null; reviewingQueue = false; reviewingChanges = false
                        navigation.add(BrowseDestination.Conversation(target))
                        routes.consume(delivery)
                        true
                    }
                }
            }
            tapFailed = !opened
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { tapFailed = true }
    }
    if (pendingTap != null && tapFailed) {
        Column {
            Text(stringResource(R.string.social_push_open_failed))
            Row {
                TextButton(enabled = !formNavigation.transitioning, onClick = { tapRetry++ }) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(enabled = !formNavigation.transitioning, onClick = { pendingTap?.let { notificationRoutes?.consume(it) } }) {
                    Text(stringResource(R.string.social_push_dismiss))
                }
            }
        }
    }

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
    val home = !reviewingChat && !reviewingQueue && !reviewingChanges && !composing && navigation.isEmpty()
    val primary = when {
        reviewingChat -> PrimaryDestination.CHAT
        navigation.firstOrNull() is BrowseDestination.Search -> PrimaryDestination.SEARCH
        navigation.firstOrNull() is BrowseDestination.Notifications -> PrimaryDestination.NOTIFICATIONS
        (navigation.firstOrNull() as? BrowseDestination.Profile)?.author == wallet?.publicKey && wallet != null -> PrimaryDestination.PROFILE
        else -> PrimaryDestination.HOME
    }
    val currentWalletKey by rememberUpdatedState(wallet?.publicKey)
    SideEffect { onHomeVisibility(home) }
    val content: @Composable () -> Unit = content@{
        val childModifier = Modifier.fillMaxSize()
        if (reviewingChat && wallet != null) {
            CompositionLocalProvider(nl.davidengelen.redsquare.media.LocalGiphyConfiguration provides
                nl.davidengelen.redsquare.media.LocalChatGiphyConfiguration.current) {
                key(chatAlertGeneration) {
                    nl.davidengelen.redsquare.chat.ChatScreen(wallet, endpoint, backend, childModifier, onWalletMetadata,
                        initialContact = chatAlert?.privateContact, initialGroup = chatAlert?.groupId,
                        onInitialDestinationClosed = { chatAlertUri = null }) { reviewingChat = false; chatAlertUri = null }
                }
            }
            return@content
        }
        if (reviewingQueue && wallet != null) {
            QueuedPostsScreen(wallet.publicKey, backend, childModifier) { reviewingQueue = false }
            return@content
        }
        if (reviewingChanges && wallet != null) {
            SavedAuthorActionsScreen(wallet, endpoint, backend, childModifier) { reviewingChanges = false; refresh++ }
            return@content
        }
        if (composing && wallet != null) {
            PostComposerScreen(wallet, endpoint, backend, childModifier) { composing = false; refresh++ }
            return@content
        }
        when (val destination = navigation.lastOrNull()) {
            is BrowseDestination.Notifications -> {
                NotificationsScreen(wallet, endpoint, backend, posts, following?.accounts.orEmpty(), childModifier, onBack = { back() },
                    onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                    onIdentity = { openProfile(it) },
                    onReference = { navigation.add(BrowseDestination.Reference(it)) })
                return@content
            }
            is BrowseDestination.Search -> {
                SearchScreen(wallet, endpoint, backend, destination.view, childModifier,
                    knownAccounts = following?.accounts.orEmpty() + navigationMemory.visitedProfiles,
                    onBack = { back() }, onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                    onIdentity = { openProfile(it) }, social = social, quotes = quotes,
                    onQuote = { navigation.add(BrowseDestination.Quote(it)) })
                return@content
            }
            is BrowseDestination.Profile -> {
                ProfileScreen(wallet, endpoint, backend, destination.author, destination.view, following, childModifier,
                    onBack = { back() }, onPost = { navigation.add(BrowseDestination.Conversation(it)) },
                    onIdentity = { openProfile(it) }, social = social, quotes = quotes, onQuote = { navigation.add(BrowseDestination.Quote(it)) })
                return@content
            }
            is BrowseDestination.Reference -> {
                NotificationReferenceScreen(wallet, endpoint, backend, destination.target, quotes, childModifier,
                    onBack = { back() }, onIdentity = { openProfile(it) },
                    onPost = { navigation.add(BrowseDestination.Conversation(it)) }, social = social,
                    onQuote = { navigation.add(BrowseDestination.Quote(it)) })
                return@content
            }
            is BrowseDestination.Conversation -> {
                ConversationScreen(wallet, endpoint, backend, destination.post, childModifier,
                    onBack = { back(); refresh++ }, onIdentity = { openProfile(it) },
                    onPost = { navigation.add(BrowseDestination.Conversation(it)) }, social = social, quotes = quotes, onQuote = { navigation.add(BrowseDestination.Quote(it)) })
                return@content
            }
            is BrowseDestination.Quote -> {
                if (wallet != null) {
                    PostComposerScreen(wallet, endpoint, backend, childModifier, quote = destination.post) { back(); refresh++ }
                    return@content
                }
            }
            null -> Unit
        }
        Column(childModifier) {
            if (queueWarning) Text(stringResource(R.string.post_queue_storage_error))
            val noInternet = !internet.available || (failed && !internet.validated)
            when {
                noInternet -> ConnectionBanner(stringResource(R.string.no_internet), false)
                failed -> ConnectionBanner(stringResource(R.string.server_unavailable), false)
                recovered -> ConnectionBanner(stringResource(R.string.back_online), true)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FeedTab(R.string.feed_title, !followingView) { followingView = false }
                FeedTab(R.string.following_title, followingView) { followingView = true }
                TextButton(onClick = { refresh++ }, enabled = wallet != null && !loading && internet.available) {
                    Text(stringResource(R.string.feed_refresh))
                }
                FeedMoreActions(wallet != null, onQueue = { reviewingQueue = true }, onChanges = { reviewingChanges = true })
            }
            val visiblePosts = posts.filter { post -> personal?.hidesPosts(post.author) == false &&
                (!followingView || post.author in following?.accounts.orEmpty()) }
            val visiblePostIds = visiblePosts.map { it.id }.toSet()
            val feedInteractive = canReportDrawn && LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(feedInteractive, cacheReady, personal?.ready, visiblePostIds, position) {
                if (feedInteractive && cacheReady && personal?.ready == true && visiblePostIds.isNotEmpty()) {
                    snapshotFlow {
                        val layout = position.layoutInfo
                        layout.visibleItemsInfo.any { item -> item.key in visiblePostIds &&
                            minOf(item.offset + item.size, layout.viewportEndOffset) > maxOf(item.offset, layout.viewportStartOffset) }
                    }.first { it }
                    withFrameNanos { }; withFrameNanos { }; onDrawn()
                }
            }
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
            if (cacheReady && personal?.ready == true) LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = position,
                contentPadding = PaddingValues(bottom = 88.dp)) {
                items(visiblePosts, key = { it.id }) { post ->
                    Column(Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GeneratedProfilePicture(post.author, Modifier.size(48.dp).clip(CircleShape).clickable { openProfile(post.author) })
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(registeredIdentity(post.author).name, Modifier.weight(1f).clickable { openProfile(post.author) },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH)
                                        .format(Date(post.createdAt)), Modifier.padding(start = 8.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(post.author, Modifier.clickable { openProfile(post.author) }, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(post.text, Modifier.clickable { navigation.add(BrowseDestination.Conversation(post)) }.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge)
                        PostImages(post.images, backend)
                        PostVideo(post, backend)
                        QuoteContent(post, quotes, backend)
                            }
                        }
                        SocialActions(post, social) { navigation.add(BrowseDestination.Quote(it)) }
                    }
                    HorizontalDivider()
                }
            }
            else Spacer(Modifier.weight(1f))
        }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
            content()
            if (home && wallet != null) {
                val composeLabel = stringResource(R.string.post_new)
                FloatingActionButton(onClick = { composing = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = AppSpacing.screen)
                        .semantics { text = AnnotatedString(composeLabel) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(painterResource(R.drawable.ic_compose), contentDescription = null)
                }
            }
        }
        if (!WindowInsets.isImeVisible && !composing && !reviewingQueue && !reviewingChanges &&
            navigation.lastOrNull() !is BrowseDestination.Quote) {
            HorizontalDivider()
            AppNavigation(primary, wallet != null && !formNavigation.transitioning, onSelect = select@{ destination ->
                if (destination == primary && (reviewingChat || home || navigation.size == 1)) return@select
                val owner = wallet?.publicKey ?: return@select
                scope.launch {
                    formNavigation.navigate {
                        if (owner != currentWalletKey) return@navigate false
                        reviewingChat = false; reviewingQueue = false; reviewingChanges = false
                        chatAlertUri = null
                        navigation.clear()
                        when (destination) {
                            PrimaryDestination.HOME -> Unit
                            PrimaryDestination.SEARCH -> navigation.add(BrowseDestination.Search(navigationMemory.searchView))
                            PrimaryDestination.NOTIFICATIONS -> navigation.add(BrowseDestination.Notifications)
                            PrimaryDestination.CHAT -> reviewingChat = true
                            PrimaryDestination.PROFILE -> navigation.add(BrowseDestination.Profile(owner, navigationMemory.ownProfileView))
                        }
                        true
                    }
                }
            })
        }
    }
}

@Composable
private fun RowScope.FeedTab(label: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f)) {
        Tab(selected = selected, onClick = onClick, text = { Text(stringResource(label), maxLines = 1) })
        Box(Modifier.fillMaxWidth().height(2.dp).background(
            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
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

/** Debug lifecycle diagnosis contains no identity, message, URI or exception content. */
private fun chatRouteObservation(result: String) {
    if (nl.davidengelen.redsquare.BuildConfig.DEBUG) android.util.Log.d("ChatAlertRoute", result)
}

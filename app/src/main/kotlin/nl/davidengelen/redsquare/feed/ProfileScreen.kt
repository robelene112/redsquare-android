package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.AccountControls
import nl.davidengelen.redsquare.policy.PublisherContactPurpose
import nl.davidengelen.redsquare.policy.PublisherEmailDialog
import nl.davidengelen.redsquare.policy.PublisherUserReport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*

internal enum class ProfileTab(val label: Int) {
    POSTS(R.string.profile_posts), REPLIES(R.string.profile_replies), LIKES(R.string.profile_likes)
}

internal class ProfileViewState {
    var tab by mutableStateOf(ProfileTab.POSTS)
    var editing by mutableStateOf(false)
    var history = AndroidProfileCache.SavedProfile(emptyList(), emptyList(), false)
    var publisherReport by mutableStateOf<PublisherUserReport?>(null)
}

private data class ProfileHistoryPage(val posts: List<FeedPost>, val likes: List<ProfileLike>, val unreadable: Boolean,
    val oldestCreatedAt: Long?, val rowCount: Int)

@Composable
internal fun ProfileScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, author: String,
    view: ProfileViewState, following: FollowingState?, modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit,
    onIdentity: (String) -> Unit, social: SocialState? = null, quotes: QuoteState? = null, onQuote: ((FeedPost) -> Unit)? = null, authorActions: PostAuthorActions? = null) {
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val cache = remember(backend) { AndroidProfileCache(context, backend) }
    val detailsCache = remember(backend, author) { AndroidProfileDetailsCache(context, backend, author) }
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
    val tab = view.tab
    var posts by remember(author, tab) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember(author, tab) { mutableStateOf(true) }
    var failed by remember(author, tab) { mutableStateOf(false) }
    var unreadable by remember(author, tab) { mutableStateOf(false) }
    var unavailableLikes by remember(author, tab) { mutableStateOf(false) }
    var cacheWarning by remember(author, tab) { mutableStateOf(false) }
    var refresh by remember(author) { mutableIntStateOf(0) }
    var requestedBefore by remember(author, tab) { mutableLongStateOf(System.currentTimeMillis() + 1) }
    var nextBefore by remember(author, tab) { mutableStateOf<Long?>(null) }
    var pagingUnavailable by remember(author, tab) { mutableStateOf(false) }
    var profile by remember(author, backend) { mutableStateOf<SaitoProfile?>(null) }
    var profileLoading by remember(author, backend) { mutableStateOf(false) }
    var profileFailed by remember(author, backend) { mutableStateOf(false) }
    var profileCacheWarning by remember(author, backend) { mutableStateOf(false) }
    var profileUnreadable by remember(author, backend) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, author, refresh) {
        if (wallet == null) return@LaunchedEffect
        profileLoading = true
        try {
            val cached = withContext(Dispatchers.IO) { detailsCache.load() }
            val known = withContext(Dispatchers.IO) {
                profile?.let { SaitoProfiles.merge(cached.profile, it) } ?: cached.profile
            }
            profile = known
            profileCacheWarning = cached.unreadable
            val observed = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val request = JsonObject().apply {
                        addProperty("request", "archive")
                        add("data", JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", "Profile")
                            addProperty("field2", author); addProperty("limit", 100)
                        })
                    }
                    SaitoProfiles.readArchive(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                        .toString(Charsets.UTF_8), author)
                }
            }
            val merged = withContext(Dispatchers.IO) { SaitoProfiles.merge(known, observed) }
            // Display verified data even when local persistence fails.
            profile = merged
            profileUnreadable = observed.ignored > 0
            profileFailed = false
            try { profile = withContext(Dispatchers.IO) { detailsCache.save(merged) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { profileCacheWarning = true }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            profileFailed = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { profileFailed = true }
        finally { profileLoading = false }
    }
    LaunchedEffect(wallet, endpoint, author, tab, refresh) {
        if (wallet == null) { loading = false; return@LaunchedEffect }
        loading = true
        val pageCursor = requestedBefore
        fun show(snapshot: AndroidProfileCache.SavedProfile) {
            val likedTargets = snapshot.likes.filter { it.author == author }.map { it.target }.toSet()
            posts = snapshot.posts.filter { post -> when (tab) {
                ProfileTab.POSTS -> post.author == author && post.parentId.isEmpty()
                ProfileTab.REPLIES -> post.author == author && post.parentId.isNotEmpty()
                ProfileTab.LIKES -> post.id in likedTargets
            } }.sortedByDescending { it.createdAt }
            unavailableLikes = tab == ProfileTab.LIKES && likedTargets.any { target -> snapshot.posts.none { it.id == target } }
        }
        val cached = withContext(Dispatchers.IO) { cache.load() }
        val retained = withContext(Dispatchers.IO) { revisions.apply(RedSquareFeed.newest(view.history.posts + cached.posts)) }
        val saved = AndroidProfileCache.SavedProfile(retained.posts,
            (view.history.likes + cached.likes).distinctBy { it.id }, cached.unreadable || retained.unreadable)
        show(saved)
        cacheWarning = saved.unreadable
        suspend fun publish(freshPosts: List<FeedPost>, freshLikes: List<ProfileLike>) {
            social?.observe(freshPosts)
            val applied = withContext(Dispatchers.IO) {
                freshPosts.forEach(revisions::observe)
                revisions.apply(RedSquareFeed.newest(freshPosts + view.history.posts + saved.posts))
            }
            val merged = AndroidProfileCache.SavedProfile(applied.posts,
                (freshLikes + view.history.likes + saved.likes).distinctBy { it.id }, applied.unreadable)
            view.history = merged
            show(merged)
            cacheWarning = withContext(Dispatchers.IO) {
                try { cache.save(merged.posts, merged.likes); applied.unreadable } catch (_: Exception) { true }
            }
        }
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
                        addProperty("created_earlier_than", pageCursor)
                        addProperty("limit", 20)
                        if (tab != ProfileTab.LIKES) addProperty("flagged_ne", 1)
                    }
                    if (tab != ProfileTab.LIKES) {
                        val page = RedSquareFeed.readAuthor(archive(criteria), author)
                        revisions.reconcileDeletions(peer, page.deletions, page.posts + saved.posts)
                        if (tab == ProfileTab.POSTS) {
                            withContext(Dispatchers.Main.immediate) { publish(page.posts, emptyList()) }
                            val quotesCriteria = criteria.deepCopy().apply { addProperty("field1", "RedSquareRetweet") }
                            val quotePage = RedSquareFeed.readCurrentQuotes(archive(quotesCriteria))
                            val quotePosts = quotePage.posts.filter { it.author == author }
                            // A sparse stream must not advance past unread rows in a fuller stream.
                            val pages = listOf(page, quotePage)
                            val cursor = pages.filter { it.rowCount >= 20 }.mapNotNull { it.oldestCreatedAt }.maxOrNull()
                            ProfileHistoryPage(page.posts + quotePosts, emptyList(), page.ignored > 0 || quotePage.ignored > 0,
                                cursor, maxOf(page.rowCount, quotePage.rowCount))
                        } else ProfileHistoryPage(page.posts, emptyList(), page.ignored > 0, page.oldestCreatedAt, page.rowCount)
                    } else {
                        val page = RedSquareLikes.readArchive(archive(criteria), author)
                        var ignored = page.ignored > 0
                        val targets = mutableListOf<FeedPost>()
                        for (target in page.likes.map { it.target }.distinct()) {
                            val query = JsonObject().apply {
                                addProperty("field1", "RedSquare"); addProperty("sig", target); addProperty("flagged_ne", 1)
                            }
                            val targetPage = RedSquareFeed.readTarget(archive(query), target)
                            targets.addAll(targetPage.posts)
                            if (targetPage.ignored > 0) ignored = true
                        }
                        ProfileHistoryPage(targets, page.likes, ignored, page.oldestCreatedAt, page.rowCount)
                    }
                }
            }
            publish(result.posts, result.likes)
            unreadable = result.unreadable
            nextBefore = result.oldestCreatedAt?.takeIf { result.rowCount >= 20 && it < pageCursor }
            pagingUnavailable = result.rowCount >= 20 && nextBefore == null
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    if (view.editing && wallet?.publicKey == author) {
        ProfileEditorScreen(requireNotNull(wallet), backend, endpoint, profile, modifier) { view.editing = false; refresh++ }
        return
    }
    val visiblePosts = posts.filter { personal?.hidesPosts(it.author) == false }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.profile_title), Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = following?.ready == true && !following.saving,
                onClick = { following?.toggle(author) }) {
                Text(stringResource(if (author in following?.accounts.orEmpty()) R.string.unfollow_account else R.string.follow_account))
            }
            TextButton(enabled = wallet != null && !loading,
                onClick = { requestedBefore = System.currentTimeMillis() + 1; refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "profile-details") {
                Column {
                    profile?.banner?.let { source ->
                        ProfileImage(source, ProfileImageKind.BANNER, author, backend, Modifier.fillMaxWidth().aspectRatio(3f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileImage(profile?.image, ProfileImageKind.PICTURE, author, backend, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            val identity = registeredIdentity(author)
                            Text(identity.name, style = MaterialTheme.typography.titleMedium)
                            if (identity.saveError) Text(stringResource(R.string.identity_name_save_error),
                                style = MaterialTheme.typography.bodySmall)
                            Text(author, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    profile?.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (wallet?.publicKey == author) TextButton(onClick = { view.editing = true }) {
                        Text(stringResource(R.string.profile_edit))
                    }
                    InlineStatus(if (profileLoading) stringResource(R.string.profile_details_loading) else null, StatusTone.WORKING)
                    InlineStatus(if (profileFailed) stringResource(R.string.profile_details_error) else null, StatusTone.ERROR)
                    InlineStatus(if (profileUnreadable) stringResource(R.string.profile_unreadable) else null, StatusTone.WARNING)
                    InlineStatus(if (profileCacheWarning) stringResource(R.string.profile_details_cache_warning) else null, StatusTone.WARNING)
                    FollowingStatus(following)
                    AccountControls(author)
                    if (wallet != null && wallet.publicKey != author) TextButton(onClick = {
                        view.publisherReport = PublisherUserReport(author, backend)
                    }) { Text(stringResource(R.string.publisher_report_user_action)) }
                    Row {
                        ProfileTab.entries.forEach { item ->
                            TextButton(onClick = { view.tab = item }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(stringResource(item.label), maxLines = 1,
                                    color = if (item == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        TextButton(enabled = wallet != null && !loading && nextBefore != null,
                            onClick = { nextBefore?.let { requestedBefore = it; refresh++ } },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(stringResource(R.string.profile_load_older), maxLines = 1)
                        }
                    }
                    if (personal?.ready == true && visiblePosts.size < posts.size)
                        Text(stringResource(R.string.personal_posts_hidden), style = MaterialTheme.typography.bodySmall)
                    InlineStatus(stringResource(R.string.status_available_history),
                        details = listOf(stringResource(R.string.profile_history)), announce = false)
                    InlineStatus(if (loading) stringResource(R.string.profile_loading) else null, StatusTone.WORKING)
                    InlineStatus(if (failed) stringResource(R.string.profile_error) else null, StatusTone.ERROR)
                    InlineStatus(if (unreadable) stringResource(R.string.profile_unreadable) else null, StatusTone.WARNING)
                    InlineStatus(if (unavailableLikes) stringResource(R.string.profile_likes_unavailable) else null, StatusTone.WARNING)
                    InlineStatus(if (cacheWarning) stringResource(R.string.feed_cache_warning) else null, StatusTone.WARNING)
                    InlineStatus(if (pagingUnavailable) stringResource(R.string.profile_paging_unavailable) else null, StatusTone.WARNING)
                    if (!loading && !failed && posts.isEmpty()) Text(stringResource(R.string.profile_empty))
                }
            }
            items(visiblePosts, key = { it.id }) { post ->
                PostCard(post, backend, onIdentity, onPost, social, quotes, onQuote, authorActions)
                HorizontalDivider()
            }
        }
    }
    view.publisherReport?.let { report ->
        PublisherEmailDialog(PublisherContactPurpose.SAFETY, report) { view.publisherReport = null }
    }
}

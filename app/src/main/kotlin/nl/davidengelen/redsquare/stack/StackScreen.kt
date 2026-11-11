package nl.davidengelen.redsquare.stack

import android.content.Intent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.FollowingState
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

private enum class StackTab(override val label: Int, override val icon: Int) : ModuleDestination {
    DISCOVER(R.string.stack_discover, R.drawable.ic_globe),
    FOLLOWING(R.string.following_title, R.drawable.ic_bookmark),
    ARTICLES(R.string.stack_my_articles, R.drawable.ic_article);
    override val caption get() = label
}

private data class StackLiveRoute(val author: String? = null, val article: String? = null,
    val articleAuthor: String? = null, val latest: Boolean = false) : java.io.Serializable

/** Native Stack reading over the same identity and explicitly selected node as Red Square. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StackScreen(wallet: SaitoWallet?, backend: SaitoBackend, endpoint: String, modifier: Modifier,
    chrome: @Composable (Boolean) -> Unit, onNodeSettings: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val navigation = requireNotNull(nl.davidengelen.redsquare.feed.LocalPostNavigation.current)
    var tab by rememberSaveable { mutableStateOf(StackTab.DISCOVER) }
    var path by rememberSaveable { mutableStateOf(listOf(StackLiveRoute())) }
    val current = path.last()
    val latest = current.latest
    val author = current.author
    val articleId = current.article
    val articleAuthor = current.articleAuthor
    fun push(route: StackLiveRoute) {
        if (route != path.last()) path = if (path.size < 100) path + route
            else listOf(path.first()) + path.takeLast(98) + route
    }
    var linkFailed by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var drafts by rememberSaveable { mutableStateOf(false) }
    val activity = requireNotNull(androidx.activity.compose.LocalActivity.current) as androidx.activity.ComponentActivity
    val localDraft = remember(activity, wallet?.publicKey) { wallet?.let {
        val memory = androidx.lifecycle.ViewModelProvider(activity)["stack-draft:${it.publicKey}", StackLocalDraftMemory::class.java]
        memory.state ?: StackLocalDraft(context, it.publicKey).also { draft -> memory.state = draft }
    } }
    LaunchedEffect(localDraft) { localDraft?.load() }
    val following = remember(wallet?.publicKey) { wallet?.let { FollowingState(context, it.publicKey, scope) } }
    LaunchedEffect(following) { following?.load() }
    val personal = LocalPersonalControls.current
    val generalLibrary = remember(wallet, backend, endpoint) {
        wallet?.let { StackLibrary(context, it, backend, endpoint) }
    }
    val ownLibrary = remember(wallet, backend, endpoint) {
        wallet?.let { StackLibrary(context, it, backend, endpoint, it.publicKey) }
    }
    val authorLibrary = remember(wallet, backend, endpoint, author) {
        author?.let { key -> wallet?.let { StackLibrary(context, it, backend, endpoint, key) } }
    }
    val library = authorLibrary ?: if (tab == StackTab.ARTICLES) ownLibrary else generalLibrary
    LaunchedEffect(library, editing, reviewing, drafts) {
        if (!editing && !reviewing && !(tab == StackTab.ARTICLES && drafts)) library?.start()
    }
    fun open(url: String) {
        try { uri.openUri(url); linkFailed = false } catch (_: Exception) { linkFailed = true }
    }
    fun select(next: StackTab) {
        scope.launch { navigation.navigate {
            tab = next; path = listOf(StackLiveRoute())
            editing = false; reviewing = false
            true
        } }
    }
    if ((editing || reviewing) && localDraft != null) {
        StackLocalEditor(localDraft, reviewing, modifier, chrome,
            onBack = { if (reviewing) reviewing = false else editing = false },
            onReview = { reviewing = true }, onOpenWeb = { open("${backend.webUrl}/stack?publish=1") },
            footer = { ModuleNavigation(StackTab.entries, tab, enabled = !localDraft.saving, onSelect = ::select) })
        return
    }
    fun back() {
        if (path.size > 1) path = path.dropLast(1)
    }
    BackHandler(path.size > 1) { back() }
    val pages = rememberSaveableStateHolder()
    val selected = library?.articles?.firstOrNull { it.id == articleId && it.author == articleAuthor }
    val visible = library?.articles.orEmpty().filter {
        personal?.ready == true && !personal.policy.hidesSocial(it.author) &&
            (tab != StackTab.FOLLOWING || it.author in following?.accounts.orEmpty())
    }
    val readable = if (tab == StackTab.DISCOVER && !latest) visible.sortedByDescending {
        it.author in following?.accounts.orEmpty()
    } else visible
    val route = "${tab.name}:${author.orEmpty()}:${articleAuthor.orEmpty()}:${articleId.orEmpty()}:$latest"
    val reveal = rememberSingleOwnerRevealState(route)
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            pages.SaveableStateProvider(route) {
                val list = rememberLazyListState()
                val header = rememberReadingHeaderState(enabled = true, list = list)
                val anchor = rememberReadingAnchorModifier(list, enabled = !header.inMotion, relativeToRoot = true)
                Column(Modifier.fillMaxSize().nestedScroll(header.connection).readingNavigation(header)) {
                    ReadingHeader(header) { chrome(false) }
                    SingleOwnerReveal(route, Modifier.weight(1f), state = reveal) {
                    Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (path.size > 1) FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back)) { back() }
                        Text(if (articleId != null) stringResource(R.string.stack_live_article)
                            else if (author != null) registeredIdentity(author).name
                            else stringResource(if (latest) R.string.stack_latest else tab.label),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        if (selected != null && articleId != null) FrameIconButton(R.drawable.ic_share,
                            stringResource(R.string.stack_share_article)) {
                            try {
                                val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TITLE, selected.title)
                                    .putExtra(Intent.EXTRA_TEXT, SaitoStack.articleUrl(selected, backend))
                                context.startActivity(Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) { linkFailed = true }
                        }
                    }
                    PullToRefreshBox(library?.loading == true, { scope.launch { library?.load() } }, Modifier.weight(1f)) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("stack-live-content").then(anchor),
                            contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                InlineStatus(if (linkFailed) stringResource(R.string.stack_link_failed) else null, StatusTone.ERROR)
                                InlineStatus(if (library?.failed == true) stringResource(R.string.stack_load_failed) else null,
                                    StatusTone.ERROR, actionLabel = stringResource(R.string.retry), onAction = { scope.launch { library?.load() } })
                                InlineStatus(if (library?.cacheWarning == true) stringResource(R.string.stack_cache_warning) else null, StatusTone.NEUTRAL, announce = false)
                                nl.davidengelen.redsquare.moderation.PersonalControlsStatus(personal)
                            }
                            if (articleId != null) {
                                if (selected != null && personal?.ready == true && !personal.policy.hidesSocial(selected.author)) {
                                    item { Text(selected.title, style = MaterialTheme.typography.headlineLarge) }
                                    if (selected.excerpt.isNotBlank()) item { Text(selected.excerpt, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    item {
                                        val name = registeredIdentity(selected.author).name
                                        TextButton(onClick = { push(StackLiveRoute(author = selected.author, latest = latest)) }) { Text(name) }
                                        Text(stackArticleDate(selected), style = MaterialTheme.typography.labelSmall)
                                    }
                                    selected.cover?.let { source -> item {
                                        nl.davidengelen.redsquare.feed.PostImage(source, backend, null, Modifier.fillMaxWidth().height(220.dp))
                                    } }
                                    item { LiveStackMarkdown(selected, backend) }
                                    item { StackBlockButton({ open(SaitoStack.articleUrl(selected, backend)) }, Modifier.fillMaxWidth(), outlined = true) {
                                        Text(stringResource(R.string.stack_open_original))
                                    } }
                                } else if (personal?.ready == true) item {
                                    if (library?.cacheReady != true || library.loading) InlineStatus(stringResource(R.string.stack_loading), StatusTone.WORKING)
                                    else Text(stringResource(R.string.stack_article_unavailable))
                                }
                            } else {
                                if (author == null && tab == StackTab.ARTICLES) item {
                                    DesignTabs(listOf(stringResource(R.string.stack_published), stringResource(R.string.stack_drafts)),
                                        if (drafts) 1 else 0, { drafts = it == 1 })
                                }
                                if (author == null && tab == StackTab.ARTICLES && drafts) {
                                    item {
                                        if (localDraft?.error == true && !localDraft.ready) InlineStatus(stringResource(R.string.stack_draft_load_failed), StatusTone.ERROR,
                                            actionLabel = stringResource(R.string.retry), onAction = { scope.launch { localDraft.load() } })
                                        else if (localDraft?.ready != true) Text(stringResource(R.string.stack_draft_loading))
                                        else if (!localDraft.hasWork) Text(stringResource(R.string.stack_draft_empty))
                                        else StackMenuRow(localDraft.draft.title.ifBlank { stringResource(R.string.stack_untitled_draft) },
                                            stringResource(R.string.stack_local_saved), R.drawable.ic_compose) { editing = true }
                                    }
                                    item { StackBlockButton({ editing = true }) { Text(stringResource(R.string.stack_write)) } }
                                } else {
                                if (author == null && tab == StackTab.DISCOVER) {
                                    if (!latest) item { StackIntro(stringResource(R.string.stack_hero), stringResource(R.string.stack_hero_body), stringResource(R.string.stack_eyebrow)) }
                                    item { DesignTabs(listOf(stringResource(R.string.stack_for_you), stringResource(R.string.stack_latest)),
                                        if (latest) 1 else 0, {
                                            if (it == 1) push(current.copy(latest = true))
                                            else if (latest && path.size > 1) path = path.dropLast(1)
                                            scope.launch { library?.load() }
                                        }) }
                                }
                                author?.let { key -> item {
                                    nl.davidengelen.redsquare.feed.ProfileAvatar(key,
                                        Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape), cachedOnly = true)
                                    Text(registeredIdentity(key).name, style = MaterialTheme.typography.headlineMedium)
                                    Text(key, style = MaterialTheme.typography.bodySmall)
                                    if (key != wallet?.publicKey) OutlinedButton(enabled = following?.ready == true && following?.saving != true,
                                        onClick = { following?.toggle(key) }) {
                                        Text(stringResource(if (key in following?.accounts.orEmpty()) R.string.following_title else R.string.follow_account))
                                    }
                                } }
                                if (following?.error == true && (author != null || tab == StackTab.FOLLOWING)) item {
                                    InlineStatus(stringResource(R.string.stack_follow_failed), StatusTone.ERROR,
                                        actionLabel = stringResource(R.string.retry), onAction = { following.retry() })
                                }
                                if (library?.loading == true && readable.isEmpty()) item { InlineStatus(stringResource(R.string.stack_loading), StatusTone.WORKING) }
                                if (library != null && library.successfulRead && !library.failed && !library.loading &&
                                    personal?.ready == true && (tab != StackTab.FOLLOWING || following?.ready == true) && readable.isEmpty()) item {
                                    Text(stringResource(when {
                                        tab == StackTab.FOLLOWING -> R.string.stack_follow_empty
                                        tab == StackTab.ARTICLES -> R.string.stack_owned_empty
                                        else -> R.string.stack_empty
                                    }))
                                    TextButton(onClick = onNodeSettings) { Text(stringResource(R.string.stack_choose_node)) }
                                }
                                items(readable, key = { "${it.author}:${it.id}" }) { article ->
                                    LiveStackCard(article, backend) { push(StackLiveRoute(author, article.id, article.author, latest)) }
                                }
                                if (library?.more == true) item {
                                    StackBlockButton({ scope.launch { library.load(reset = false) } }, Modifier.fillMaxWidth(),
                                        outlined = true, enabled = !library.loading) { Text(stringResource(R.string.stack_load_more)) }
                                }
                                item { TextButton(onClick = { open("${backend.webUrl}/stack") }) {
                                    Text(stringResource(R.string.stack_open_web))
                                } }
                                }
                            }
                        }
                    }
                    }
                    }
                }
            }
            if (articleId == null) FloatingActionButton(onClick = { editing = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(AppSpacing.screen),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Icon(painterResource(R.drawable.ic_compose), stringResource(R.string.stack_write_article_live))
            }
        }
        ModuleNavigation(StackTab.entries, tab, enabled = true, onSelect = ::select)
    }
}

package nl.davidengelen.redsquare.spaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.StackLink
import nl.davidengelen.redsquare.stack.LiveStackCard
import nl.davidengelen.redsquare.stack.LiveStackMarkdown
import nl.davidengelen.redsquare.stack.StackDraft
import nl.davidengelen.redsquare.stack.StackEditorForm
import nl.davidengelen.redsquare.stack.StackMarkdown
import nl.davidengelen.redsquare.stack.StackVisualBuffer
import nl.davidengelen.redsquare.stack.stackArticleDate
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import java.text.DateFormat
import java.util.Date

private enum class ArticlePage { LIST, READER, EDITOR, REVIEW, SHARE }

@Composable
internal fun SpaceArticleChannel(account: SpacesAccount, state: SpacesAccount.State, channel: SpaceClient.Channel,
    modifier: Modifier, onEditorGuard: ((() -> Boolean)?) -> Unit) {
    val target = SpacesAccount.Target(requireNotNull(state.selected), channel.id)
    val scope = rememberCoroutineScope()
    val publishing = LocalPublishingAgreement.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var page by rememberSaveable(target.space, target.channel) { mutableStateOf(ArticlePage.LIST) }
    var selected by rememberSaveable(target.space, target.channel) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(target.space, target.channel) { mutableStateOf<String?>(null) }
    var fromCache by rememberSaveable(target.space, target.channel) { mutableStateOf(false) }
    var draft by remember(target) { mutableStateOf(StackDraft("", "", "")) }
    var draftReady by remember(target) { mutableStateOf(false) }
    var draftFailed by remember(target) { mutableStateOf(false) }
    var retryDraft by remember(target) { mutableIntStateOf(0) }
    val visual = remember(target, page == ArticlePage.EDITOR) { StackVisualBuffer() }
    var tooLong by remember(target) { mutableStateOf(false) }
    var linkText by rememberSaveable(target.space, target.channel) { mutableStateOf("") }
    var stackReference by remember(target) { mutableStateOf<StackLink?>(null) }
    val listedRecord = state.messages.firstOrNull { it.isArticle && it.id == selected }
    val cachedLookup by produceState<Pair<String, SpaceClient.Message?>?>(null, target, selected, listedRecord, state.refreshing, state.working) {
        value = if (listedRecord == null) selected?.let { id ->
            id to try { account.readArticle(target, id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } else null
    }
    val readerRecord = listedRecord ?: cachedLookup?.takeIf { it.first == selected }?.second
    val readerDocument by produceState<Pair<SpaceClient.Message, SpaceArticleContent?>?>(null, target, readerRecord) {
        value = withContext(Dispatchers.Default) {
            readerRecord?.let { record ->
                record to record.text?.let { runCatching { SpaceArticleContent.decode(it) }.getOrNull() }
            }
        }
    }
    var copied by remember(target, selected) { mutableStateOf(false) }
    var copyFailed by remember(target, selected) { mutableStateOf(false) }
    var confirmRemoval by remember(target, selected) { mutableStateOf(false) }
    LaunchedEffect(state.articleFocus) {
        state.articleFocus?.let { requested ->
            selected = requested.id
            editing = null
            fromCache = requested.fromCache
            page = ArticlePage.READER
            account.consumeArticleFocus(requested)
        }
    }
    val articles by produceState(emptyMap<SpaceClient.Message, SpaceArticleContent?>(), target, state.messages) {
        value = withContext(Dispatchers.Default) {
            state.messages.mapNotNull { record ->
                if (!record.isArticle) return@mapNotNull null
                val content = record.text?.let { runCatching { SpaceArticleContent.decode(it) }.getOrNull() }
                record to content
            }.toMap()
        }
    }
    val records = remember(state.messages) { state.messages.filter { it.isArticle }.asReversed() }
    val savedAnchor = state.anchor
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = records.indexOfFirst { it.id == savedAnchor?.messageId }.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = savedAnchor?.takeIf { anchor -> records.any { it.id == anchor.messageId } }?.offset ?: 0,
    )
    val currentRecords by rememberUpdatedState(records)
    val windowFrom by rememberUpdatedState(state.windowFrom)
    LaunchedEffect(target, page, list) {
        if (page != ArticlePage.LIST) return@LaunchedEffect
        var scrolled = false
        snapshotFlow { Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .collect { (scrolling, index, offset) ->
                if (scrolling) scrolled = true
                else if (scrolled) {
                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val record = currentRecords.firstOrNull { it.id == item?.key }
                    val anchor = record?.position?.takeIf { it >= windowFrom }
                        ?.let { SpaceClient.ReadingAnchor(record.id, it, offset, windowFrom) }
                    account.readingPosition(target.space, target.channel, !list.canScrollBackward, anchor)
                    scrolled = false
                }
            }
    }
    LaunchedEffect(target, page, state.scrollToMessage, records) {
        val requested = state.scrollToMessage ?: return@LaunchedEffect
        if (page != ArticlePage.LIST) return@LaunchedEffect
        val index = records.indexOfFirst { it.id == requested.id }
        if (index >= 0) list.requestScrollToItem(index)
        account.consumeScrollTarget(requested)
    }
    val canWrite = state.current?.canSend == true && !state.loadingChannel && !state.working && !state.savingNavigation
    fun save(value: StackDraft): Boolean {
        val saved = value.asArticleDraft()
        tooLong = !saved.fitsStorage()
        if (!tooLong) account.editArticleDraft(target, editing, saved)
        draft = value
        return !tooLong
    }
    fun returnToChannel() {
        if (page == ArticlePage.EDITOR && draftReady && !save(visual.snapshot(draft))) return
        focus.clearFocus(); keyboard?.hide()
        page = if (editing == null) ArticlePage.LIST else ArticlePage.READER
        if (editing == null) {
            selected = null
            fromCache = false
        }
        editing = null
    }
    BackHandler(page != ArticlePage.LIST && !state.working) {
        if (page == ArticlePage.REVIEW) page = ArticlePage.EDITOR else returnToChannel()
    }
    val inEditor = page == ArticlePage.EDITOR || page == ArticlePage.REVIEW
    LaunchedEffect(target, inEditor, editing, retryDraft) {
        if (!inEditor) return@LaunchedEffect
        draftFailed = false
        try {
            val saved = account.articleDraft(target, editing)
            val original = if (saved == null) editing?.let { account.readArticle(target, it) } else null
            val prior = original?.change?.takeIf { it.rejected || it.cancelled }?.text ?: original?.text
            val content = withContext(Dispatchers.Default) { prior?.let { SpaceArticleContent.decode(it) } }
            val initial = if (content is SpaceArticleContent.Original)
                SpaceArticleDraft(content.title, content.subtitle, content.markdown) else SpaceArticleDraft()
            draft = (saved ?: initial).asStackDraft()
            draftReady = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { draftFailed = true }
    }
    val storeEditor by rememberUpdatedState<() -> Boolean> { save(visual.snapshot(draft)) }
    DisposableEffect(target, page, draftReady) {
        val active = page == ArticlePage.EDITOR && draftReady
        if (active) onEditorGuard { storeEditor() }
        onDispose { if (active) onEditorGuard(null) }
    }
    val reviewed = remember(page, draft, draftReady) {
        if (page == ArticlePage.REVIEW && draftReady) draft.asArticleDraft() else null
    }
    val reviewedArticle = remember(reviewed) { runCatching { reviewed?.content() }.getOrNull() }
    Column(modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.problem != null && state.articleFocus == null) ProblemNotice(state.problem, account::clearProblem)
        if (page == ArticlePage.LIST) {
            if (state.current?.joined == true) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { draftReady = false; page = ArticlePage.EDITOR },
                    enabled = canWrite, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_article_write)) }
                OutlinedButton(onClick = { linkText = ""; stackReference = null; page = ArticlePage.SHARE },
                    enabled = canWrite, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_article_share_stack)) }
            }
            if (!state.contentLoaded && state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (records.isEmpty() && state.contentLoaded) Text(stringResource(R.string.spaces_article_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.id }) { record ->
                    val content = articles[record]
                    Card(onClick = { selected = record.id; page = ArticlePage.READER }, enabled = !record.deleted,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(painterResource(R.drawable.ic_article), null, Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            val title = if (record.text != null && record !in articles) stringResource(R.string.spaces_article_opening)
                                else articleTitle(content, record)
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3,
                                overflow = TextOverflow.Ellipsis)
                            if (content is SpaceArticleContent.Original && content.subtitle.isNotBlank())
                                Text(content.subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(registeredIdentity(record.author).name, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val status = articleStatus(record)
                            if (status != null) Text(stringResource(status), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (state.hasEarlier) item {
                    TextButton(onClick = account::olderMessages, enabled = !state.working) {
                        Text(stringResource(R.string.protected_chat_older))
                    }
                }
                if (state.hasLater) item { TextButton(onClick = account::latestMessages) { Text(stringResource(R.string.protected_chat_latest)) } }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), enabled = !state.working,
                    onClick = { if (page == ArticlePage.REVIEW) page = ArticlePage.EDITOR else returnToChannel() })
                val title = when (page) {
                    ArticlePage.LIST, ArticlePage.READER -> R.string.spaces_kind_article
                    ArticlePage.EDITOR -> if (editing == null) R.string.spaces_article_write else R.string.spaces_article_edit
                    ArticlePage.REVIEW -> R.string.stack_review_article
                    ArticlePage.SHARE -> R.string.spaces_article_share_stack
                }
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            }
            if (page == ArticlePage.REVIEW) {
                SpaceArticleReview(reviewedArticle, channel, editing != null, draftFailed, draftReady, canWrite,
                    onRetry = { retryDraft++ }, onPublish = {
                        val approved = reviewed
                        val articleId = editing
                        if (approved != null) scope.launch {
                            if (publishing.beforePublishing()) account.publishArticle(target, approved, articleId) { id ->
                                selected = id
                                editing = null
                                page = ArticlePage.READER
                            }
                        }
                    }, modifier = Modifier.weight(1f))
            } else {
                val pageScroll = key(page, selected, editing) { rememberScrollState() }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(pageScroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (page) {
                        ArticlePage.LIST -> Unit
                        ArticlePage.READER -> {
                            val record = readerRecord
                            val content = if (listedRecord != null) articles[listedRecord]
                                else readerDocument?.takeIf { it.first == record }?.second
                            if (record != null) {
                                if (fromCache || state.availability == SpacesAccount.Availability.OFFLINE) {
                                    val revision = record.editedAt ?: record.createdAt
                                    val date = remember(revision) {
                                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(revision))
                                    }
                                    Text(stringResource(R.string.spaces_article_cached, date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                articleStatus(record)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium) }
                                when (content) {
                                    is SpaceArticleContent.Original -> {
                                        ArticleBody(content)
                                        Text(registeredIdentity(record.author).name, style = MaterialTheme.typography.labelMedium)
                                        if (record.edited) Text(stringResource(R.string.spaces_article_edited),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (canWrite && account.canEditArticle(record)) {
                                            OutlinedButton(onClick = {
                                                editing = record.id
                                                draftReady = false
                                                page = ArticlePage.EDITOR
                                            }) { Text(stringResource(R.string.spaces_article_edit)) }
                                        }
                                    }
                                    is SpaceArticleContent.PublicStack -> SharedStackArticle(account, content.link)
                                    null -> {
                                        val loaded = if (listedRecord != null) listedRecord in articles else readerDocument?.first == record
                                        if (loaded || record.text == null) Text(articleTitle(null, record))
                                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                                    }
                                }
                                if (record.position != null && !record.deleted && content != null) {
                                    val label = stringResource(R.string.spaces_article_copy_link)
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                val link = account.articleLink(target, record.id)
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                    .setPrimaryClip(ClipData.newPlainText(label, link))
                                                copied = true
                                                copyFailed = false
                                            } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (_: Exception) { copyFailed = true }
                                        }
                                    }) { Text(if (copied) stringResource(R.string.spaces_copied) else label) }
                                    if (copyFailed) Text(stringResource(R.string.spaces_article_copy_failed),
                                        color = MaterialTheme.colorScheme.error)
                                    if (canWrite && record.change?.let { it.rejected || it.cancelled } != false &&
                                        (record.author == account.ownerKey || state.current?.canManage == true)) {
                                        TextButton(onClick = { confirmRemoval = true }) {
                                            Text(stringResource(R.string.spaces_article_remove), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false },
                                        title = { Text(stringResource(R.string.spaces_article_remove)) },
                                        text = { Text(stringResource(R.string.spaces_article_remove_detail)) },
                                        confirmButton = { TextButton(onClick = {
                                            confirmRemoval = false
                                            account.deleteMessage(target, record) {}
                                        }, enabled = canWrite) {
                                            Text(stringResource(R.string.spaces_article_remove), color = MaterialTheme.colorScheme.error)
                                        } },
                                        dismissButton = { TextButton(onClick = { confirmRemoval = false }) {
                                            Text(stringResource(R.string.spaces_close))
                                        } })
                                }
                            } else if (cachedLookup?.first == selected) Text(stringResource(R.string.spaces_article_unavailable))
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        ArticlePage.EDITOR -> {
                            if (draftFailed) InlineStatus(stringResource(R.string.stack_draft_load_failed), StatusTone.ERROR,
                                actionLabel = stringResource(R.string.retry), onAction = { retryDraft++ })
                            else if (!draftReady) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else StackEditorForm(draft, visual, { save(it) }, { save(visual.applyVisual(draft, it)) },
                                onReview = {
                                    val snapshot = visual.snapshot(draft)
                                    if (save(snapshot) && snapshot.title.isNotBlank() && snapshot.body.isNotBlank()) {
                                        focus.clearFocus(); keyboard?.hide(); page = ArticlePage.REVIEW
                                    }
                                }, sampleMedia = false, enabled = !state.working && !state.savingNavigation,
                                privateSource = channel.visibility == SpaceVisibility.PRIVATE)
                            if (tooLong) InlineStatus(stringResource(R.string.spaces_article_too_long), StatusTone.ERROR)
                        }
                        ArticlePage.REVIEW -> Unit
                        ArticlePage.SHARE -> {
                            OutlinedTextField(linkText, { linkText = it.take(4096); stackReference = null }, Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.spaces_article_stack_link)) }, maxLines = 3)
                            val link = remember(linkText) { StackLink.parse(linkText.trim())?.takeIf { it.author != null && it.signature != null } }
                            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); stackReference = link }, enabled = link != null && canWrite) {
                                Text(stringResource(R.string.spaces_article_review_stack))
                            }
                            stackReference?.let { reference ->
                                SharedStackArticle(account, reference) { article ->
                                    Button(onClick = {
                                        scope.launch {
                                            if (publishing.beforePublishing()) account.shareStackArticle(target, reference, article) { id ->
                                                selected = id
                                                page = ArticlePage.READER
                                            }
                                        }
                                    }, enabled = canWrite) { Text(stringResource(R.string.spaces_article_share_stack)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceArticleReview(article: SpaceArticleContent.Original?, channel: SpaceClient.Channel,
    editing: Boolean, draftFailed: Boolean, draftReady: Boolean, canWrite: Boolean,
    onRetry: () -> Unit, onPublish: () -> Unit, modifier: Modifier = Modifier) {
    val scrollAudience = LocalDensity.current.fontScale > 1.3f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            draftFailed -> InlineStatus(stringResource(R.string.stack_draft_load_failed), StatusTone.ERROR,
                actionLabel = stringResource(R.string.retry), onAction = onRetry)
            !draftReady -> LinearProgressIndicator(Modifier.fillMaxWidth())
            article == null -> InlineStatus(stringResource(R.string.spaces_article_review_invalid), StatusTone.ERROR)
            else -> {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (scrollAudience) ArticleAudience(channel)
                    ArticleBody(article)
                }
                if (!scrollAudience) ArticleAudience(channel)
                Button(onClick = onPublish, enabled = canWrite, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (editing) R.string.spaces_article_publish_edit else R.string.spaces_article_publish))
                }
            }
        }
    }
}

@Composable
private fun ArticleAudience(channel: SpaceClient.Channel) {
    val privateChannel = channel.visibility == SpaceVisibility.PRIVATE
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            Icon(painterResource(if (privateChannel) R.drawable.ic_lock else R.drawable.ic_article),
                null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.spaces_article_review_scope, channel.profile.name),
                    style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (privateChannel) R.string.spaces_article_private_scope
                    else R.string.spaces_article_public_scope), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SharedStackArticle(account: SpacesAccount, link: StackLink,
    share: (@Composable (nl.davidengelen.redsquare.saito.StackArticle) -> Unit)? = null) {
    val library = remember(account, link) { account.stackArticleLibrary(link) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(library) { library.start() }
    val article = library.articles.firstOrNull { it.author == link.author && it.revision == link.signature }
    if (library.loading || !library.cacheReady) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (article != null) {
        if (share != null) {
            LiveStackCard(article, link.backend) {}
            share(article)
        } else {
            if (library.failed) Text(stringResource(R.string.spaces_article_cached, stackArticleDate(article)),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(article.title, style = MaterialTheme.typography.headlineLarge)
            if (article.excerpt.isNotBlank()) Text(article.excerpt, style = MaterialTheme.typography.bodyLarge)
            Text(registeredIdentity(article.author).name, style = MaterialTheme.typography.labelMedium)
            LiveStackMarkdown(article, link.backend)
        }
    } else if (!library.loading && library.cacheReady) {
        Text(stringResource(R.string.spaces_article_stack_unavailable))
        TextButton(onClick = { scope.launch { library.load() } }) { Text(stringResource(R.string.spaces_retry)) }
    }
}

@Composable
private fun ArticleBody(article: SpaceArticleContent.Original) {
    Text(article.title, style = MaterialTheme.typography.headlineLarge)
    if (article.subtitle.isNotBlank()) Text(article.subtitle, style = MaterialTheme.typography.bodyLarge)
    StackMarkdown(article.markdown, reader = true)
}

@Composable
private fun articleTitle(content: SpaceArticleContent?, record: SpaceClient.Message): String = when (content) {
    is SpaceArticleContent.Original -> content.title
    is SpaceArticleContent.PublicStack -> stringResource(R.string.spaces_article_public_stack)
    null -> stringResource(when {
        record.deleted -> R.string.spaces_article_deleted
        record.missingKeys -> R.string.spaces_missing_keys
        else -> R.string.spaces_article_unavailable
    })
}

private fun articleStatus(record: SpaceClient.Message): Int? {
    val change = record.change
    return when {
        change?.cancelled == true -> R.string.spaces_send_cancelled
        change?.kind == SpaceClient.MessageChangeKind.DELETE && change.rejected -> R.string.spaces_article_remove_failed
        change?.kind == SpaceClient.MessageChangeKind.DELETE && change.attempted -> R.string.spaces_delete_waiting_connection
        change?.kind == SpaceClient.MessageChangeKind.DELETE -> R.string.spaces_article_remove_queued
        change?.rejected == true -> R.string.spaces_article_edit_rejected
        change?.attempted == true -> R.string.protected_chat_send_waiting
        change != null -> R.string.protected_chat_send_queued
        record.rejected -> R.string.spaces_article_send_rejected
        record.cancelled -> R.string.spaces_send_cancelled
        record.pending && record.attempted -> R.string.protected_chat_send_waiting
        record.pending -> R.string.protected_chat_send_queued
        else -> null
    }
}

private fun StackDraft.asArticleDraft() = SpaceArticleDraft(title, subtitle, body, sourceMode)

private fun SpaceArticleDraft.asStackDraft() = StackDraft(title, subtitle, markdown, sourceMode)

package nl.davidengelen.redsquare.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

private enum class ArticlePage { LIST, READER, EDITOR, REVIEW, SHARE }

@Composable
internal fun SpaceArticleChannel(account: SpacesAccount, state: SpacesAccount.State, channel: SpaceClient.Channel,
    modifier: Modifier, onEditorGuard: ((() -> Boolean)?) -> Unit) {
    val target = SpacesAccount.Target(requireNotNull(state.selected), channel.id)
    val scope = rememberCoroutineScope()
    val publishing = LocalPublishingAgreement.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var page by rememberSaveable(target.space, target.channel) { mutableStateOf(ArticlePage.LIST) }
    var selected by rememberSaveable(target.space, target.channel) { mutableStateOf<String?>(null) }
    var draft by remember(target) { mutableStateOf(StackDraft("", "", "")) }
    var draftReady by remember(target) { mutableStateOf(false) }
    var draftFailed by remember(target) { mutableStateOf(false) }
    var retryDraft by remember(target) { mutableIntStateOf(0) }
    val visual = remember(target, page == ArticlePage.EDITOR) { StackVisualBuffer() }
    var tooLong by remember(target) { mutableStateOf(false) }
    var linkText by rememberSaveable(target.space, target.channel) { mutableStateOf("") }
    var stackReference by remember(target) { mutableStateOf<StackLink?>(null) }
    val articles by produceState(emptyMap<String, SpaceArticleContent>(), target, state.messages) {
        value = withContext(Dispatchers.Default) {
            state.messages.mapNotNull { record ->
                val content = record.text?.let { runCatching { SpaceArticleContent.decode(it) }.getOrNull() }
                if (record.isArticle && content != null) record.id to content else null
            }.toMap()
        }
    }
    val canWrite = state.current?.canSend == true && !state.loadingChannel && !state.working && !state.savingNavigation
    fun save(value: StackDraft): Boolean {
        val saved = value.asArticleDraft()
        tooLong = !saved.fitsStorage()
        if (!tooLong) account.editArticleDraft(target, null, saved)
        draft = value
        return !tooLong
    }
    fun returnToChannel() {
        if (page == ArticlePage.EDITOR && draftReady && !save(visual.snapshot(draft))) return
        focus.clearFocus(); keyboard?.hide()
        page = ArticlePage.LIST
        selected = null
    }
    BackHandler(page != ArticlePage.LIST && !state.working) {
        if (page == ArticlePage.REVIEW) page = ArticlePage.EDITOR else returnToChannel()
    }
    val inEditor = page == ArticlePage.EDITOR || page == ArticlePage.REVIEW
    LaunchedEffect(target, inEditor, retryDraft) {
        if (!inEditor) return@LaunchedEffect
        draftFailed = false
        try {
            draft = (account.articleDraft(target, null) ?: SpaceArticleDraft()).asStackDraft()
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
    Column(modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (page == ArticlePage.LIST) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { draftReady = false; page = ArticlePage.EDITOR },
                    enabled = canWrite, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.spaces_article_write)) }
                OutlinedButton(onClick = { linkText = ""; stackReference = null; page = ArticlePage.SHARE },
                    enabled = canWrite, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.spaces_article_share_stack)) }
            }
            if (!state.contentLoaded && state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val records = state.messages.filter { it.isArticle }.asReversed()
            if (records.isEmpty() && state.contentLoaded) Text(stringResource(R.string.spaces_article_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.id }) { record ->
                    val content = articles[record.id]
                    Card(onClick = { selected = record.id; page = ArticlePage.READER }, enabled = !record.deleted) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(articleTitle(content, record), style = MaterialTheme.typography.titleLarge, maxLines = 3,
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
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), enabled = !state.working,
                onClick = { if (page == ArticlePage.REVIEW) page = ArticlePage.EDITOR else returnToChannel() })
            val title = when (page) {
                ArticlePage.LIST, ArticlePage.READER -> R.string.spaces_kind_article
                ArticlePage.EDITOR -> R.string.spaces_article_write
                ArticlePage.REVIEW -> R.string.stack_review_article
                ArticlePage.SHARE -> R.string.spaces_article_share_stack
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                ArticlePage.LIST -> Unit
                ArticlePage.READER -> {
                    val record = state.messages.firstOrNull { it.id == selected }
                    val content = selected?.let(articles::get)
                    if (record != null) {
                        articleStatus(record)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium) }
                        when (content) {
                            is SpaceArticleContent.Original -> {
                                ArticleBody(content)
                                Text(registeredIdentity(record.author).name, style = MaterialTheme.typography.labelMedium)
                            }
                            is SpaceArticleContent.PublicStack -> SharedStackArticle(account, content.link)
                            null -> Text(articleTitle(null, record))
                        }
                    } else Text(stringResource(R.string.spaces_article_unavailable))
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
                        }, sampleMedia = false, enabled = !state.working && !state.savingNavigation)
                    if (tooLong) InlineStatus(stringResource(R.string.spaces_article_too_long), StatusTone.ERROR)
                }
                ArticlePage.REVIEW -> {
                    val reviewed = remember(draft, draftReady) { draft.takeIf { draftReady }?.asArticleDraft() }
                    val article = remember(reviewed) { runCatching { reviewed?.content() }.getOrNull() }
                    if (article != null) ArticleBody(article)
                    else InlineStatus(stringResource(R.string.spaces_article_review_invalid), StatusTone.ERROR)
                    Text(stringResource(if (channel.visibility == SpaceVisibility.PRIVATE) R.string.spaces_article_private_scope
                        else R.string.spaces_article_public_scope), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        val approved = reviewed ?: return@Button
                        scope.launch {
                            if (publishing.beforePublishing()) account.publishArticle(target, approved) { id ->
                                selected = id
                                page = ArticlePage.READER
                            }
                        }
                    }, enabled = article != null && canWrite) { Text(stringResource(R.string.spaces_article_publish)) }
                }
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

private fun articleStatus(record: SpaceClient.Message): Int? = when {
    record.rejected -> R.string.protected_chat_send_rejected
    record.cancelled -> R.string.spaces_send_cancelled
    record.pending && record.attempted -> R.string.protected_chat_send_waiting
    record.pending -> R.string.protected_chat_send_queued
    else -> null
}

private fun StackDraft.asArticleDraft() = SpaceArticleDraft(title, subtitle, body, sourceMode)

private fun SpaceArticleDraft.asStackDraft() = StackDraft(title, subtitle, markdown, sourceMode)

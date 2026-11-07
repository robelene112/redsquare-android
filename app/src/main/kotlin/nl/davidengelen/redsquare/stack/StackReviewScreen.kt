package nl.davidengelen.redsquare.stack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.Serializable
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.*

private enum class StackDestination(override val label: Int, override val icon: Int) : ModuleDestination {
    DISCOVER(R.string.stack_discover, R.drawable.ic_globe),
    FOLLOWING(R.string.following_title, R.drawable.ic_bookmark),
    ARTICLES(R.string.stack_my_articles, R.drawable.ic_article);
    override val caption get() = label
}

internal enum class StackPage(val title: Int) {
    DISCOVER(R.string.stack_discover), LATEST(R.string.stack_latest), DRAFTS(R.string.stack_drafts), FOLLOWING(R.string.following_title), ARTICLES(R.string.stack_my_articles),
    ARTICLE(R.string.stack_article_preview), AUTHOR(R.string.stack_author_name), EDITOR(R.string.stack_write),
    REVIEW(R.string.stack_publish_review), PUBLISHED(R.string.stack_published_preview), PREVIEW_ARTICLE(R.string.stack_your_article),
    ACCESS(R.string.stack_access), ACCESS_REVIEW(R.string.stack_access_review),
    SUBSCRIPTIONS(R.string.stack_subscriptions), SUBSCRIPTION(R.string.stack_your_access),
    AUTHOR_ACCESS(R.string.stack_author_access)
}

private data class StackRoute(val page: StackPage, val article: Int = 0) : Serializable

/** A local, explicitly labelled design journey. It has no network/wallet/signing dependency. */
@Composable
internal fun StackReviewScreen(modifier: Modifier, chrome: @Composable (Boolean) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(StackDestination.DISCOVER) }
    var path by rememberSaveable { mutableStateOf(listOf(StackRoute(StackPage.DISCOVER))) }
    var follows by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var access by rememberSaveable { mutableStateOf(false) }
    var publication by rememberSaveable { mutableStateOf<StackDraft?>(null) }
    val initialTitle = stringResource(R.string.stack_draft_title)
    val initialSubtitle = stringResource(R.string.stack_draft_subtitle)
    val initialBody = stringResource(R.string.stack_draft_body)
    val visual = remember { StackVisualBuffer() }
    var draft by rememberSaveable(stateSaver = visual.saver) { mutableStateOf(StackDraft(initialTitle, initialSubtitle, initialBody)) }
    var explanation by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<StackPage?>(null) }
    val route = path.last()
    val page = route.page
    val article = route.article
    val author = if (article == 1) 1 else 0
    val followed = author in follows
    fun follow() { follows = if (followed) follows - author else follows + author }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun navigate(next: StackPage, article: Int = 0) {
        draft = visual.snapshot(draft)
        focus.clearFocus(); keyboard?.hide()
        val entry = StackRoute(next, article)
        if (entry != route) path = path + entry
    }
    fun back() { draft = visual.snapshot(draft); focus.clearFocus(); keyboard?.hide(); path = path.dropLast(1) }
    fun openArticle(index: Int) { navigate(StackPage.ARTICLE, index) }
    BackHandler(path.size > 1) { back() }
    val pages = rememberSaveableStateHolder()
    val keyboardVisible = keyboardOccupiesWindow()
    Column(modifier.imePadding()) {
        Box(Modifier.weight(1f)) {
            SingleOwnerReveal(route, Modifier.fillMaxSize()) {
                pages.SaveableStateProvider("${page.name}:${route.article}") {
                    val list = rememberLazyListState()
                    val collapsible = page in setOf(StackPage.DISCOVER, StackPage.LATEST, StackPage.DRAFTS, StackPage.FOLLOWING, StackPage.ARTICLES,
                        StackPage.ARTICLE, StackPage.AUTHOR, StackPage.PREVIEW_ARTICLE)
                    val header = rememberReadingHeaderState(enabled = collapsible, list = list)
                    val anchor = rememberReadingAnchorModifier(list, enabled = !header.inMotion, relativeToRoot = true)
                    Column(Modifier.fillMaxSize().nestedScroll(header.connection).readingNavigation(header)) {
                        ReadingHeader(header) { chrome(false) }
                        val title = if (page == StackPage.AUTHOR) sampleAuthor(article) else stringResource(page.title)
                        val compactTitle = LocalDensity.current.fontScale <= 1.3f && page != StackPage.EDITOR
                        val preview: @Composable () -> Unit = {
                            TextButton(onClick = { explanation = true }, modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Text(stringResource(R.string.stack_review), Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = if (path.size > 1) 4.dp else AppSpacing.screen),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (path.size > 1) FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back)) { back() }
                            if (compactTitle) {
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                preview()
                            } else Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleLarge)
                                preview()
                            }
                            if (page == StackPage.EDITOR) TextButton(onClick = { navigate(StackPage.REVIEW) }) {
                                Text(stringResource(R.string.stack_review_action))
                            }
                        }
                        if (page == StackPage.EDITOR) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .testTag("stack-content").padding(start = AppSpacing.screen, end = AppSpacing.screen,
                                    top = 12.dp, bottom = 88.dp)) {
                                StackEditorForm(draft, visual, { draft = it }, { draft = visual.applyVisual(draft, it) }, { navigate(StackPage.REVIEW) })
                            }
                        } else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()
                            .testTag("stack-content").then(anchor),
                            contentPadding = PaddingValues(start = AppSpacing.screen, end = AppSpacing.screen,
                                top = 12.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (page) {
                                StackPage.DISCOVER, StackPage.LATEST -> {
                                    val latest = page == StackPage.LATEST
                                    if (!latest) item { StackIntro(stringResource(R.string.stack_hero), stringResource(R.string.stack_hero_body),
                                        stringResource(R.string.stack_eyebrow)) }
                                    item { DesignTabs(listOf(stringResource(R.string.stack_for_you), stringResource(R.string.stack_latest)),
                                        if (latest) 1 else 0, { if (it == 1) navigate(StackPage.LATEST) else if (latest) back() }) }
                                    if (latest) item { StackSection(stringResource(R.string.stack_latest_stories)) }
                                    item { StackArticleCard(if (latest) 1 else 0, access) { openArticle(if (latest) 1 else 0) } }
                                    item { StackSection(stringResource(R.string.stack_worth_reading)) }
                                    item { StackArticleCard(if (latest) 0 else 1, access) { openArticle(if (latest) 0 else 1) } }
                                    item { StackArticleCard(2, access) { openArticle(2) } }
                                }
                                StackPage.FOLLOWING -> {
                                    item { StackIntro(stringResource(R.string.stack_circle), stringResource(R.string.stack_following_intro)) }
                                    item { StackAuthorRow(0 in follows, onFollow = { follows = if (0 in follows) follows - 0 else follows + 0 }) { navigate(StackPage.AUTHOR) } }
                                    item { StackArticleCard(0, access) { openArticle(0) } }
                                    item { StackArticleCard(2, access) { openArticle(2) } }
                                }
                                StackPage.ARTICLES, StackPage.DRAFTS -> {
                                    val drafts = page == StackPage.DRAFTS
                                    if (!drafts) item { DesignTabs(listOf(stringResource(R.string.stack_published), stringResource(R.string.stack_drafts)),
                                        0, { if (it == 1) navigate(StackPage.DRAFTS) }) }
                                    if (drafts) {
                                        item { StackIntro(stringResource(R.string.stack_room_to_think), stringResource(R.string.stack_drafts_detail)) }
                                        item { StackMenuRow(draft.title, stringResource(R.string.stack_local_draft), R.drawable.ic_compose) { navigate(StackPage.EDITOR) } }
                                    } else {
                                        publication?.let { saved -> item { StackDraftCard(saved) { navigate(StackPage.PREVIEW_ARTICLE) } } }
                                        item { StackArticleCard(1, access) { openArticle(1) } }
                                    }
                                    item { StackMenuRow(stringResource(R.string.stack_subscriptions), stringResource(R.string.stack_subscriptions_detail),
                                        R.drawable.ic_bookmark) { navigate(StackPage.SUBSCRIPTIONS) } }
                                }
                                StackPage.ARTICLE -> articleContent(article, access, followed,
                                    { follow() }, { navigate(StackPage.AUTHOR, article) }, { navigate(StackPage.ACCESS) })
                                StackPage.AUTHOR -> {
                                    item { StackAuthorProfile(article, followed) { follow() } }
                                    item { StackSection(stringResource(R.string.stack_writing_by, sampleAuthor(article))) }
                                    if (article == 1) item { StackArticleCard(1, access) { openArticle(1) } }
                                    else {
                                        item { StackArticleCard(0, access) { openArticle(0) } }
                                        item { StackArticleCard(2, access) { openArticle(2) } }
                                    }
                                }
                                StackPage.EDITOR -> Unit // The persistent form above owns its editor lifetime.
                                StackPage.REVIEW -> publicationContent(draft, { draft = it }) { confirmation = StackPage.REVIEW }
                                StackPage.PUBLISHED -> {
                                    item { StackIntro(stringResource(R.string.stack_publication_hero), stringResource(R.string.stack_no_publication),
                                        stringResource(R.string.stack_preview_complete)) }
                                    item {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(publication?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                                            Text(publication?.subtitle.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    item { DesignButton({ navigate(StackPage.PREVIEW_ARTICLE) }, Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.stack_view_article)) } }
                                    item { DesignButton({ destination = StackDestination.ARTICLES; path = listOf(StackRoute(StackPage.ARTICLES)) },
                                        Modifier.fillMaxWidth(), outlined = true) { Text(stringResource(R.string.stack_back_articles)) } }
                                }
                                StackPage.PREVIEW_ARTICLE -> {
                                    val saved = publication ?: draft
                                    item { StackIntro(saved.title, saved.subtitle, stringResource(R.string.stack_published_preview)) }
                                    if (saved.cover) item { StackCover(editorial = true, height = 200.dp) }
                                    item { StackMarkdown(saved.body) }
                                    item { Text(stringResource(R.string.stack_no_publication), style = MaterialTheme.typography.bodySmall) }
                                    item { DesignButton({ navigate(StackPage.EDITOR) }, outlined = true) { Text(stringResource(R.string.stack_edit_preview)) } }
                                }
                                else -> accessContent(page, access, { navigate(it) }, { confirmation = StackPage.ACCESS_REVIEW })
                            }
                        }
                    }
                }
            }
            if (page in setOf(StackPage.DISCOVER, StackPage.LATEST, StackPage.DRAFTS, StackPage.FOLLOWING, StackPage.ARTICLES) && !keyboardVisible) {
                FloatingActionButton(onClick = { navigate(StackPage.EDITOR) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large) {
                    Icon(painterResource(R.drawable.ic_compose), stringResource(R.string.stack_write_article))
                }
            }
        }
        if (!keyboardVisible && page !in setOf(StackPage.EDITOR, StackPage.REVIEW, StackPage.ACCESS,
            StackPage.ACCESS_REVIEW, StackPage.SUBSCRIPTION, StackPage.AUTHOR_ACCESS, StackPage.PUBLISHED)) {
            HorizontalDivider()
            ModuleNavigation(StackDestination.entries, destination, enabled = true) {
                focus.clearFocus(); keyboard?.hide(); destination = it
                path = listOf(StackRoute(when (it) {
                    StackDestination.DISCOVER -> StackPage.DISCOVER
                    StackDestination.FOLLOWING -> StackPage.FOLLOWING
                    StackDestination.ARTICLES -> StackPage.ARTICLES
                }))
            }
        }
    }
    if (explanation) AlertDialog(onDismissRequest = { explanation = false },
        title = { Text(stringResource(R.string.stack_review)) }, text = { Text(stringResource(R.string.stack_review_detail)) },
        confirmButton = { TextButton(onClick = { explanation = false }) { Text(stringResource(R.string.done)) } })
    confirmation?.let { requested ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (requested == StackPage.REVIEW) R.string.stack_publish_preview else R.string.stack_confirm_access)) },
            text = { Text(stringResource(if (requested == StackPage.REVIEW) R.string.stack_no_publication else R.string.stack_no_payment)) },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.stack_cancel)) } },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                if (requested == StackPage.REVIEW) { draft = visual.snapshot(draft); publication = draft; navigate(StackPage.PUBLISHED) }
                else { access = true; path = path.dropLast(2) }
            }) { Text(stringResource(R.string.stack_show_preview)) } })
    }
}

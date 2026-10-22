package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.ui.preserveReadingAnchor
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.registry.knownRegisteredIdentities
import nl.davidengelen.redsquare.registry.LocalRegistryAuthority
import nl.davidengelen.redsquare.registry.RegistryLookup
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.saito.*

/** Retained by the wallet/backend navigation memory, never by an old coroutine scope. */
internal class SearchViewState {
    var query by mutableStateOf("")
    var searchedQuery by mutableStateOf("")
    var submitted by mutableStateOf(false)
    var request by mutableIntStateOf(0)
    var completed = 0
    var activeLookup: Job? = null
    var posts by mutableStateOf<List<FeedPost>>(emptyList())
    var failed by mutableStateOf(false)
    var unreadable by mutableStateOf(false)
    var cursors by mutableStateOf<Map<String, Long?>>(emptyMap())
    var pending by mutableStateOf<List<String>>(emptyList())
    var pagingUnavailable by mutableStateOf(false)
    var limitReached by mutableStateOf(false)
    var saveError by mutableStateOf(false)
    var exactPerson by mutableStateOf<SaitoRegisteredName?>(null)
    var usernameFailed by mutableStateOf(false)
    var usernameSaveError by mutableStateOf(false)
    var usernameConfirmed by mutableStateOf(false)
    var usernamePending = false

    fun search() {
        submitted = true
        searchedQuery = query.trim()
        if (exactPerson?.identifier != searchedQuery) {
            exactPerson = null
            usernameSaveError = false
        }
        usernamePending = searchedQuery.matches(Regex("[A-Za-z0-9]{1,45}@saito"))
        usernameFailed = false
        usernameConfirmed = false
        val before = System.currentTimeMillis() + 1
        cursors = listOf("RedSquare", "RedSquareRetweet").associateWith { before }
        pending = cursors.keys.toList()
        pagingUnavailable = false
        limitReached = false
        request++
    }

    fun clear() {
        // Cancel synchronously so queued coroutine resumptions cannot publish old state
        // while Compose is still scheduling disposal of the previous effect.
        activeLookup?.cancel()
        activeLookup = null
        query = ""
        searchedQuery = ""
        submitted = false
        // Invalidate an unfinished lookup without scheduling another one.
        request++
        completed = request
        pending = emptyList()
        cursors = emptyMap()
        exactPerson = null
        usernamePending = false
        usernameFailed = false
        usernameConfirmed = false
        usernameSaveError = false
        failed = false
        pagingUnavailable = false
        limitReached = false
        saveError = false
    }

    fun loadMore() {
        pending = cursors.filterValues { it != null }.keys.toList()
        request++
    }
}

@Composable
internal fun SearchScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend,
    view: SearchViewState, modifier: Modifier, knownAccounts: Set<String>, onBack: () -> Unit, onPost: (FeedPost) -> Unit,
    onIdentity: (String) -> Unit, social: SocialState?, quotes: QuoteState?, onQuote: (FeedPost) -> Unit, authorActions: PostAuthorActions? = null) {
    val personal = LocalPersonalControls.current
    val context = LocalContext.current.applicationContext
    val keyboard = LocalSoftwareKeyboardController.current
    val registrar = LocalRegistryAuthority.current
    val revisions = remember(backend) { AndroidPostRevisions(context, backend) }
    val cache = remember(backend) { AndroidSearchCache(context, backend) }
    val revision by AndroidPostRevisions.changes.collectAsState()
    var loading by remember(view) { mutableStateOf(false) }
    var cacheReady by remember(view) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(view, cache) {
        val saved = withContext(Dispatchers.IO) { cache.load() }
        // A retained view or newer proof can change while disk validation is suspended.
        while (true) {
            val known = view.posts
            val checkedRevision = AndroidPostRevisions.changes.value
            val retained = withContext(Dispatchers.IO) { cache.retain(known + saved.posts, view.searchedQuery) }
            if (view.posts !== known || AndroidPostRevisions.changes.value != checkedRevision) continue
            view.posts = retained.posts
            view.unreadable = view.unreadable || saved.unreadable || retained.unreadable
            cacheReady = true
            break
        }
    }
    LaunchedEffect(view, revision, view.posts) {
        val known = view.posts
        val checkedRevision = AndroidPostRevisions.changes.value
        val applied = withContext(Dispatchers.IO) { revisions.apply(known) }
        // A newer archive page or proof can arrive while disk validation is suspended.
        if (view.posts === known && AndroidPostRevisions.changes.value == checkedRevision) {
            view.posts = applied.posts
            view.unreadable = view.unreadable || applied.unreadable
        }
    }
    LaunchedEffect(wallet, endpoint, view.request, cacheReady) {
        if (wallet == null || !cacheReady || view.request == 0 || view.completed == view.request) return@LaunchedEffect
        val request = view.request
        val lookup = currentCoroutineContext().job
        view.activeLookup = lookup
        val pages = view.pending.mapNotNull { stream -> view.cursors[stream]?.let { stream to it } }
        loading = true
        view.failed = false
        try {
            withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val identifier = withContext(Dispatchers.Main.immediate) {
                        view.searchedQuery.takeIf { view.usernamePending }
                    }
                    if (identifier != null) {
                        try {
                            val person = RegistryLookup(peer, registrar).namecheck(identifier)?.takeIf { it.active }
                            withContext(Dispatchers.Main.immediate) {
                                view.exactPerson = person
                                view.usernameConfirmed = person != null
                            }
                            val saved = try {
                                person?.let { RegisteredNameStore(context, it.publicKey, registrar).save(it, backend) }
                                true
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { false }
                            withContext(Dispatchers.Main.immediate) { view.usernameSaveError = !saved }
                        } catch (_: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            withContext(Dispatchers.Main.immediate) { view.usernameFailed = true }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { withContext(Dispatchers.Main.immediate) { view.usernameFailed = true } }
                        // Paging/retrying post streams must not repeat a completed username attempt.
                        withContext(Dispatchers.Main.immediate) { view.usernamePending = false }
                    }
                    for ((stream, before) in pages) {
                        if (withContext(Dispatchers.Main.immediate) { view.limitReached }) break
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", stream)
                            addProperty("flagged_ne", 1); addProperty("limit", 100)
                            addProperty("created_earlier_than", before)
                        }
                        val query = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                        val page = if (stream == "RedSquare") RedSquareFeed.readSearch(response)
                            else RedSquareFeed.readCurrentQuotes(response)
                        val observed = withContext(Dispatchers.Main.immediate) { view.posts }
                        revisions.reconcileDeletions(peer, page.deletions, page.posts + observed)
                        page.posts.forEach(revisions::observe)
                        val searchedQuery = withContext(Dispatchers.Main.immediate) { view.searchedQuery }
                        while (true) {
                            val known = withContext(Dispatchers.Main.immediate) { view.posts }
                            val checkedRevision = AndroidPostRevisions.changes.value
                            val applied = cache.retain(page.posts + known, searchedQuery)
                            val published = withContext(Dispatchers.Main.immediate) {
                                if (view.posts !== known || AndroidPostRevisions.changes.value != checkedRevision) false
                                else {
                                    social?.observe(page.posts)
                                    view.posts = applied.posts
                                    view.unreadable = view.unreadable || applied.unreadable || page.ignored > 0
                                    val next = page.oldestCreatedAt?.takeIf { page.rowCount == 100 && it < before }
                                    view.pagingUnavailable = view.pagingUnavailable || (page.rowCount == 100 && next == null)
                                    view.cursors = view.cursors + (stream to next)
                                    view.pending = view.pending - stream
                                    if (applied.limited) {
                                        view.limitReached = true
                                        view.cursors = view.cursors.mapValues { null }
                                        view.pending = emptyList()
                                    }
                                    true
                                }
                            }
                            // Reuse this verified page; do not repeat a completed network read.
                            if (published) break
                        }
                        val retained = withContext(Dispatchers.Main.immediate) { view.posts to view.searchedQuery }
                        val saved = try { cache.save(retained.first, retained.second); true }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                        withContext(Dispatchers.Main.immediate) { view.saveError = !saved }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            view.failed = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { view.failed = true }
        finally {
            if (view.activeLookup === lookup) view.activeLookup = null
            if (currentCoroutineContext().isActive && view.request == request) {
                view.completed = request
                loading = false
            }
        }
    }
    val matches = view.posts.filter { personal?.hidesPosts(it.author) == false &&
        it.text.contains(view.searchedQuery, ignoreCase = true) }
    // Follow preferences are public local input, not proof of a valid curve point.
    var validAccounts by remember(view) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(view, knownAccounts) {
        validAccounts = withContext(Dispatchers.IO) { knownAccounts.filter(SaitoWallet::isPublicKey).toSet() }
    }
    val knownKeys = remember(view.posts, wallet, validAccounts) {
        view.posts.map { it.author }.toSet() + validAccounts + listOfNotNull(wallet?.publicKey)
    }
    val knownNames = knownRegisteredIdentities(knownKeys)
    val exactKey = remember(view.searchedQuery) { view.searchedQuery.takeIf(SaitoWallet::isPublicKey) }
    val exactName = view.exactPerson?.takeIf { it.identifier == view.searchedQuery }
    val people = remember(knownKeys, knownNames, view.searchedQuery, exactKey, exactName) {
        (listOfNotNull(exactName?.publicKey, exactKey) + (knownKeys + knownNames.keys).filter { key ->
            view.searchedQuery.isNotBlank() && (key.startsWith(view.searchedQuery) ||
                knownNames[key]?.name?.startsWith(view.searchedQuery, ignoreCase = true) == true)
        }.sorted()).distinct()
    }
    val resultsPosition = rememberLazyListState()
    val canSearch = wallet != null && cacheReady && !loading && view.query.isNotBlank()
    val submit = {
        if (canSearch) {
            keyboard?.hide()
            loading = true
            view.search()
        }
    }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.search_title), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = view.query, onValueChange = { if (it.length <= 256) view.query = it },
            label = { Text(stringResource(R.string.search_query)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                if (view.query.isNotEmpty() || view.submitted) IconButton(onClick = {
                    view.clear()
                    loading = false
                }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear))
                }
            })
        Row {
            TextButton(modifier = Modifier.weight(1f), enabled = canSearch, onClick = submit) {
                Text(stringResource(R.string.search_history))
            }
            if (view.failed) {
                TextButton(modifier = Modifier.weight(1f), enabled = wallet != null && !loading && view.pending.isNotEmpty(),
                    onClick = { loading = true; view.request++ }) { Text(stringResource(R.string.search_retry)) }
            } else {
                TextButton(modifier = Modifier.weight(1f), enabled = wallet != null && !loading && view.cursors.values.any { it != null },
                    onClick = { loading = true; view.loadMore() }) { Text(stringResource(R.string.search_more)) }
            }
        }
        PersonalControlsStatus(personal)
        InlineStatus(stringResource(R.string.status_search_coverage), details = listOf(
            stringResource(R.string.search_coverage), stringResource(R.string.search_people_coverage)), announce = false)
        InlineStatus(if (loading) stringResource(R.string.search_loading) else null, StatusTone.WORKING)
        InlineStatus(if (view.failed) stringResource(R.string.search_error) else null, StatusTone.ERROR)
        InlineStatus(if (view.usernameFailed) stringResource(R.string.search_username_error) else null, StatusTone.WARNING)
        InlineStatus(if (view.submitted && view.unreadable) stringResource(R.string.search_unreadable) else null, StatusTone.WARNING)
        InlineStatus(if (view.pagingUnavailable) stringResource(R.string.search_paging_unavailable) else null, StatusTone.WARNING)
        InlineStatus(if (view.limitReached) stringResource(R.string.search_limit) else null, StatusTone.INFO)
        InlineStatus(if (view.submitted && (view.saveError || view.usernameSaveError || people.any { knownNames[it]?.saveError == true }))
            stringResource(R.string.search_save_error) else null, StatusTone.WARNING)
        if (view.submitted && !loading && !view.failed && !view.limitReached && matches.isEmpty()) Text(stringResource(R.string.search_empty))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().preserveReadingAnchor(resultsPosition), state = resultsPosition) {
            if (view.submitted) {
                items(people, key = { "person:$it" }) { personKey ->
                    val label = exactName?.takeIf { it.publicKey == personKey }?.identifier ?: registeredIdentity(personKey).name
                    val confirmed = view.usernameConfirmed && exactName?.publicKey == personKey
                    Column(Modifier.fillMaxWidth().clickable { onIdentity(personKey) }.padding(vertical = 12.dp)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        if (!confirmed && label.equals(view.searchedQuery, ignoreCase = true))
                            Text(stringResource(R.string.search_cached_name), style = MaterialTheme.typography.bodySmall)
                        Text(personKey, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
            if (view.submitted) items(matches, key = { it.id }) { post ->
                PostCard(post, backend, onIdentity, onPost, social, quotes, onQuote, authorActions)
                HorizontalDivider()
            }
        }
    }
}

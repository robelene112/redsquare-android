package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable
internal fun SpaceSearchPage(account: SpacesAccount, state: SpacesAccount.State, space: String, modifier: Modifier,
    onBack: () -> Unit, onOpen: (SpaceDestination) -> Unit) {
    var query by rememberSaveable(space) { mutableStateOf("") }
    var channelFilter by rememberSaveable(space) { mutableStateOf<String?>(null) }
    var kindFilter by rememberSaveable(space) { mutableStateOf<String?>(null) }
    val revision = state.accessRevision
    val listedNames = remember(state.channels) { state.channels.map { it.id to it.profile.name } }
    var attempt by remember(account, space) { mutableIntStateOf(0) }
    var options by remember(account, space, revision) { mutableStateOf<Result<List<SpaceClient.Channel>>?>(null) }
    LaunchedEffect(account, space, revision, listedNames, attempt) {
        options = try { Result.success(account.searchChannels(space, revision)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }
    val channels = options?.getOrNull().orEmpty()
    val access = channels.map { it.id }
    val channel = channelFilter?.takeIf { it in access }
    val kind = kindFilter?.let(SpaceDestination.Kind::read)
    val term = query.trim()
    val filter = SpaceSearchFilter(channel, kind)
    var cursorChannel by rememberSaveable(space, term, channelFilter, kindFilter) { mutableStateOf<String?>(null) }
    var cursorBefore by rememberSaveable(space, term, channelFilter, kindFilter) { mutableStateOf<Long?>(null) }
    val cursor = cursorChannel?.takeIf { it in access }?.let { SpaceSearchCursor(it, cursorBefore) }
    fun selectPage(page: SpaceSearchCursor?) {
        cursorChannel = page?.channel
        cursorBefore = page?.before
    }
    var result by remember(account, space, term, filter, access, cursor, revision) { mutableStateOf<SpaceSearchPage?>(null) }
    var failed by remember(account, space, term, filter, access, cursor, revision) { mutableStateOf(false) }
    val optionsReady = options?.isSuccess == true
    val optionsFailed = options?.isFailure == true
    LaunchedEffect(account, space, term, filter, access, cursor, attempt, revision, optionsReady) {
        if (term.isBlank() || !optionsReady) return@LaunchedEffect
        failed = false
        delay(200)
        try { result = account.searchCached(space, term, filter, cursor, revision) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_search_back_to_info)) { onBack() }
            Text(stringResource(R.string.spaces_search_title), Modifier.padding(horizontal = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.testTag("spaces-search-list"), contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(query, { query = it.take(128) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.spaces_search_query)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) })
            }
            item {
                SearchOptions(channels.firstOrNull { it.id == channel }?.profile?.name ?: stringResource(R.string.spaces_search_all_channels),
                    channels.map { it.id to it.profile.name }, stringResource(R.string.spaces_search_all_channels)) { channelFilter = it }
            }
            item {
                val types = listOf(SpaceDestination.Kind.MESSAGE, SpaceDestination.Kind.ARTICLE, SpaceDestination.Kind.FILE)
                SearchOptions(kind?.let { stringResource(searchKindLabel(it)) } ?: stringResource(R.string.spaces_search_all_types),
                    types.map { it.wire to stringResource(searchKindLabel(it)) }, stringResource(R.string.spaces_search_all_types)) { kindFilter = it }
            }
            item {
                Text(stringResource(R.string.spaces_search_saved_history), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (term.isNotBlank() && result == null && !failed && !optionsFailed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (failed || optionsFailed) item {
                InlineStatus(stringResource(R.string.spaces_search_failed), StatusTone.WARNING)
                TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_retry))
                }
            }
            val page = result
            if (page != null) {
                if (cursor != null) item {
                    TextButton(onClick = { selectPage(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_search_first_page))
                    }
                }
                items(page.hits.filter { it.address.channel in access }, key = { it.address.record.orEmpty() }) { hit ->
                    Surface(onClick = { onOpen(hit.address) }, enabled = !state.working && !state.savingNavigation,
                        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(hit.channelName, style = MaterialTheme.typography.labelLarge)
                            Text(hit.excerpt, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (page.hits.isEmpty() && page.next == null) item {
                    Text(stringResource(R.string.spaces_search_no_matches), style = MaterialTheme.typography.bodyMedium)
                }
                page.next?.let { next -> item {
                    OutlinedButton(onClick = { selectPage(next) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_search_next_page))
                    }
                } }
            }
        }
    }
}

@Composable
private fun SearchOptions(label: String, options: List<Pair<String, String>>, all: String, onChoice: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { open = false; onChoice(null) })
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onChoice(key) })
            }
        }
    }
}

private fun searchKindLabel(kind: SpaceDestination.Kind): Int = when (kind) {
    SpaceDestination.Kind.ARTICLE -> R.string.spaces_search_articles
    SpaceDestination.Kind.FILE -> R.string.spaces_search_files
    else -> R.string.spaces_search_messages
}

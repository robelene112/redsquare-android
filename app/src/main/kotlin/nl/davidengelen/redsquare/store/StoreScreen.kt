package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*
import java.io.IOException

/** Reading destinations keep each catalogue anchor and return through their actual origin. */
@Composable
internal fun StoreScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, modifier: Modifier,
    chrome: @Composable (Boolean) -> Unit, entry: StoreLink? = null, onExitLink: (() -> Unit)? = null,
    chatBackend: SaitoBackend = backend, chatEndpoint: String = endpoint, onChatVisibility: (Boolean) -> Unit = {},
    onWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit, onNodeSettings: () -> Unit) {
    var path by rememberSaveable(backend, entry) { mutableStateOf(arrayListOf(entry ?: StoreLink(backend))) }
    val states = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var contact by rememberSaveable(wallet.publicKey, backend) { mutableStateOf<String?>(null) }
    DisposableEffect(contact != null) {
        onChatVisibility(contact != null)
        onDispose { onChatVisibility(false) }
    }
    var reference by rememberSaveable(wallet.publicKey, backend) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var contactFailed by remember { mutableStateOf(false) }
    var preparingContact by remember { mutableStateOf(false) }
    fun pathKey(target: StoreLink) = if (target.signature != null || target.nftId != null) "product:${target.url()}" else target.url()
    fun replacePath(next: List<StoreLink>) {
        val kept = next.map(::pathKey).toSet()
        path.map(::pathKey).filterNot(kept::contains).distinct().forEach(states::removeState)
        path = ArrayList(next)
    }
    fun open(target: StoreLink) {
        val next = path + target
        replacePath(if (next.size <= 32) next else listOf(next.first()) + next.takeLast(31))
    }
    fun back() { if (path.size > 1) replacePath(path.dropLast(1)) else onExitLink?.invoke() }
    val canBack = path.size > 1 || onExitLink != null
    androidx.activity.compose.BackHandler(contact == null && canBack) { back() }
    SingleOwnerReveal(contact ?: path.last(), modifier) {
        val seller = contact
        if (seller != null) {
            Column(Modifier.fillMaxSize()) {
                chrome(false)
                nl.davidengelen.redsquare.chat.ChatScreen(wallet, chatEndpoint, chatBackend, Modifier.weight(1f),
                    onWalletMetadata, initialContact = seller, initialDraft = reference,
                    onInitialDestinationClosed = { contact = null; reference = null }) { contact = null; reference = null }
            }
        } else {
            val target = path.last()
            if (target.nftId != null || target.signature != null) {
                states.SaveableStateProvider("product:${target.url()}") {
                    StoreProductScreen(wallet, backend, endpoint, target, Modifier.fillMaxSize(), chrome,
                        onBack = { back() }, onSeller = { open(StoreLink(backend, it)) },
                        contactFailed = contactFailed, preparingContact = preparingContact, onMessage = { product ->
                            if (!preparingContact) {
                                preparingContact = true; contactFailed = false
                                scope.launch {
                                    try {
                                        nl.davidengelen.redsquare.chat.retainChatContact(wallet, product.seller, onWalletMetadata)
                                        reference = product.title + "\n" + StoreLink(backend, signature = product.signature).url()
                                        contact = product.seller
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { contactFailed = true }
                                    finally { preparingContact = false }
                                }
                            }
                        }, onNodeSettings = onNodeSettings)
                }
            } else states.SaveableStateProvider(target.url()) {
                StoreCatalogueScreen(wallet, backend, endpoint, Modifier.fillMaxSize(), chrome, target,
                    if (canBack) ({ back() }) else null, { open(it) }, onNodeSettings)
            }
        }
    }
}

private enum class StoreTab(override val label: Int, override val icon: Int) : ModuleDestination {
    DISCOVER(R.string.store_discover, R.drawable.ic_globe),
    ORDERS(R.string.store_orders, R.drawable.ic_article),
    MINE(R.string.store_my_store, R.drawable.ic_store);
    override val caption get() = label
}
private enum class StoreReadProblem { CONNECTION, MISSING_SERVICE }
private class StoreServiceMissing : IOException()
private data class StoreBrowseView(val query: StoreQuery? = null, val page: StorePage? = null,
    val loading: Boolean = false, val problem: StoreReadProblem? = null)

/** Selected-node public reading. Each replacement owns its result; cancellation never clears a newer load. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCatalogueScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, modifier: Modifier,
    chrome: @Composable (Boolean) -> Unit, target: StoreLink,
    onBack: (() -> Unit)?, onOpen: (StoreLink) -> Unit, onNodeSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(StoreTab.DISCOVER) }
    var category by rememberSaveable { mutableStateOf<StoreCategory?>(StoreCategory.entries.find { it.wire == target.category }) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var stock by rememberSaveable { mutableStateOf(StoreStock.ACTIVE) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val seller = if (tab == StoreTab.MINE) wallet.publicKey else target.seller
    val query = StoreQuery(seller, category, offset, stock = if (seller != null) stock else StoreStock.ACTIVE)
    var loaded by remember(wallet.publicKey, backend, endpoint) { mutableStateOf(StoreBrowseView()) }
    LaunchedEffect(wallet.publicKey, backend, endpoint, query, tab, retry) {
        if (tab == StoreTab.ORDERS) return@LaunchedEffect
        val previous = loaded.page.takeIf { loaded.query == query }
        loaded = StoreBrowseView(query, previous, loading = true)
        try {
            val page = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    if ("Store" !in peer.services()) throw StoreServiceMissing()
                    val response = peer.request(SaitoStore.catalogueRequest(query).toByteArray())
                    SaitoStore.readCatalogue(response.decodeToString(), query)
                }
            }
            currentCoroutineContext().ensureActive()
            loaded = StoreBrowseView(query, page)
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            loaded = StoreBrowseView(query, previous, problem = if (failure is StoreServiceMissing)
                StoreReadProblem.MISSING_SERVICE else StoreReadProblem.CONNECTION)
        }
    }
    val view = loaded.takeIf { it.query == query } ?: StoreBrowseView(query, loading = true)
    fun selectCategory(next: StoreCategory?) {
        category = next; offset = 0; categoriesOpen = false
        scope.launch { list.scrollToItem(0) }
    }
    val filters: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seller != null) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(registeredIdentity(seller).name, style = MaterialTheme.typography.titleMedium)
                        Text(seller, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoreStock.entries.forEach { choice ->
                        FilterChip(selected = stock == choice, onClick = {
                            stock = choice; offset = 0; scope.launch { list.scrollToItem(0) }
                        }, label = { Text(stringResource(if (choice == StoreStock.ACTIVE) R.string.store_stock_active else R.string.store_stock_sold)) })
                    }
                }
            }
            OutlinedButton(onClick = { categoriesOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(category?.let { stringResource(it.caption) } ?: stringResource(R.string.store_all_categories))
                Spacer(Modifier.width(8.dp)); Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
            }
        }
    }
    Column(modifier) {
        chrome(list.firstVisibleItemIndex > 0)
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (onBack != null) FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back)) { onBack() }
            Text(if (target.seller != null && tab == StoreTab.DISCOVER) stringResource(R.string.store_seller_store) else stringResource(tab.label), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (tab != StoreTab.ORDERS) FrameIconButton(R.drawable.ic_refresh, stringResource(R.string.store_refresh), !view.loading) { retry++ }
        }
        if (tab == StoreTab.ORDERS) {
            Column(Modifier.weight(1f).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.store_orders_unavailable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.store_orders_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
            InlineStatus(if (view.loading) stringResource(R.string.store_loading) else null, StatusTone.WORKING)
            InlineStatus(view.problem?.let { stringResource(if (it == StoreReadProblem.MISSING_SERVICE)
                R.string.store_service_unavailable else R.string.store_load_failed) },
                if (view.problem == StoreReadProblem.MISSING_SERVICE) StatusTone.NEUTRAL else StatusTone.ERROR,
                actionLabel = stringResource(if (view.problem == StoreReadProblem.MISSING_SERVICE) R.string.vault_choose_node else R.string.retry),
                onAction = { if (view.problem == StoreReadProblem.MISSING_SERVICE) onNodeSettings() else retry++ })
            InlineStatus(if ((view.page?.ignored ?: 0) > 0) stringResource(R.string.store_unreadable_items) else null, StatusTone.NEUTRAL)
            }
            SingleOwnerReveal(query to view.page?.items?.map { it.signature }, Modifier.weight(1f)) {
                // An initial one-row list would clamp a restored deep index before the real page arrives.
                if (view.page == null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.screen, vertical = 12.dp)) { filters() }
                else LazyColumn(Modifier.fillMaxSize(), state = list,
                    contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item("filters") { filters() }
                    val page = view.page
                    if (page != null && page.items.isEmpty() && !view.loading && view.problem == null) item("empty") {
                        Text(stringResource(if (page.ignored > 0) R.string.store_unreadable_items else R.string.store_empty),
                            Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(page?.items.orEmpty(), key = { it.signature }) { offer -> StoreOfferCard(offer) {
                        onOpen(StoreLink(backend, signature = offer.signature))
                    } }
                    if (page != null && (page.nextOffset != null || page.offset > 0)) item("pages") {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (page.offset > 0) OutlinedButton(enabled = !view.loading, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                                offset = (page.offset - 24).coerceAtLeast(0); scope.launch { list.scrollToItem(0) }
                            }) { Text(stringResource(R.string.store_previous)) }
                            page.nextOffset?.let { next -> Button(enabled = !view.loading, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                                offset = next; scope.launch { list.scrollToItem(0) }
                            }) { Text(stringResource(R.string.store_more)) } }
                        }
                    }
                }
            }
        }
        ModuleNavigation(StoreTab.entries, tab, enabled = true) { next ->
            if (next != tab) { tab = next; offset = 0; scope.launch { list.scrollToItem(0) } }
        }
    }
    DesignSheet(categoriesOpen, stringResource(R.string.store_categories), { categoriesOpen = false }) { firstAction ->
        TextButton(onClick = { selectCategory(null) }, modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = category == null }) {
            Text(stringResource(R.string.store_all_categories))
        }
        StoreCategory.entries.forEach { choice ->
            val label = stringResource(choice.caption)
            val accessible = stringResource(R.string.store_filter_category, label)
            TextButton(onClick = { selectCategory(choice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = accessible; selected = category == choice }) { Text(label) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreOfferCard(offer: StoreSummary, onClick: () -> Unit) {
    Surface(onClick = onClick,shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(offer.category.caption), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(offer.title.ifBlank { stringResource(R.string.store_untitled) }, style = MaterialTheme.typography.titleLarge)
            Text(registeredIdentity(offer.seller).name, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(offer.price?.let { "${it.amount.stripTrailingZeros().toPlainString()} ${it.currency}" }
                    ?: stringResource(R.string.store_price_unavailable), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.store_quantity_available, offer.available.toString()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal val StoreCategory.caption: Int get() = when (this) {
    StoreCategory.APPS -> R.string.store_category_apps
    StoreCategory.EXTENSIONS -> R.string.store_category_extensions
    StoreCategory.THEMES -> R.string.store_category_themes
    StoreCategory.IMAGES -> R.string.store_category_images
    StoreCategory.TOKENS -> R.string.store_category_tokens
    StoreCategory.ACCESS -> R.string.store_category_access
    StoreCategory.MERCHANDISE -> R.string.store_category_merchandise
    StoreCategory.OTHER -> R.string.store_category_other
}

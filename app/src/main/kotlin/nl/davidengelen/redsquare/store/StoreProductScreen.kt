package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

private data class StoreProductView(val loading: Boolean = true, val offer: StoreSummary? = null,
    val product: StoreProduct? = null, val unavailable: Boolean = false, val failed: Boolean = false,
    val missingService: Boolean = false)

/** The node reports stock. The original seller signature authenticates the displayed product terms. */
@Composable
internal fun StoreProductScreen(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, target: StoreLink,
    modifier: Modifier, chrome: @Composable (Boolean) -> Unit, onBack: () -> Unit, onSeller: (String) -> Unit,
    purchases: StorePurchases, contactFailed: Boolean, preparingContact: Boolean, onMessage: (StoreProduct) -> Unit, onNodeSettings: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var view by remember(wallet.publicKey, backend, endpoint, target) { mutableStateOf(StoreProductView()) }
    val list = rememberLazyListState()
    var identityDetails by remember { mutableStateOf(false) }
    LaunchedEffect(wallet.publicKey, backend, endpoint, target, retry) {
        view = view.copy(loading = true, failed = false, missingService = false)
        try {
            val next = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val services = peer.services()
                    if ("Store" !in services) return@use StoreProductView(loading = false, missingService = true)
                    val offer = SaitoStore.readOffer(peer.request(SaitoStore.listingRequest(target).toByteArray()).decodeToString(), target)
                    val signature = offer?.signature ?: target.signature
                    val product = if ("archive" in services && signature != null) {
                        readStoreProduct(peer, signature, offer)
                    } else null
                    StoreProductView(loading = false, offer = offer, product = product, unavailable = offer == null)
                }
            }
            currentCoroutineContext().ensureActive(); view = next
        } catch (_: Exception) { currentCoroutineContext().ensureActive(); view = view.copy(loading = false, failed = true) }
    }
    val external = LocalExternalUriHandler.current
    Column(modifier) {
        chrome(list.firstVisibleItemIndex > 0)
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back), onClick = onBack)
            Text(stringResource(R.string.store_product), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            FrameIconButton(R.drawable.ic_refresh, stringResource(R.string.store_refresh), !view.loading) { retry++ }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
            InlineStatus(if (view.loading) stringResource(R.string.store_loading_product) else null, StatusTone.WORKING)
            InlineStatus(if (view.failed) stringResource(R.string.store_load_failed) else null, StatusTone.ERROR,
                actionLabel = stringResource(R.string.retry), onAction = { retry++ })
            InlineStatus(if (view.missingService) stringResource(R.string.store_service_unavailable) else null, StatusTone.NEUTRAL,
                actionLabel = stringResource(R.string.vault_choose_node), onAction = onNodeSettings)
            if (purchases.review == null && purchases.focused == null) StorePurchaseProblem(purchases)
            InlineStatus(if (view.unavailable) stringResource(R.string.store_offer_unavailable) else null, StatusTone.NEUTRAL)
        }
        SingleOwnerReveal(view, Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(AppSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val offer = view.offer
                val product = view.product
                val seller = product?.seller ?: offer?.seller
                val signature = product?.signature ?: offer?.signature
                if (seller != null && signature != null) {
                    product?.image?.let { source -> item("image") {
                        nl.davidengelen.redsquare.feed.PostImages(listOf(source), backend)
                    } }
                    item("identity") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource((product?.category ?: requireNotNull(offer).category).caption), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text((product?.title ?: requireNotNull(offer).title).ifBlank { stringResource(R.string.store_untitled) },
                                style = MaterialTheme.typography.headlineMedium)
                            TextButton(onClick = { onSeller(seller) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.store_by_seller, registeredIdentity(seller).name))
                            }
                            val price = if (product != null) product.price else offer?.price
                            Text(price?.let { "${it.amount.stripTrailingZeros().toPlainString()} ${it.currency}" }
                                ?: stringResource(R.string.store_price_unavailable), style = MaterialTheme.typography.headlineSmall)
                            if (offer != null) {
                                Text(stringResource(R.string.store_quantity_available, offer.available.toString()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.store_stock_report, backend.domain), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (product != null) Text(stringResource(R.string.store_original_quantity, product.quantity.toString()),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item("description") {
                        Text(product?.description ?: requireNotNull(offer).description, style = MaterialTheme.typography.bodyLarge)
                    }
                    item("actions") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (product == null) InlineStatus(stringResource(R.string.store_original_unavailable), StatusTone.NEUTRAL)
                            if (product?.browserOnly == true) Text(stringResource(R.string.store_browser_compatibility),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (product != null && offer != null && product.price?.matches(offer.price) == true) StoreBuyControl(product, offer.available, purchases)
                            InlineStatus(if (contactFailed) stringResource(R.string.store_contact_failed) else null, StatusTone.ERROR)
                            Button(enabled = product != null && !preparingContact && product.seller != wallet.publicKey,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { product?.let(onMessage) }) {
                                Text(stringResource(if (preparingContact) R.string.store_opening_chat else R.string.store_message_seller))
                            }
                            if (external != null) OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                onClick = { external.openUri(target.url()) }) { Text(stringResource(R.string.store_open_website)) }
                        }
                    }
                    item("reference") {
                        TextButton(onClick = { identityDetails = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_info), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.store_offer_details))
                        }
                    }
                }
            }
        }
    }
    val reference = view.product?.let { it.signature to it.seller } ?: view.offer?.let { it.signature to it.seller }
    if (reference != null) DesignSheet(identityDetails, stringResource(R.string.store_offer_details), { identityDetails = false }) { firstAction ->
        StoreOfferIdentity(reference.first, reference.second)
        TextButton(onClick = { identityDetails = false }, modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.done))
        }
    }

}

@Composable
private fun StoreOfferIdentity(signature: String, seller: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.store_offer_reference), style = MaterialTheme.typography.titleSmall)
            Text(signature, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.store_seller_key), style = MaterialTheme.typography.titleSmall)
            Text(seller, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

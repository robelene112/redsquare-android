package nl.davidengelen.redsquare.store

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.*
import nl.davidengelen.redsquare.vault.*

@Composable
internal fun StoreSellerScreen(wallet: SaitoWallet, backend: SaitoBackend, selected: SaitoBackend, endpoint: String,
    modifier: Modifier, onOpen: (StoreLink) -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val balances = remember(activity) { WalletBalances.fromIntent(activity.intent) }
    val selling = remember(wallet.publicKey, backend, selected, endpoint) { StoreSelling(activity, wallet, backend, selected, endpoint, balances, scope) }
    val drafts = remember(wallet.publicKey, backend) { StoreSaleDraftStore(activity, wallet.publicKey, backend) }
    var draft by remember(wallet.publicKey, backend) { mutableStateOf(StoreSaleDraft()) }
    var loaded by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    var draftVersion by remember { mutableLongStateOf(0) }
    var choosingCurrency by rememberSaveable(wallet.publicKey, backend.id) { mutableStateOf(false) }
    var showingProceeds by remember(wallet.publicKey, backend) { mutableStateOf(false) }
    var editing by rememberSaveable(wallet.publicKey, backend.id) { mutableStateOf(false) }
    var pickingImage by rememberSaveable { mutableStateOf(false) }
    var uploading by rememberSaveable(wallet.publicKey, backend.id) { mutableStateOf(false) }
    val uploads = remember(wallet.publicKey, backend, endpoint) { VaultUploads.session(activity, wallet, backend, endpoint, balances) }
    val upload by uploads.state.collectAsState()
    val auth = remember(activity, uploads) { VaultUploadAuthorization(activity, uploads) }
    DisposableEffect(selling, auth) { onDispose { auth.close(); selling.dispose() } }
    LaunchedEffect(selling) { selling.load(); selling.discoverPricing(); uploads.load() }
    LaunchedEffect(drafts) {
        try { draft = withContext(Dispatchers.IO) { drafts.read() }; loaded = true; editing = editing || draft.creating; uploading = uploading || draft.creating }
        catch (_: Exception) { storageError = true }
    }
    fun update(next: StoreSaleDraft) {
        draft = next; val version = ++draftVersion
        scope.launch {
            try { withContext(Dispatchers.IO) {
                synchronized(SaitoLocalState.lock) {
                    if (draftVersion == version) {
                        check(AndroidWalletStore(activity).loadExisting().publicKey == wallet.publicKey && AndroidBackendStore(activity).load() == selected)
                        drafts.save(next)
                    }
                }
            }; if (draftVersion == version) storageError = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (draftVersion == version) storageError = true }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { update(draft.copy(creating = true, image = pickingImage)); uploading = true; uploads.prepare(uri) }
    }
    val focused = selling.focused
    val removal = selling.focusedDelist
    val image = selling.focusedImage
    fun token(item: StoreSelling.Inventory) = item.holding.id + ":" + item.holding.creator + ":" + item.holding.witnesses[1]
    val stock = selling.inventory.firstOrNull { token(it) == draft.item }
    DesignSheet(choosingCurrency, stringResource(R.string.store_sell_currency_choose), { choosingCurrency = false }) { firstAction ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selling.pricingLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            InlineStatus(if (selling.pricingUnavailable) stringResource(R.string.store_sell_currencies_unavailable) else null, StatusTone.NEUTRAL)
            val currencies = listOf("SAITO") + selling.pricing?.methods?.currencies.orEmpty().map { it.ticker }
            currencies.distinct().forEachIndexed { index, currency ->
                OutlinedButton(onClick = { update(draft.copy(currency = currency)); choosingCurrency = false },
                    modifier = (if (index == 0) firstAction else Modifier).fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(currency)
                }
            }
            if (selling.pricing?.enabled == false) Text(stringResource(R.string.store_sell_external_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { choosingCurrency = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
        }
    }
    SingleOwnerReveal(Triple(if (showingProceeds) "proceeds" else image?.mint?.transaction?.id ?: removal?.delist?.transaction?.id ?: focused?.listing?.transaction?.id, editing, uploading), modifier) {
        when {
            showingProceeds -> StoreProceedsScreen(wallet, backend, selected, endpoint, Modifier, { showingProceeds = false })
            image != null -> StoreImageReview(selling, image, Modifier.fillMaxSize()) {
                update(draft.copy(item = image.mint.nftId + ":" + wallet.publicKey, creating = false, image = false))
                uploads.discard(); selling.closeImage(); editing = true; uploading = false; selling.load()
            }
            removal != null -> StoreDelistReview(selling, removal, Modifier.fillMaxSize())
            uploading && draft.image -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.store_image_prepare), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.store_image_public))
                Text(stringResource(R.string.store_image_limit))
                upload.file?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) }
                InlineStatus(upload.problem?.let { stringResource(it) } ?: selling.problem?.let { stringResource(it) }, StatusTone.ERROR)
                if (upload.phase == VaultUploadPhase.PREPARING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = uploads::cancelPreparation, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.vault_cancel_reading)) }
                }
                Button(enabled = !selling.busy && upload.phase == VaultUploadPhase.REVIEW && upload.record == null && upload.file != null,
                    onClick = { selling.prepareImage(draft.title.ifBlank { upload.file?.name.orEmpty() }, draft.description) { update(draft.copy(creating = false)); uploading = false; editing = false } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_image_review)) }
                TextButton(onClick = { update(draft.copy(creating = false)); uploading = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_back_to_listing)) }
            }
            uploading -> VaultUploadReview(upload, Modifier.fillMaxSize(), uploads::discard, uploads::cancelPreparation,
                auth::request, uploads::cancelSending, uploads::checkOwnership,
                onBack = {
                    val keyId = upload.record?.candidate?.nftId
                    if (keyId != null) {
                        update(draft.copy(item = keyId + ":" + wallet.publicKey, creating = false)); uploads.backToFiles(); uploading = false; selling.load()
                    }
                }, onLibrary = { uploading = false }, creator = wallet.publicKey, onRules = uploads::changeRules,
                completeLabel = R.string.store_sell_back_form, libraryLabel = R.string.store_sell_back_to_listing)
            focused != null -> StoreSaleReview(selling, focused, Modifier.fillMaxSize())
            editing -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.store_sell_new), style = MaterialTheme.typography.titleLarge)
                InlineStatus(selling.problem?.let { stringResource(it) }, if (selling.problem == R.string.store_sell_submission_unknown) StatusTone.NEUTRAL else StatusTone.ERROR)
                InlineStatus(if (storageError) stringResource(R.string.store_sell_storage) else null, StatusTone.ERROR)
                OutlinedTextField(draft.title, { if (it.length <= 10_000) update(draft.copy(title = it)) }, label = { Text(stringResource(R.string.store_sell_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.description, { if (it.length <= 200_000) update(draft.copy(description = it)) }, label = { Text(stringResource(R.string.store_sell_description)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedButton(onClick = { keyboard?.hide(); choosingCurrency = true; selling.discoverPricing() }, enabled = !selling.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    SingleOwnerReveal(draft.currency) { Text(stringResource(R.string.store_sell_currency_button, draft.currency)) }
                }
                InlineStatus(if (draft.currency != "SAITO" && selling.pricingUnavailable) stringResource(R.string.store_sell_currencies_unavailable) else null, StatusTone.NEUTRAL)
                OutlinedTextField(draft.price, { if (it.length <= 80) update(draft.copy(price = it)) }, label = { Text(stringResource(R.string.store_sell_price, draft.currency)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.quantity, { if (it.length <= 20) update(draft.copy(quantity = it)) }, label = { Text(stringResource(R.string.store_purchase_quantity)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.store_sell_inventory), style = MaterialTheme.typography.titleMedium)
                if (selling.busy || selling.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                selling.inventory.forEach { item ->
                    val eligible = SaitoStoreListing.sellable(item.holding) && item.hasMetadata
                    OutlinedButton(enabled = eligible && !selling.busy, onClick = { update(draft.copy(item = token(item))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title)
                            Text(stringResource(if (!SaitoStoreListing.sellable(item.holding)) R.string.store_sell_rental_only else if (!item.hasMetadata) R.string.store_sell_metadata_unavailable else R.string.store_sell_owned, item.holding.quantity.toString()), style = MaterialTheme.typography.bodySmall)
                            if (draft.item == token(item) || draft.item == item.holding.id + ":" + item.holding.creator) Text(stringResource(R.string.store_sell_selected))
                        }
                    }
                }
                if (selling.inventoryPage > 0) TextButton(onClick = { selling.load(selling.inventoryPage - 1) }, enabled = !selling.busy && !selling.loading) { Text(stringResource(R.string.store_previous)) }
                if (selling.moreInventory) TextButton(onClick = { selling.load(selling.inventoryPage + 1) }, enabled = !selling.busy && !selling.loading) { Text(stringResource(R.string.store_more)) }
                OutlinedButton(onClick = { if (upload.phase == VaultUploadPhase.FILES) { pickingImage = false; picker.launch(arrayOf("*/*")) } else uploading = true }, enabled = loaded && selling.localReady && !storageError && !selling.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_create_file)) }
                OutlinedButton(onClick = { pickingImage = true; picker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")) },
                    enabled = loaded && selling.localReady && !storageError && !selling.busy && upload.phase == VaultUploadPhase.FILES,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_image_create)) }
                val chosen = stock ?: selling.inventory.firstOrNull { it.holding.id + ":" + it.holding.creator == draft.item }
                val terms = runCatching { StoreListingTerms(draft.title, draft.description, chosen?.let { StoreCategory.forType(it.holding.type) } ?: StoreCategory.OTHER,
                    draft.price, draft.quantity.toBigInteger(), draft.currency) }.getOrNull()
                Button(enabled = loaded && selling.localReady && !storageError && !selling.busy && chosen?.hasMetadata == true && terms != null &&
                    (terms.currency == "SAITO" || selling.pricing?.methods?.currencies.orEmpty().any { it.ticker == terms.currency }),
                    onClick = { keyboard?.hide(); selling.prepare(requireNotNull(chosen), requireNotNull(terms)) { editing = false } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_review)) }
                TextButton(onClick = { editing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)) {
                    InlineStatus(selling.problem?.let { stringResource(it) }, StatusTone.ERROR)
                    InlineStatus(if (storageError) stringResource(R.string.store_sell_storage) else null, StatusTone.ERROR)
                    if (selling.busy || selling.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Button(enabled = loaded && selling.localReady && !storageError && !selling.busy, onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_new)) }
                    OutlinedButton(onClick = { showingProceeds = true }, enabled = !selling.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_proceeds_title)) }
                    TextButton(onClick = { selling.load() }, enabled = !selling.busy && !selling.loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.store_refresh)) }
                }
                items(selling.images.filter { it.backend == backend }, key = { "image:" + it.mint.transaction.id }) { record ->
                    OutlinedCard(onClick = { selling.openImage(record) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.mint.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (selling.records.any { it.backend == record.backend && it.listing.holding.id == record.mint.nftId && it.listing.holding.creator == record.mint.author }) R.string.store_image_offered else if (record.mint.transaction.id in selling.ownedImages) R.string.store_image_owned else if (record.mayHaveSent) R.string.store_image_waiting else R.string.store_image_review))
                        }
                    }
                }
                items(selling.delistRecords.filter { it.backend == backend }, key = { "delist:" + it.delist.transaction.id }) { record ->
                    OutlinedCard(onClick = { selling.openDelist(record) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.delist.product.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (selling.delistObservations[record.delist.transaction.id]?.returned == true) R.string.store_delist_returned
                                else if (record.lastObservation != null) R.string.store_delist_historical else if (record.mayHaveSent) R.string.store_delist_waiting else R.string.store_delist_review))
                        }
                    }
                }
                items(selling.records.filter { it.backend == backend && selling.reported.none { row -> row.summary.signature == it.listing.transaction.id } }, key = { "local:" + it.listing.transaction.id }) { record ->
                    OutlinedCard(onClick = { selling.open(record) }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(record.listing.terms.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (record.mayHaveSent) R.string.store_sell_waiting else R.string.store_sell_not_sent))
                        }
                    }
                }
                items(selling.reported, key = { "remote:" + it.summary.signature }) { item ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.summary.title, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (item.sold) R.string.store_sell_sold else when (item.approval) { StoreApproval.PENDING -> R.string.store_sell_pending; StoreApproval.REJECTED -> R.string.store_sell_declined; StoreApproval.APPROVED -> R.string.store_sell_approved; StoreApproval.NOT_SUBMITTED -> R.string.store_sell_unapproved }))
                            item.summary.price?.let { price -> Text(stringResource(R.string.store_sell_currency_price, price.amount.stripTrailingZeros().toPlainString(), price.currency)) }
                            Text(stringResource(if (item.sold) R.string.store_sell_sale_quantity else R.string.store_sell_stock_count,
                                if (item.sold) item.quantitySold.toString() else item.summary.available.toString(), item.summary.total.toString()))
                            if (item.sold && item.buyer.isNotBlank()) Text(stringResource(R.string.store_sell_sale_buyer, registeredIdentity(item.buyer).name))
                            if (item.risk.isNotBlank()) Text(item.risk)
                            if (item.note.isNotBlank()) Text(item.note)
                            TextButton(onClick = { onOpen(StoreLink(backend, signature = item.summary.signature)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_open)) }
                            if (!item.sold && item.summary.price != null) {
                                OutlinedButton(onClick = { selling.prepareDelist(item) }, enabled = !selling.busy,
                                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.store_delist_action)) }
                                Text(stringResource(R.string.store_sell_edit_explanation), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!item.sold && item.approval in setOf(StoreApproval.NOT_SUBMITTED, StoreApproval.REJECTED)) OutlinedButton(onClick = { selling.submitMarketplace(item) }, enabled = !selling.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_marketplace)) }
                        }
                    }
                }
            }
            }
        }
    }
}

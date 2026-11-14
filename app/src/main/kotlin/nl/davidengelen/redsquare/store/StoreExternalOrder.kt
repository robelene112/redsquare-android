package nl.davidengelen.redsquare.store

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.SingleOwnerReveal

@Composable
internal fun StoreExternalReview(review: StorePurchases.CryptoReview, purchases: StorePurchases, firstAction: Modifier) {
    Text(review.product.title, style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.store_crypto_estimate, review.deposit.stripTrailingZeros().toPlainString(), review.currency.ticker),
        style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.store_crypto_network, review.network))
    Text(stringResource(R.string.store_purchase_quantity_review, review.quantity.toString()))
    val price = requireNotNull(review.product.price)
    Text(stringResource(R.string.store_crypto_original_item, (price.amount * review.quantity.toBigDecimal()).stripTrailingZeros().toPlainString(), price.currency))
    Text(stringResource(R.string.store_purchase_store_fee, "0 SAITO"))
    Text(stringResource(R.string.store_crypto_fees), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.store_crypto_review_terms), color = MaterialTheme.colorScheme.onSurfaceVariant)
    StorePurchaseProblem(purchases)
    Button(onClick = { purchases.authorize() }, enabled = !purchases.busy,
        modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_crypto_create)) }
    TextButton(onClick = { purchases.closeReview() }, enabled = !purchases.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.done))
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.store_purchase_store_destination)); Text(review.store, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.store_purchase_node, purchases.backend.domain))
        }
    }
}

@Composable
internal fun StoreExternalOrder(order: StoreOrderStore.Order, purchases: StorePurchases, firstAction: Modifier) {
    val external = requireNotNull(order.external)
    val invoice = external.invoice
    val context = LocalContext.current
    var now by remember(order.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var copyStatus by rememberSaveable(order.id) { mutableIntStateOf(0) }
    LaunchedEffect(order.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Text(order.product.title, style = MaterialTheme.typography.headlineSmall)
    if (invoice != null) Text(stringResource(R.string.store_crypto_total, invoice.amount.stripTrailingZeros().toPlainString(), external.currency.ticker),
        style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.store_crypto_network, external.network))
    Text(stringResource(R.string.store_purchase_quantity_review, external.carrier.quantity.toString()))
    Text(stringResource(R.string.store_crypto_original_item, external.carrier.total.stripTrailingZeros().toPlainString(), external.carrier.price.currency))
    val observation = purchases.observations[order.id]
    val status = when {
        observation?.payment != null || observation?.receipt != null || order.payment != null || order.receipt != null -> orderStatus(order, observation)
        external.hidden -> R.string.store_crypto_closed
        invoice == null -> R.string.store_crypto_waiting_instructions
        invoice.state in setOf(SaitoStoreCrypto.TransferState.FAILED, SaitoStoreCrypto.TransferState.REJECTED) -> R.string.store_crypto_provider_problem
        invoice.state == SaitoStoreCrypto.TransferState.CANCELLED -> R.string.store_crypto_closed
        invoice.state != SaitoStoreCrypto.TransferState.AWAITING -> R.string.store_crypto_wait_settlement
        !invoice.payable(now) -> R.string.store_crypto_expired
        else -> R.string.store_crypto_awaiting
    }
    SingleOwnerReveal(status) { Text(stringResource(status), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    order.receipt?.takeIf { it.kind == SaitoStoreReceipt.Kind.REFUND }?.let {
        Text(stringResource(R.string.store_crypto_refund, storeAmount(requireNotNull(order.fundedPayment).amount)))
    }
    StorePurchaseProblem(purchases)
    Button(onClick = { purchases.check(order) }, enabled = !purchases.busy && !purchases.refreshing,
        modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_check)) }
    if (invoice == null && order.fundedPayment == null) OutlinedButton(onClick = { purchases.authorize(order) }, enabled = !purchases.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_crypto_retry)) }
    if (invoice?.payable(now) == true && !external.hidden && order.payment == null && order.receipt == null) {
        Text(stringResource(R.string.store_crypto_fees), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.store_crypto_valid_until, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT).format(java.util.Date(invoice.expiresAt))))
        Text(stringResource(R.string.store_crypto_address, external.currency.ticker, external.network), style = MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(invoice.address, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = {
            copyStatus = try {
                check(invoice.payable(System.currentTimeMillis()) && !external.hidden)
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(external.currency.ticker, invoice.address))
                1
            } catch (_: Exception) { 2 }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            SingleOwnerReveal(copyStatus) {
                Text(stringResource(when (copyStatus) { 1 -> R.string.wallet_address_copied; 2 -> R.string.wallet_address_copy_failed; else -> R.string.store_crypto_copy }))
            }
        }
        PaymentAddressQr(invoice.address)
        Text(stringResource(R.string.store_crypto_cannot_recall), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { purchases.hidePayment(order) }, enabled = !purchases.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.store_crypto_close))
        }
    } else if (external.hidden && invoice?.payable(now) == true) {
        TextButton(onClick = { purchases.hidePayment(order, false) }, enabled = !purchases.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.store_crypto_show))
        }
    }
    TextButton(onClick = { purchases.closeReview() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.store_purchase_order_reference)); Text(order.id, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.store_purchase_store_destination)); Text(external.carrier.store, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.store_purchase_node, purchases.backend.domain))
        }
    }
}

@Composable
private fun PaymentAddressQr(address: String) {
    val pixels = with(LocalDensity.current) { 192.dp.roundToPx() }
    key(address, pixels) {
        val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) {
            value = withContext(Dispatchers.Default) {
                try {
                    val matrix = QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, pixels, pixels, mapOf(EncodeHintType.MARGIN to 4))
                    Bitmap.createBitmap(IntArray(pixels * pixels) { index -> if (matrix[index % pixels, index / pixels])
                        android.graphics.Color.BLACK else android.graphics.Color.WHITE }, pixels, pixels, Bitmap.Config.ARGB_8888).asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
        image?.let { bitmap -> Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Image(bitmap, stringResource(R.string.store_crypto_qr), Modifier.size(192.dp), filterQuality = FilterQuality.None)
        } }
    }
}

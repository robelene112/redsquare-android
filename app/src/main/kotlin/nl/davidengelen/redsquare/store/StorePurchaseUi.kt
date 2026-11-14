package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*
import java.math.BigDecimal
import java.math.BigInteger

internal fun storeAmount(nolan: BigInteger) = BigDecimal(nolan, 8).stripTrailingZeros().toPlainString() + " SAITO"

@Composable
internal fun StoreBuyControl(product: StoreProduct, available: BigInteger, purchases: StorePurchases) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var quantity by rememberSaveable(product.signature) { mutableStateOf("1") }
    var fee by rememberSaveable(product.signature) { mutableStateOf("0") }
    var feesOpen by rememberSaveable { mutableStateOf(false) }
    if (product.price?.currency != "SAITO") return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(quantity, { quantity = it.take(16) }, label = { Text(stringResource(R.string.store_purchase_quantity)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !purchases.busy)
        TextButton(onClick = { feesOpen = !feesOpen }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.store_purchase_network_fee, "$fee SAITO"))
        }
        if (feesOpen) OutlinedTextField(fee, { fee = it.take(30) }, label = { Text(stringResource(R.string.transfer_fee)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !purchases.busy)
        Button(onClick = { keyboard?.hide(); purchases.prepare(product, quantity, fee) }, enabled = !purchases.busy && available.signum() > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_review)) }
    }
}

@Composable
internal fun StoreOrders(purchases: StorePurchases, modifier: Modifier) {
    LaunchedEffect(purchases) { purchases.refresh() }
    LaunchedEffect(purchases, purchases.orders.map { it.id }) {
        while (true) { kotlinx.coroutines.delay(5000); if (!purchases.busy) purchases.refresh() }
    }
    DisposableEffect(purchases) { onDispose { purchases.stopReading() } }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.store_purchase_history), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            FrameIconButton(R.drawable.ic_refresh, stringResource(R.string.store_refresh), !purchases.busy && !purchases.refreshing) { purchases.refresh() }
        }
        StorePurchaseProblem(purchases)
        InlineStatus(if (purchases.refreshing) stringResource(R.string.store_purchase_working) else null, StatusTone.WORKING)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val rows = purchases.orders.filter { it.backend == purchases.backend }
            if (rows.isEmpty()) item { Text(stringResource(R.string.store_purchase_no_orders), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(rows, key = { it.id }) { order ->
                Card(onClick = { purchases.open(order) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(order.product.title, style = MaterialTheme.typography.titleMedium)
                        Text(order.external?.let { "${it.carrier.total.stripTrailingZeros().toPlainString()} ${it.carrier.price.currency}" } ?: storeAmount(requireNotNull(order.purchase).total), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(orderStatus(order, purchases.observations[order.id])))
                    }
                }
            }
        }
    }
}

private fun orderStatus(order: StoreOrderStore.Order, observation: SaitoStoreChain.Observation?): Int = when {
    observation?.delivered == true -> R.string.store_purchase_delivered
    observation?.refunded == true -> R.string.store_purchase_refunded
    observation?.historical == true && observation.receipt?.kind == SaitoStoreReceipt.Kind.REFUND -> R.string.store_purchase_previous_refund
    observation?.historical == true && observation.receipt != null -> R.string.store_purchase_previous_delivery
    observation?.receipt != null -> R.string.store_purchase_settled
    observation?.payment != null -> R.string.store_purchase_paid
    order.receipt?.kind == SaitoStoreReceipt.Kind.REFUND -> R.string.store_purchase_previous_refund
    order.receipt != null -> R.string.store_purchase_previous_delivery
    order.payment != null -> R.string.store_purchase_previous_observation
    order.mayHaveSent -> R.string.store_purchase_submitted
    else -> R.string.store_purchase_not_sent
}

@Composable
internal fun StorePurchaseDialog(purchases: StorePurchases) {
    val review = purchases.review
    val order = purchases.orders.firstOrNull { it.id == purchases.focused }
    val visible = review != null || order != null
    LaunchedEffect(purchases, order?.id) {
        if (order != null) while (true) {
            kotlinx.coroutines.delay(5000)
            if (!purchases.busy) purchases.refresh(order.id)
        }
    }
    DesignSheet(visible, stringResource(R.string.store_purchase_title), { purchases.closeReview() }) { firstAction ->
        val product = review?.product ?: order?.product
        if (product != null && order?.external != null) {
            StoreExternalOrder(order, purchases, firstAction)
        } else if (product != null) {
            Text(product.title, style = MaterialTheme.typography.headlineSmall)
            if (order == null) Text(product.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val quantity = review?.quantity ?: requireNotNull(requireNotNull(order).purchase).quantity
            val unit = review?.unit ?: requireNotNull(requireNotNull(order).purchase).unitPrice
            val fee = review?.fee ?: requireNotNull(requireNotNull(order).purchase).networkFee
            val total = review?.total ?: requireNotNull(requireNotNull(order).purchase).total
            Text(stringResource(R.string.store_purchase_quantity_review, quantity.toString()))
            Text(stringResource(R.string.store_purchase_unit, storeAmount(unit)))
            Text(stringResource(R.string.store_purchase_store_fee, "0 SAITO"))
            Text(stringResource(R.string.store_purchase_network_fee, storeAmount(fee)))
            Text(stringResource(R.string.store_purchase_total, storeAmount(total)), style = MaterialTheme.typography.titleLarge)
            if (order == null) {
                Text(stringResource(R.string.store_purchase_fulfillment_terms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.store_purchase_delivery_terms), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (order?.cursor != null) Text(stringResource(R.string.store_purchase_older_history, order.cursor.height.toString()),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            StorePurchaseProblem(purchases)
            InlineStatus(if (purchases.refreshing) stringResource(R.string.store_purchase_working) else null, StatusTone.WORKING)
            if (order != null) {
                val observation = purchases.observations[order.id]
                Text(stringResource(orderStatus(order, observation)), style = MaterialTheme.typography.titleMedium)
                if (order.receipt?.reason != null) Text(stringResource(R.string.store_purchase_refund_reason, requireNotNull(order.receipt.reason)))
                Button(onClick = { purchases.check(order) }, enabled = !purchases.busy && !purchases.refreshing,
                    modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_check)) }
                if (order.payment == null && order.receipt == null) OutlinedButton(onClick = { purchases.authorize(order) }, enabled = !purchases.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (order.mayHaveSent) R.string.store_purchase_retry else R.string.store_purchase_confirm))
                }
                if (!order.mayHaveSent) TextButton(onClick = { purchases.discard(order) }, enabled = !purchases.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_discard)) }
            } else Button(onClick = { purchases.authorize() }, enabled = !purchases.busy,
                modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_confirm)) }
            TextButton(onClick = { purchases.closeReview() }, enabled = !purchases.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
            if (order != null) {
                Text(product.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.store_purchase_fulfillment_terms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.store_purchase_delivery_terms), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.store_seller_key)); Text(product.seller, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.store_purchase_store_destination)); Text(review?.store ?: requireNotNull(requireNotNull(order).purchase).store, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.store_purchase_node, purchases.backend.domain))
                    Text(stringResource(R.string.store_offer_reference)); Text(product.signature, style = MaterialTheme.typography.bodySmall)
                    order?.let { Text(stringResource(R.string.store_purchase_order_reference)); Text(it.id, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
internal fun StorePurchaseProblem(purchases: StorePurchases) {
    val label = when (purchases.problem) {
        StorePurchases.Problem.CONNECTION -> R.string.store_purchase_connection
        StorePurchases.Problem.INSUFFICIENT -> R.string.store_purchase_insufficient
        StorePurchases.Problem.CONTEXT -> R.string.vault_key_context_changed
        StorePurchases.Problem.STORAGE -> R.string.store_purchase_storage
        StorePurchases.Problem.AUTH -> R.string.transfer_auth_setup
        StorePurchases.Problem.CANCELLED -> R.string.transfer_cancelled
        StorePurchases.Problem.TERMS -> R.string.store_purchase_terms_changed
        null -> null
    }
    InlineStatus(if (purchases.busy) stringResource(R.string.store_purchase_working) else label?.let { stringResource(it) },
        if (purchases.busy) StatusTone.WORKING else StatusTone.NEUTRAL)
}

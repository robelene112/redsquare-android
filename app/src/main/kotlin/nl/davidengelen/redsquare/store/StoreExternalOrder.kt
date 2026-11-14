package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.StoreOrderStore

@Composable
internal fun StoreExternalOrder(order: StoreOrderStore.Order, purchases: StorePurchases, firstAction: Modifier) {
    val external = requireNotNull(order.external)
    Text(order.product.title, style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.store_crypto_total, external.invoice?.amount?.stripTrailingZeros()?.toPlainString()
        ?: external.carrier.total.stripTrailingZeros().toPlainString(), external.currency.ticker), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.store_crypto_network, external.network))
    Text(stringResource(R.string.store_purchase_quantity_review, external.carrier.quantity.toString()))
    Text(stringResource(if (external.invoice == null) R.string.store_crypto_waiting_instructions else R.string.store_crypto_awaiting),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    StorePurchaseProblem(purchases)
    Button(onClick = { purchases.check(order) }, enabled = !purchases.busy && !purchases.refreshing,
        modifier = firstAction.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_purchase_check)) }
    TextButton(onClick = { purchases.closeReview() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.done))
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.store_purchase_order_reference))
            Text(order.id, style = MaterialTheme.typography.bodySmall)
        }
    }
}

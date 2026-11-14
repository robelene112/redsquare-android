package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.StoreDelistStore
import nl.davidengelen.redsquare.ui.*

@Composable
internal fun StoreDelistReview(selling: StoreSelling, record: StoreDelistStore.Record, modifier: Modifier) {
    val observation = selling.delistObservations[record.delist.transaction.id]
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.store_delist_review), style = MaterialTheme.typography.headlineSmall)
            Text(record.delist.product.title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.store_delist_quantity, record.delist.quantity.toString()))
            Text(stringResource(R.string.store_delist_explanation))
            Text(stringResource(R.string.vault_no_network_fee))
            Text(record.backend.webUrl, style = MaterialTheme.typography.bodySmall)
            Text(record.delist.transaction.id, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineStatus(selling.problem?.let { stringResource(it) }, if (selling.problem == R.string.store_sell_submission_unknown) StatusTone.NEUTRAL else StatusTone.ERROR)
            Text(stringResource(when {
                observation?.returned == true -> R.string.store_delist_returned
                observation != null -> R.string.store_delist_included
                record.mayHaveSent -> R.string.store_delist_waiting
                else -> R.string.store_sell_not_sent
            }), style = MaterialTheme.typography.bodySmall)
            if (observation == null) Button(onClick = { selling.authorizeDelist(record) }, enabled = !selling.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (record.mayHaveSent) R.string.store_delist_retry else R.string.store_delist_authorize)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = selling::closeDelist, enabled = !selling.busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                if (record.mayHaveSent) OutlinedButton(onClick = { selling.checkDelist(record) }, enabled = !selling.busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.store_delist_check)) }
                else TextButton(onClick = { selling.discardDelist(record) }, enabled = !selling.busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.store_delist_discard)) }
            }
        }
    }
}

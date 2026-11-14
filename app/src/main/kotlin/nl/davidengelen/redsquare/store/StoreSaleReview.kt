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
import nl.davidengelen.redsquare.saito.StoreListingStore
import nl.davidengelen.redsquare.ui.*

/** Financial actions stay reachable when submission feedback changes the amount of text. */
@Composable
internal fun StoreSaleReview(selling: StoreSelling, record: StoreListingStore.Record, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.store_sell_review), style = MaterialTheme.typography.headlineSmall)
            Text(record.listing.terms.title, style = MaterialTheme.typography.titleLarge)
            Text(record.listing.terms.description)
            Text(stringResource(R.string.store_sell_terms, record.listing.terms.quantity.toString(), record.listing.terms.price))
            Text(stringResource(R.string.store_sell_cost))
            Text(stringResource(R.string.store_sell_custody))
            Text(record.backend.webUrl, style = MaterialTheme.typography.bodySmall)
            Text(record.listing.transaction.id, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineStatus(selling.problem?.let { stringResource(it) }, if (selling.problem == R.string.store_sell_submission_unknown) StatusTone.NEUTRAL else StatusTone.ERROR)
            Text(stringResource(if (record.mayHaveSent) R.string.store_sell_waiting else R.string.store_sell_not_sent), style = MaterialTheme.typography.bodySmall)
            Button(enabled = !selling.busy, onClick = { selling.authorize(record) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (record.mayHaveSent) R.string.store_sell_retry_original else R.string.store_sell_publish))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !selling.busy, onClick = selling::closeReview, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                if (!record.mayHaveSent) TextButton(enabled = !selling.busy, onClick = { selling.discard(record) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_discard)) }
            }
        }
    }
}

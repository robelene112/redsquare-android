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
import nl.davidengelen.redsquare.saito.StoreImageStore
import nl.davidengelen.redsquare.ui.*

@Composable
internal fun StoreImageReview(selling: StoreSelling, record: StoreImageStore.Record, modifier: Modifier, onContinue: () -> Unit) {
    val owned = record.mint.transaction.id in selling.ownedImages
    val offer = selling.records.firstOrNull { it.backend == record.backend && it.listing.holding.id == record.mint.nftId && it.listing.holding.creator == record.mint.author }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.store_image_review), style = MaterialTheme.typography.headlineSmall)
            Text(record.mint.title, style = MaterialTheme.typography.titleLarge)
            nl.davidengelen.redsquare.feed.PostImages(listOf(record.mint.image), record.backend)
            Text(record.mint.description)
            Text(stringResource(R.string.store_image_public))
            Text(stringResource(R.string.store_image_deposit))
            Text(stringResource(R.string.vault_no_network_fee))
            Text(record.backend.webUrl, style = MaterialTheme.typography.bodySmall)
            Text(record.mint.transaction.id, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineStatus(selling.problem?.let { stringResource(it) }, if (selling.problem == R.string.store_sell_submission_unknown) StatusTone.NEUTRAL else StatusTone.ERROR)
            Text(stringResource(if (offer != null) R.string.store_image_offered else if (owned) R.string.store_image_owned else if (record.mayHaveSent) R.string.store_image_waiting else R.string.store_sell_not_sent), style = MaterialTheme.typography.bodySmall)
            if (offer != null) Button(onClick = { selling.closeImage(); selling.open(offer) }, enabled = !selling.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_open)) }
            else if (owned) Button(onClick = onContinue, enabled = !selling.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_sell_back_form)) }
            else {
                Button(onClick = { selling.authorizeImage(record) }, enabled = !selling.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (record.mayHaveSent) R.string.store_image_retry else R.string.store_image_authorize)) }
                if (record.mayHaveSent) OutlinedButton(onClick = { selling.checkImage(record) }, enabled = !selling.busy && !selling.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.store_image_check)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = selling::closeImage, enabled = !selling.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                if (!record.mayHaveSent) TextButton(onClick = { selling.discardImage(record) }, enabled = !selling.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.store_image_discard)) }
            }
        }
    }
}

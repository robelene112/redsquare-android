package nl.davidengelen.redsquare.store

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*

internal data class StoreRentalRead(val offer: SaitoStoreRental.Offer? = null, val loading: Boolean = true, val ended: Boolean = false) {
    val available: Boolean get() = offer != null && !loading && !ended && offer.path.grant == null
}

@Composable
internal fun rememberStoreRentalTerms(product: StoreProduct?): StoreRentalRead {
    val activity = androidx.compose.ui.platform.LocalContext.current
    val result by produceState(StoreRentalRead(), product) {
        value = StoreRentalRead()
        value = try {
            withContext(Dispatchers.Default) { product?.let(SaitoStoreRental::offer) }.let { offer -> StoreRentalRead(offer, false, offer?.let { rentalNowMillis(activity) >= it.expiresAt } == true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { StoreRentalRead(loading = false) }
        val expiry = value.offer?.expiresAt
        if (expiry != null && !value.ended) {
            kotlinx.coroutines.delay((expiry - rentalNowMillis(activity)).coerceAtLeast(1))
            value = value.copy(ended = true)
        }
    }
    return result
}

@Composable
internal fun StoreRentalTerms(read: StoreRentalRead) {
    SingleOwnerReveal(read) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.store_rental_temporary), style = MaterialTheme.typography.titleMedium)
        val offer = read.offer
        when {
            read.loading -> InlineStatus(stringResource(R.string.store_rental_checking), StatusTone.WORKING)
            read.ended -> InlineStatus(stringResource(R.string.store_rental_ended), StatusTone.NEUTRAL)
            offer?.path?.grant != null -> InlineStatus(stringResource(R.string.store_rental_assigned), StatusTone.NEUTRAL)
            offer == null -> InlineStatus(stringResource(R.string.store_rental_unverified), StatusTone.NEUTRAL)
            else -> {
                Text(stringResource(R.string.store_rental_duration, offer.source.hours))
                Text(stringResource(R.string.vault_rental_ends, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(offer.expiresAt))))
                Text(stringResource(R.string.store_rental_rights))
                Text(stringResource(R.string.store_rental_roles), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.store_rental_copies), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
}

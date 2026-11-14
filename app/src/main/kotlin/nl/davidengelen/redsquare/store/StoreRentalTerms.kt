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

internal data class StoreRentalRead(val offer: SaitoStoreRental.Offer? = null, val loading: Boolean = true)

@Composable
internal fun rememberStoreRentalTerms(product: StoreProduct?): StoreRentalRead {
    val result by produceState(StoreRentalRead(), product) {
        value = StoreRentalRead()
        value = try {
            StoreRentalRead(withContext(Dispatchers.Default) { product?.let(SaitoStoreRental::offer) }, false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { StoreRentalRead(loading = false) }
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

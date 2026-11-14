package nl.davidengelen.redsquare.store

import android.app.Activity
import nl.davidengelen.redsquare.BuildConfig

/** Local purchase availability only. Storage access remains evaluated by the provider's NOW. */
internal fun rentalNowMillis(activity: Activity): Long {
    // The native protocol fixtures deliberately use historical signed transactions.
    if (BuildConfig.DEBUG && activity.packageName.endsWith(".dev") && android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) {
        activity.intent.getLongExtra("storeRentalFixtureTime", 0L).takeIf { it > 0 }?.let { return it }
    }
    return System.currentTimeMillis()
}

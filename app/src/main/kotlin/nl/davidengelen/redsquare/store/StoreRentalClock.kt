package nl.davidengelen.redsquare.store

import android.app.Activity
import nl.davidengelen.redsquare.BuildConfig

/** Local purchase availability only. Storage access remains evaluated by the provider's NOW. */
internal fun rentalNowMillis(context: android.content.Context): Long {
    var current = context
    var activity: Activity? = null
    repeat(16) {
        if (current is Activity) { activity = current as Activity; return@repeat }
        current = (current as? android.content.ContextWrapper)?.baseContext ?: return System.currentTimeMillis()
    }
    // The native protocol fixtures deliberately use historical signed transactions.
    if (activity != null && BuildConfig.DEBUG && requireNotNull(activity).packageName.endsWith(".dev") && android.os.Build.HARDWARE in listOf("ranchu", "goldfish")) {
        requireNotNull(activity).intent.getLongExtra("storeRentalFixtureTime", 0L).takeIf { it > 0 }?.let { return it }
    }
    return System.currentTimeMillis()
}

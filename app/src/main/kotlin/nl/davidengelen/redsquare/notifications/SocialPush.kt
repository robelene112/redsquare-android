package nl.davidengelen.redsquare.notifications

import android.content.Context

/** Transport boundary for an opaque social wake-up reference, not a notification payload. */
internal object SocialPush {
    suspend fun receive(context: Context, payload: String, endpoint: String, now: Long = System.currentTimeMillis()) {
        // First native tracer deliberately has no publisher yet.
    }
}

internal class SocialPushSettings(context: Context, owner: String) {
    fun setEnabled(enabled: Boolean) {
        // Explicit app-side subscription choice is implemented with the receiver.
    }
}

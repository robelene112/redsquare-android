package nl.davidengelen.redsquare.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.BuildConfig

/** The gateway sends data only. Content and routing are resolved and verified by durable work. */
class RedSquareMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!PushTransport.initialize(applicationContext) || message.notification != null) return
        val data = message.data
        if (data.keys != setOf("version", "wake") || data["version"] != "1") return
        val wake = data["wake"]?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return
        try {
            // Wait for the durable enqueue acknowledgement, not for network/content processing.
            runBlocking { SocialPushWakeScheduling.enqueue(applicationContext, wake, BuildConfig.PUSH_RESOLVER) }
        } catch (_: Exception) {
            Log.w("PushTransport", "Push receipt scheduling unavailable")
        }
    }

    override fun onRegistered(installationId: String) {
        PushRegistration.observed(applicationContext, registered = true, fid = installationId)
    }

    override fun onUnregistered(installationId: String) {
        // Reconcile actual desired state instead of erasing a newer local registration.
        PushRegistration.observed(applicationContext, registered = false)
    }
}

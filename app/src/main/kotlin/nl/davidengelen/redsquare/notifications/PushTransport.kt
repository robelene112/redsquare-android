package nl.davidengelen.redsquare.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import nl.davidengelen.redsquare.BuildConfig

/** SDK initialization is independent of social consent; registration is explicitly opt-in. */
internal object PushTransport {
    fun initialize(context: Context): Boolean {
        if (!BuildConfig.PUSH_CONFIGURED) return false
        return try {
            synchronized(this) {
                if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
                    FirebaseApp.initializeApp(context.applicationContext, FirebaseOptions.Builder()
                        .setApplicationId(BuildConfig.PUSH_APP_ID).setApiKey(BuildConfig.PUSH_API_KEY)
                        .setProjectId(BuildConfig.PUSH_PROJECT_ID).setGcmSenderId(BuildConfig.PUSH_SENDER_ID).build())
                }
                // Runtime preferences can override manifest defaults after an update.
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
            }
            true
        } catch (_: Exception) {
            Log.w("PushTransport", "Push initialization unavailable")
            false
        }
    }
}

package nl.davidengelen.redsquare.feed

import android.content.Intent
import nl.davidengelen.redsquare.BuildConfig
import java.net.URI

object FeedEndpoints {
    const val OWNER = "wss://saito.davidengelen.nl/wsopen"

    fun fromIntent(intent: Intent): String {
        if (!BuildConfig.DEBUG) return OWNER
        val fixture = intent.getStringExtra("feedPeerUrl") ?: return OWNER
        return try {
            val uri = URI(fixture)
            if (uri.scheme == "ws" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/wsopen" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) fixture
            else OWNER
        } catch (_: Exception) { OWNER }
    }
}

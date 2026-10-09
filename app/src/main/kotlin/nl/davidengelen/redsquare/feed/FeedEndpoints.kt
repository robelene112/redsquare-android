package nl.davidengelen.redsquare.feed

import android.content.Intent
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.net.URI

object FeedEndpoints {
    fun fromIntent(intent: Intent, backend: SaitoBackend): String {
        if (!BuildConfig.DEBUG) return backend.peerUrl
        val extra = if (backend == SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl"
        val fixture = intent.getStringExtra(extra) ?: return backend.peerUrl
        return try {
            val uri = URI(fixture)
            if (uri.scheme == "ws" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/wsopen" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) fixture
            else "ws://127.0.0.1:1/wsopen"
        } catch (_: Exception) { "ws://127.0.0.1:1/wsopen" }
    }
}

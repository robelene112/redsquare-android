package nl.davidengelen.redsquare.spaces

import android.content.Intent
import android.net.Uri
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Routing only. Cryptographic invitation validation happens off main and
 * following a link never performs membership or key activation. */
internal object SpacesLaunchRoutes {
    const val NOTIFICATION_OWNER = "spacesNotificationOwner"
    const val NOTIFICATION_BACKEND = "spacesNotificationBackend"

    data class Open(val uri: Uri, val owner: String? = null, val backend: SaitoBackend? = null) {
        val fromNotification get() = owner != null
        fun matches(wallet: String, node: SaitoBackend) = owner == null || owner == wallet && backend == node
        override fun toString() = "Space launch"
    }

    fun from(intent: Intent): Open? {
        val uri = intent.data?.takeIf(::accepts) ?: return null
        if (!intent.hasExtra(NOTIFICATION_OWNER) && !intent.hasExtra(NOTIFICATION_BACKEND)) return Open(uri)
        val owner = intent.getStringExtra(NOTIFICATION_OWNER)?.takeIf(SaitoWallet::isPublicKey) ?: return null
        val backend = SaitoBackend.entries.firstOrNull { it.id == intent.getStringExtra(NOTIFICATION_BACKEND) } ?: return null
        return if (isDestination(uri)) Open(uri, owner, backend) else null
    }

    fun isPublic(uri: Uri?): Boolean = accepts(uri) && uri?.path == "/community"
    fun isArticle(uri: Uri?): Boolean = accepts(uri) && uri?.path == "/article"
    fun isDestination(uri: Uri?): Boolean = accepts(uri) && uri?.path == "/target"
    fun isProtectedChat(uri: Uri?): Boolean = accepts(uri) && uri?.path == "/chat"
    fun accepts(uri: Uri?): Boolean = uri != null && uri.toString().length <= 8192 &&
        uri.scheme == "saito" && uri.host == "spaces" && uri.path in setOf("/join", "/community", "/chat", "/article", "/target") &&
        uri.query == null && uri.userInfo == null && uri.port == -1 && !uri.fragment.isNullOrEmpty()
}

package nl.davidengelen.redsquare.spaces

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    class Incoming : ViewModel() {
        val pending = MutableStateFlow<Open?>(null)
    }

    private fun Bundle.putOrigin(target: SpacesAccount.Target) {
        putString("originSpace", target.space)
        putString("originChannel", target.channel)
        putString("originThread", target.thread)
    }

    private fun Bundle.origin(): SpacesAccount.Target? {
        val space = getString("originSpace") ?: return null
        val channel = getString("originChannel") ?: return null
        return SpacesAccount.Target(space, channel, getString("originThread"))
    }

    val conversationSaver = Saver<SpacesAccount.Target?, Bundle>(
        save = { target -> target?.let { Bundle().apply { putOrigin(it) } } },
        restore = { it.origin() },
    )

    /** Only an exact read address survives recreation. Membership invitations remain transient. */
    data class PendingDestination(val link: String, val owner: String, val backend: SaitoBackend,
        val cold: Boolean, val fromNotification: Boolean, val origin: SpacesAccount.Target? = null) {
        fun matches(wallet: String, node: SaitoBackend) = owner == wallet && backend == node
        override fun toString() = "Pending Space destination"

        companion object {
            val saver = Saver<PendingDestination?, Bundle>(
                save = { pending -> pending?.let { destination ->
                    Bundle().apply {
                        putString("link", destination.link)
                        putString("owner", destination.owner)
                        putString("node", destination.backend.id)
                        putBoolean("cold", destination.cold)
                        putBoolean("notification", destination.fromNotification)
                        destination.origin?.let { putOrigin(it) }
                    }
                } },
                restore = { saved ->
                    val link = saved.getString("link")?.takeIf { isDestination(Uri.parse(it)) }
                    val owner = saved.getString("owner")?.takeIf(SaitoWallet::isPublicKey)
                    val backend = SaitoBackend.entries.firstOrNull { it.id == saved.getString("node") }
                    if (link == null || owner == null || backend == null) null
                    else PendingDestination(link, owner, backend, saved.getBoolean("cold"), saved.getBoolean("notification"), saved.origin())
                },
            )
        }
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

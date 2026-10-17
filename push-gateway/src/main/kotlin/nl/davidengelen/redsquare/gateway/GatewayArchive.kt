package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.ArchiveFeedPage
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable

/** Read-only Archive access using a gateway-owned identity, never a subscriber's signing key. */
class GatewayArchive(private val identity: SaitoWallet, private val backend: SaitoBackend,
    private val localOrigin: String? = null) : Closeable {
    fun recentSocial(): ArchiveFeedPage = ArchiveFeedPage(emptyList(), null, 0, 0)
    override fun close() = Unit
}

package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable

/** One selected backend's automatic observer. Hosting is a separate operation. */
class GatewayObserver(gateway: PushGateway, identity: SaitoWallet, backend: SaitoBackend, sender: FcmSender,
    intervalMillis: Long = 30_000, localChainOrigin: String? = null, localArchiveOrigin: String? = null) : Closeable {
    fun start() = Unit
    override fun close() = Unit
}

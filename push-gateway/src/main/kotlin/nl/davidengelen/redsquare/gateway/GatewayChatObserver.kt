package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable

/** Executable #46 seam. Observation remains absent until the first ordinary delivery tracer fails. */
class GatewayChatObserver(gateway: PushGateway, identity: SaitoWallet, backend: SaitoBackend,
    sender: FcmSender, localOrigin: String? = null) : Closeable {
    fun start() = Unit
    override fun close() = Unit
}

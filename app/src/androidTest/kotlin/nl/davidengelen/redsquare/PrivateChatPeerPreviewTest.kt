package nl.davidengelen.redsquare

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateChatPeerPreviewTest {
    @Test fun authenticatedSelectedPeerAdvertisesBothPrivateChatServices() = runBlocking {
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||")
        val server = fixture.start()
        try {
            val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe local socket fixture.
            SaitoPeer(wallet, server.url("/").toString()).use { peer ->
                assertEquals(setOf("archive", "relay"), peer.services())
            }
            fixture.assertHealthy()
        } finally { server.close() }
    }
}

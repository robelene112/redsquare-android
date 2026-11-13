package nl.davidengelen.redsquare

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Opt-in public service discovery only. No application transaction, upload, mint or payment. */
object HostVaultServiceReader {
    @JvmStatic fun main(arguments: Array<String>) = runBlocking {
        require(arguments.isEmpty())
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe reference identity. Never fund.
        for (backend in SaitoBackend.entries) {
            try {
                val services = withTimeout(15_000) {
                    SaitoPeer(wallet, backend.peerUrl).use { it.services().sorted() }
                }
                println("${backend.domain}: ${services.joinToString()}")
            } catch (failure: Exception) {
                println("${backend.domain}: service discovery unavailable (${failure.javaClass.simpleName})")
            }
        }
    }
}

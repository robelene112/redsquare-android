package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoFullBlock
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger

/** Read-only chain references and full blocks. Peer tips are hints, not processed cursors. */
class GatewayChain(identity: SaitoWallet, val backend: SaitoBackend, localOrigin: String? = null) : Closeable {
    data class Cursor(val height: BigInteger, val hash: String, val forkId: String)
    data class Reference(val height: BigInteger, val hash: String, val timestamp: BigInteger)
    data class Range(val tip: Cursor, val ancestorHeight: BigInteger, val ancestorHash: String,
        val references: List<Reference>)
    fun range(cursor: Cursor? = null): Range = Range(Cursor(BigInteger.ZERO, "0".repeat(64), "0".repeat(64)),
        BigInteger.ZERO, "0".repeat(64), emptyList())
    fun block(reference: Reference): SaitoFullBlock = throw IOException("Full block read not implemented")
    override fun close() = Unit
}

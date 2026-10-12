package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Observes selected-server ancestry; it does not validate consensus or finality. */
object SaitoPaymentChain {
    data class Inclusion(val height: BigInteger, val hash: String, val tipHeight: BigInteger,
        val tipHash: String, val tipTimestamp: Long)

    fun observe(payment: SaitoPayment, snapshot: SaitoBalanceSnapshot,
        fetchBlock: (String) -> ByteArray?): Inclusion? {
        require(snapshot.publicKey == payment.author)
        var expectedHash = snapshot.blockHash
        var expectedHeight = snapshot.height
        var received = 0L
        try {
            repeat(16) {
                val bytes = fetchBlock(expectedHash) ?: return null
                received += bytes.size
                if (received > 32L * 1024 * 1024) return null
                val block = SaitoFullBlock.parse(bytes)
                if (block.hash != expectedHash || block.height != expectedHeight) return null
                if (it == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp)) return null
                if (block.contains(payment)) return Inclusion(block.height, block.hash,
                    snapshot.height, snapshot.blockHash, snapshot.timestamp)
                if (expectedHeight == BigInteger.ONE) return null
                expectedHash = block.previousHash
                expectedHeight -= BigInteger.ONE
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { return null }
        return null
    }
}

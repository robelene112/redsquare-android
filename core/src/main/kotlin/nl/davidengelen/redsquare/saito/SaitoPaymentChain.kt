package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Observes selected-server ancestry; it does not validate consensus or finality. */
object SaitoPaymentChain {
    data class Inclusion(val height: BigInteger, val hash: String, val tipHeight: BigInteger,
        val tipHash: String, val tipTimestamp: Long)

    enum class PriorStatus { REORGANIZED, UNKNOWN }

    fun check(previous: Inclusion, snapshot: SaitoBalanceSnapshot,
        fetchBlock: (String) -> ByteArray?): PriorStatus {
        if (previous.height > snapshot.height) return PriorStatus.UNKNOWN
        var result = PriorStatus.UNKNOWN
        walk(snapshot, fetchBlock) { block ->
            if (block.height != previous.height) false else {
                if (block.hash != previous.hash) result = PriorStatus.REORGANIZED
                true
            }
        }
        return result
    }

    fun observe(payment: SaitoPayment, snapshot: SaitoBalanceSnapshot,
        fetchBlock: (String) -> ByteArray?): Inclusion? {
        require(snapshot.publicKey == payment.author)
        var result: Inclusion? = null
        walk(snapshot, fetchBlock) { block ->
            if (!block.contains(payment)) false else {
                result = Inclusion(block.height, block.hash, snapshot.height, snapshot.blockHash, snapshot.timestamp)
                true
            }
        }
        return result
    }

    /** A shared verified prefix only. Callers separately decide inclusion or displacement. */
    private fun walk(snapshot: SaitoBalanceSnapshot, fetchBlock: (String) -> ByteArray?,
        stopOn: (SaitoFullBlock) -> Boolean) {
        var expectedHash = snapshot.blockHash
        var expectedHeight = snapshot.height
        var received = 0L
        try {
            repeat(16) {
                val bytes = fetchBlock(expectedHash) ?: return
                received += bytes.size
                if (received > 32L * 1024 * 1024) return
                val block = SaitoFullBlock.parse(bytes)
                if (block.hash != expectedHash || block.height != expectedHeight) return
                if (it == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp)) return
                if (stopOn(block) || expectedHeight == BigInteger.ONE) return
                expectedHash = block.previousHash
                expectedHeight -= BigInteger.ONE
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { return }
    }
}

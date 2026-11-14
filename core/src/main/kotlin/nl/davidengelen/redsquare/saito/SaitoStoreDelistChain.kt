package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Bounded selected-node ancestry, distinct from consensus/finality and from current returned stock. */
object SaitoStoreDelistChain {
    data class Observation(val height: BigInteger, val hash: String, val ordinal: BigInteger, val returned: Boolean)
    fun observe(delist: SaitoStoreDelist, snapshot: SaitoBalanceSnapshot, fetch: (String) -> ByteArray?): Observation? {
        require(snapshot.publicKey == delist.author)
        var hash = snapshot.blockHash; var height = snapshot.height; var total = 0L
        try {
            repeat(16) { depth ->
                val bytes = fetch(hash) ?: return null
                total += bytes.size; if (total > 32L * 1024 * 1024) return null
                val block = SaitoFullBlock.parse(bytes)
                if (block.hash != hash || block.height != height || depth == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp)) return null
                val ordinal = block.storeDelistOrdinal(delist)
                if (ordinal != null) {
                    val returned = snapshot.nfts.any { holding ->
                        holding.id == delist.product.nftId && holding.creator == delist.product.creator && holding.owner == delist.author &&
                            holding.quantity == delist.quantity && holding.deposit == delist.inputs[1].amount &&
                            holding.positions.map { Triple(it.block, it.ordinal, it.index) } == (0..2).map { Triple(height, ordinal, it) }
                    }
                    return Observation(height, hash, ordinal, returned)
                }
                if (height == BigInteger.ONE) return null
                hash = block.previousHash; height -= BigInteger.ONE
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { return null }
        return null
    }
}

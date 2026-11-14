package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Bounded selected-node ancestry, no consensus or irreversible-finality claim. */
object SaitoStoreChain {
    data class Payment(val height: BigInteger, val hash: String, val ordinal: BigInteger)
    data class Observation(val payment: Payment?, val receipt: SaitoStoreReceipt?, val receiptBlock: String?,
        val delivered: Boolean, val refunded: Boolean)
    fun observe(purchase: SaitoStorePurchase, snapshot: SaitoBalanceSnapshot, creator: String, previous: Payment? = null,
        fetchBlock: (String) -> ByteArray?): Observation {
        require(purchase.author == snapshot.publicKey && SaitoWallet.isPublicKey(creator))
        var payment: Payment? = null
        val receipts = ArrayList<Pair<SaitoStoreReceipt, String>>()
        var expectedHash = snapshot.blockHash; var height = snapshot.height; var received = 0L
        try {
            for (index in 0 until 16) {
                val bytes = fetchBlock(expectedHash) ?: break
                received += bytes.size; if (received > 32L * 1024 * 1024) break
                val block = SaitoFullBlock.parse(bytes)
                if (block.hash != expectedHash || block.height != height ||
                    (index == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp))) break
                block.storePaymentOrdinal(purchase)?.let { payment = Payment(height, block.hash, it) }
                receipts.addAll(block.storeReceipts(purchase).map { it to block.hash })
                if (height == BigInteger.ONE) break
                expectedHash = block.previousHash; height -= BigInteger.ONE
            }
        } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { /* Incomplete evidence remains unknown. */ }
        val original = payment ?: previous
        val observed = receipts.filter { (receipt, _) -> original != null &&
            receipt.paymentInput.block == original.height && receipt.paymentInput.ordinal == original.ordinal }
        // Conflicting outcomes stay unknown instead of selecting an arbitrary successful answer.
        val receipt = observed.singleOrNull()
        val asset = receipt?.first?.asset
        val delivered = asset != null && asset.creator == creator && snapshot.nfts.any { it.id == asset.id && it.creator == asset.creator &&
            it.owner == asset.owner && it.quantity == asset.quantity && it.witnesses == asset.witnesses }
        return Observation(payment, receipt?.first, receipt?.second, delivered,
            receipt?.first?.kind == SaitoStoreReceipt.Kind.REFUND)
    }
}

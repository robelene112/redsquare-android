package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Bounded selected-node ancestry with durable older-history continuation. No consensus/finality claim. */
object SaitoStoreChain {
    data class Payment(val height: BigInteger, val hash: String, val ordinal: BigInteger)
    /** Every continuation follows a previously checked, selected-node ancestry prefix. */
    data class Cursor(val height: BigInteger, val hash: String)
    data class Observation(val payment: Payment?, val receipt: SaitoStoreReceipt?, val receiptBlock: String?,
        val delivered: Boolean, val refunded: Boolean, val historical: Boolean = false, val cursor: Cursor? = null,
        val displaced: Boolean = false)
    fun observe(purchase: SaitoStorePurchase, snapshot: SaitoBalanceSnapshot, creator: String, previous: Payment? = null,
        previousReceipt: SaitoStoreReceipt? = null, previousReceiptBlock: String? = null, cursor: Cursor? = null,
        fetchBlock: (String) -> ByteArray?): Observation {
        require(purchase.author == snapshot.publicKey && SaitoWallet.isPublicKey(creator))
        require((previousReceipt == null) == (previousReceiptBlock == null))
        var payment: Payment? = null
        var displaced = false
        var received = 0L
        val receipts = ArrayList<Pair<SaitoStoreReceipt, String>>()
        val minimum = purchase.inputs.maxOf { it.block }
        fun scan(start: Cursor, fresh: Boolean): Cursor? {
            var next = start
            try {
                repeat(16) { index ->
                    if (next.height < minimum) return null
                    val bytes = fetchBlock(next.hash) ?: return next
                    received += bytes.size; if (received > 32L * 1024 * 1024) return next
                    val block = SaitoFullBlock.parse(bytes)
                    if (block.hash != next.hash || block.height != next.height ||
                        (fresh && index == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp))) return next
                    if (fresh && previous?.height == block.height && previous.hash != block.hash) displaced = true
                    block.storePaymentOrdinal(purchase)?.let { payment = Payment(block.height, block.hash, it) }
                    receipts.addAll(block.storeReceipts(purchase).filter { it.asset?.creator?.let { key -> key == creator } != false }
                        .map { it to block.hash })
                    next = Cursor(next.height - BigInteger.ONE, block.previousHash)
                    if (payment != null) return null // Settlement cannot precede the original payment.
                }
            } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
            catch (_: Exception) { return next }
            return next.takeIf { it.height >= minimum }
        }
        val recentCursor = scan(Cursor(snapshot.height, snapshot.blockHash), true)
        var historyCursor = recentCursor
        if (payment == null && cursor != null && cursor.height < snapshot.height && !displaced) historyCursor = scan(cursor, false)
        val original = payment ?: previous.takeUnless { displaced }
        val recent = receipts.distinctBy { it.first.signature }.filter { (receipt, _) -> original == null ||
            (receipt.paymentInput.block == original.height && receipt.paymentInput.ordinal == original.ordinal) }
        val retained = previousReceipt?.takeIf { !displaced && it.asset?.creator?.let { key -> key == creator } != false &&
            (original == null || (it.paymentInput.block == original.height && it.paymentInput.ordinal == original.ordinal)) }
        // Conflicting observed outcomes remain unknown. A retained receipt never overrules current evidence.
        val selected = if (recent.size == 1) recent.single() else if (recent.isEmpty() && retained != null)
            retained to requireNotNull(previousReceiptBlock) else null
        val historical = selected != null && recent.none { it.first.signature == selected.first.signature }
        val asset = selected?.first?.asset
        val linked = original != null && selected?.first?.paymentInput?.let {
            it.block == original.height && it.ordinal == original.ordinal
        } == true
        val delivered = linked && asset != null && snapshot.nfts.any { it.id == asset.id && it.creator == creator &&
            it.owner == asset.owner && it.quantity == asset.quantity && it.witnesses == asset.witnesses }
        val refund = selected?.first?.refundOutput
        val refunded = linked && selected?.first?.kind == SaitoStoreReceipt.Kind.REFUND &&
            (!historical || (refund != null && snapshot.slips.any { it == refund }))
        return Observation(payment ?: previous.takeUnless { displaced }, selected?.first, selected?.second, delivered, refunded,
            historical, if (selected != null || payment != null) null else historyCursor, displaced)
    }
}

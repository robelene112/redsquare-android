package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import com.google.gson.JsonObject

/** Funded payment boundary. The zero-payment restore contract remains separate. */
class SaitoPayment private constructor(val transaction: SaitoTransaction) {
    companion object {
        /** Caller supplies current, wallet-owned eligible inputs, excluding reserved funds. */
        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, recipient: String,
            amount: BigInteger, fee: BigInteger, timestamp: Long, operationId: String, memo: String): SaitoPayment {
            require(SaitoWallet.isPublicKey(recipient) && timestamp >= 0)
            require(amount.signum() > 0 && amount.bitLength() <= 64 && fee.signum() >= 0 && fee.bitLength() <= 64)
            require(operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            require(inputs.size in 1..254)
            val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
            var total = BigInteger.ZERO
            inputs.forEach { slip ->
                require(slip.block.signum() > 0 && slip.block.bitLength() <= 64)
                require(slip.ordinal.signum() >= 0 && slip.ordinal.bitLength() <= 64)
                require(slip.index in 0..255 && slip.type in setOf(0, 1, 5, 7))
                require(slip.amount.signum() > 0 && slip.amount.bitLength() <= 64)
                require(locations.add(Triple(slip.block, slip.ordinal, slip.index)))
                total += slip.amount
                require(total.bitLength() <= 64)
            }
            val change = total - amount - fee
            require(change.signum() >= 0) { "Insufficient funding" }
            val body = JsonObject().apply {
                addProperty("module", "Saito"); addProperty("request", "crypto payment")
                addProperty("amount", BigDecimal(amount, 8).stripTrailingZeros().toPlainString())
                addProperty("from", wallet.publicKey); addProperty("to", recipient)
                addProperty("hash", operationId); addProperty("memo", memo)
            }
            val message = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(body.toString()))
                .let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
            require(message.size <= SaitoTransaction.MAX_MESSAGE_BYTES && memo.toByteArray(Charsets.UTF_8).size <= 4096)
            val author = Base58.decode(wallet.publicKey)
            val receiving = Base58.decode(recipient)
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val payment = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, amount, 0)
            val returning = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 1, change, 0)
            val slips = listOf(author to zero) + inputs.map { author to it } +
                listOf(receiving to payment, author to returning)
            val preimage = ByteBuffer.allocate(16 + slips.size * 43 + message.size).putLong(timestamp)
            slips.forEach { (point, slip) -> preimage.put(point).putLong(slip.amount.toLong())
                .put(slip.index.toByte()).put(slip.type.toByte()) }
            preimage.putInt(1).putInt(0).put(message)
            val signature = wallet.sign(preimage.array()).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val network = ByteBuffer.allocate(93 + slips.size * 59 + message.size)
                .putInt(inputs.size + 1).putInt(2).putInt(message.size).putInt(0).put(signature)
                .putLong(timestamp).putInt(1).put(0)
            slips.forEach { (point, slip) -> network.put(point).putLong(slip.amount.toLong())
                .putLong(slip.block.toLong()).putLong(slip.ordinal.toLong()).put(slip.index.toByte()).put(slip.type.toByte()) }
            network.put(message)
            return SaitoPayment(SaitoTransaction.fromFundedBytes(network.array()))
        }
    }
}

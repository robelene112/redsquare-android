package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.util.Base64
import com.google.gson.JsonObject

/** Funded payment boundary. The zero-payment restore contract remains separate. */
class SaitoPayment private constructor(val transaction: SaitoTransaction, val author: String,
    inputs: List<SaitoBalanceSnapshot.Slip>, val recipient: String, val amount: BigInteger,
    val fee: BigInteger, val timestamp: Long, val operationId: String, val memo: String) {
    val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    companion object {
        fun restore(encoded: String): SaitoPayment {
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size in 330..(SaitoTransaction.MAX_MESSAGE_BYTES + 93 + 257 * 59))
            require(Base64.getEncoder().encodeToString(bytes) == encoded)
            val header = ByteBuffer.wrap(bytes)
            val count = header.getInt(0)
            val length = header.getInt(8)
            require(count in 2..255 && header.getInt(4) == 2 && header.getInt(12) == 0)
            require(length in 1..SaitoTransaction.MAX_MESSAGE_BYTES &&
                bytes.size == 93 + 59 * (count + 2) + length)
            require(header.getLong(80) >= 0 && header.getInt(88) == 1 && bytes[92] == 0.toByte())
            val author = Base58.encode(bytes.copyOfRange(93, 126))
            require(SaitoWallet.isPublicKey(author))
            require(bytes.copyOfRange(126, 152).all { it == 0.toByte() })
            val inputs = (1 until count).map { index ->
                val offset = 93 + 59 * index
                require(bytes.copyOfRange(offset, offset + 33).contentEquals(bytes.copyOfRange(93, 126)))
                slip(bytes, offset)
            }
            val total = checkedInputs(inputs)
            val output = 93 + 59 * count
            val recipient = Base58.encode(bytes.copyOfRange(output, output + 33))
            require(SaitoWallet.isPublicKey(recipient))
            val payment = slip(bytes, output)
            val returning = slip(bytes, output + 59)
            require(Base58.encode(bytes.copyOfRange(output + 59, output + 92)) == author)
            require(payment.block.signum() == 0 && payment.ordinal.signum() == 0 && payment.index == 0 && payment.type == 0)
            require(returning.block.signum() == 0 && returning.ordinal.signum() == 0 && returning.index == 1 && returning.type == 0)
            require(payment.amount.signum() > 0)
            val fee = total - payment.amount - returning.amount
            require(fee.signum() >= 0)
            val transaction = SaitoTransaction.fromFundedBytes(bytes)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            val body = signed.body
            require(body.keySet() == setOf("module", "request", "amount", "from", "to", "hash", "memo"))
            require(body.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString })
            require(body.get("module").asString == "Saito" && body.get("request").asString == "crypto payment")
            require(body.get("from").asString == author && body.get("to").asString == recipient)
            require(body.get("amount").asString == BigDecimal(payment.amount, 8).stripTrailingZeros().toPlainString())
            val operationId = body.get("hash").asString
            val memo = body.get("memo").asString
            require(operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && memo.toByteArray(Charsets.UTF_8).size <= 4096)
            require(body.toString().toByteArray(Charsets.UTF_8).contentEquals(bytes.copyOfRange(output + 118, bytes.size)))
            return SaitoPayment(transaction, author, inputs, recipient, payment.amount, fee, signed.createdAt, operationId, memo)
        }

        /** Caller supplies current, wallet-owned eligible inputs, excluding reserved funds. */
        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, recipient: String,
            amount: BigInteger, fee: BigInteger, timestamp: Long, operationId: String, memo: String): SaitoPayment {
            require(SaitoWallet.isPublicKey(recipient) && timestamp >= 0)
            require(amount.signum() > 0 && amount.bitLength() <= 64 && fee.signum() >= 0 && fee.bitLength() <= 64)
            require(operationId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            require(memo.length <= 4096 && memo.toByteArray(Charsets.UTF_8).size <= 4096)
            val total = checkedInputs(inputs)
            val change = total - amount - fee
            require(change.signum() >= 0) { "Insufficient funding" }
            val body = JsonObject().apply {
                addProperty("module", "Saito"); addProperty("request", "crypto payment")
                addProperty("amount", BigDecimal(amount, 8).stripTrailingZeros().toPlainString())
                addProperty("from", wallet.publicKey); addProperty("to", recipient)
                addProperty("hash", operationId); addProperty("memo", memo)
            }
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val payment = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, amount, 0)
            val returning = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 1, change, 0)
            val fundedInputs = listOf(SaitoTransaction.OwnedSlip(wallet.publicKey, zero)) +
                inputs.map { SaitoTransaction.OwnedSlip(wallet.publicKey, it) }
            val outputs = listOf(SaitoTransaction.OwnedSlip(recipient, payment),
                SaitoTransaction.OwnedSlip(wallet.publicKey, returning))
            return SaitoPayment(SaitoTransaction.createNormal(wallet, body, timestamp, fundedInputs, outputs),
                wallet.publicKey, inputs, recipient, amount, fee, timestamp, operationId, memo)
        }

        private fun checkedInputs(inputs: List<SaitoBalanceSnapshot.Slip>): BigInteger {
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
            return total
        }

        private fun slip(bytes: ByteArray, offset: Int) = SaitoBalanceSnapshot.Slip(
            BigInteger(1, bytes.copyOfRange(offset + 41, offset + 49)),
            BigInteger(1, bytes.copyOfRange(offset + 49, offset + 57)), bytes[offset + 57].toInt() and 255,
            BigInteger(1, bytes.copyOfRange(offset + 33, offset + 41)), bytes[offset + 58].toInt() and 255)
    }
}

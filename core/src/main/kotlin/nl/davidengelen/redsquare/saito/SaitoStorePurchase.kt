package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.util.Base64
import com.google.gson.JsonObject

/** Immutable SAITO Store escrow. Its script commits the buyer and selected Store counterparty. */
class SaitoStorePurchase private constructor(val transaction: SaitoTransaction, val author: String,
    inputs: List<SaitoBalanceSnapshot.Slip>, val store: String, val nftId: String, val quantity: BigInteger,
    val unitPrice: BigInteger, val storeFee: BigInteger, val networkFee: BigInteger, val timestamp: Long,
    val escrow: String) {
    val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    val amount: BigInteger get() = unitPrice * quantity + storeFee
    val total: BigInteger get() = amount + networkFee
    companion object {
        fun restore(encoded: String): SaitoStorePurchase {
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
                SaitoFundingInputs.slip(bytes, offset)
            }
            val total = SaitoFundingInputs.checked(inputs)
            val output = 93 + 59 * count
            val recipient = Base58.encode(bytes.copyOfRange(output, output + 33))
            require(Base58.decode(recipient).let { it.size == 33 && it[0] == 0.toByte() })
            val payment = SaitoFundingInputs.slip(bytes, output)
            val returning = SaitoFundingInputs.slip(bytes, output + 59)
            require(Base58.encode(bytes.copyOfRange(output + 59, output + 92)) == author)
            require(payment.block.signum() == 0 && payment.ordinal.signum() == 0 && payment.index == 0 && payment.type == 0)
            require(returning.block.signum() == 0 && returning.ordinal.signum() == 0 && returning.index == 1 && returning.type == 0)
            require(payment.amount.signum() > 0)
            val fee = total - payment.amount - returning.amount
            require(fee.signum() >= 0)
            val transaction = SaitoTransaction.fromFundedBytes(bytes)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            val body = signed.body
            require(body.keySet() == setOf("module", "request", "buyer", "refund", "nft_id", "quantity", "price", "fee", "access_script", "access_hash", "p2sh_address"))
            require(body.get("module").asString == "Store" && body.get("request").asString == "purchase-asset")
            require(body.get("buyer").asString == author && body.get("refund").asString == author)
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            val tree = com.google.gson.JsonParser.parseString(script.source).asJsonObject
            val keys = tree.getAsJsonArray("publickeys")
            require(keys.size() == 2 && keys[0].asString == author)
            val store = keys[1].asString
            require(SaitoWallet.isPublicKey(store) && tree == scriptBody(author, store))
            require(body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash)
            require(Base58.decode(recipient).contentEquals(hex("00" + script.hash)))
            val id = body.get("nft_id").asString
            require(id.matches(Regex("[0-9a-f]{66}")))
            val quantity = body.get("quantity").asString.toBigInteger()
            require(body.get("quantity").asJsonPrimitive.isNumber && quantity.signum() > 0 && quantity <= MAX_SAFE_INTEGER)
            fun nolan(name: String): BigInteger {
                val value = body.get(name)
                require(value.asJsonPrimitive.isString && value.asString.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,8})?")))
                return BigDecimal(value.asString).movePointRight(8).toBigIntegerExact().also { require(it.bitLength() <= 64) }
            }
            val unit = nolan("price"); val storeFee = nolan("fee")
            require(unit.signum() > 0 && unit <= MAX_SAFE_INTEGER && payment.amount <= MAX_SAFE_INTEGER && unit * quantity + storeFee == payment.amount)
            val result = SaitoStorePurchase(transaction, author, inputs, store, id, quantity, unit, storeFee, fee, signed.createdAt, recipient)
            require(body.toString().toByteArray(Charsets.UTF_8).contentEquals(bytes.copyOfRange(output + 118, bytes.size)))
            return result
        }

        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, store: String, nftId: String,
            quantity: BigInteger, unitPrice: BigInteger, storeFee: BigInteger, networkFee: BigInteger, timestamp: Long): SaitoStorePurchase {
            require(SaitoWallet.isPublicKey(store) && nftId.matches(Regex("[0-9a-f]{66}")) && timestamp >= 0)
            require(quantity.signum() > 0 && quantity <= MAX_SAFE_INTEGER && unitPrice.signum() > 0)
            require(storeFee.signum() >= 0 && networkFee.signum() >= 0)
            val amount = unitPrice * quantity + storeFee
            require(amount <= MAX_SAFE_INTEGER && unitPrice <= MAX_SAFE_INTEGER && networkFee.bitLength() <= 64)
            val change = SaitoFundingInputs.checked(inputs) - amount - networkFee
            require(change.signum() >= 0) { "Insufficient funding" }
            val script = SaitoAccessScript.parse(scriptBody(wallet.publicKey, store).toString())
            val escrow = Base58.encode(hex("00" + script.hash))
            val body = JsonObject().apply {
                addProperty("module", "Store"); addProperty("request", "purchase-asset")
                addProperty("buyer", wallet.publicKey); addProperty("refund", wallet.publicKey)
                addProperty("nft_id", nftId); addProperty("quantity", quantity)
                addProperty("price", BigDecimal(unitPrice, 8).stripTrailingZeros().toPlainString())
                addProperty("fee", BigDecimal(storeFee, 8).stripTrailingZeros().toPlainString())
                addProperty("access_script", script.source); addProperty("access_hash", script.hash)
                addProperty("p2sh_address", "00" + script.hash)
            }
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val source = listOf(SaitoTransaction.OwnedSlip(wallet.publicKey, zero)) + inputs.map { SaitoTransaction.OwnedSlip(wallet.publicKey, it) }
            val outputs = listOf(SaitoTransaction.OwnedSlip(escrow, zero.copy(amount = amount)),
                SaitoTransaction.OwnedSlip(wallet.publicKey, zero.copy(index = 1, amount = change)))
            return restore(SaitoTransaction.createStorePurchase(wallet, body, timestamp, source, outputs).encoded())
        }

        private val MAX_SAFE_INTEGER = BigInteger("9007199254740991")
        private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        private fun scriptBody(buyer: String, store: String) = JsonObject().apply {
            addProperty("op", "CHECKMULTISIG"); addProperty("m", 1)
            add("publickeys", com.google.gson.JsonArray().apply { add(buyer); add(store) })
            addProperty("msg", "tx.from.p2sh.utxoset_key")
        }


    }
}

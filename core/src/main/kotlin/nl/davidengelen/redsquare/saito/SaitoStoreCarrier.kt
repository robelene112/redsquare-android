package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** Signed original purchase intent for the deposit provider. It spends no SAITO and is never broadcast. */
class SaitoStoreCarrier private constructor(val transaction: SaitoTransaction, val buyer: String,
    val store: String, val nftId: String, val quantity: BigInteger, val price: StorePrice, val timestamp: Long) {
    val total: BigDecimal get() = price.amount * quantity.toBigDecimal()
    val escrow: String get() = Base58.encode(("00" + SaitoAccessScript.parse(storePurchaseScriptBody(buyer, store).toString()).hash)
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    companion object {
        private val maximum = BigInteger("9007199254740991")
        fun create(wallet: SaitoWallet, store: String, nftId: String, quantity: BigInteger,
            price: StorePrice, timestamp: Long): SaitoStoreCarrier {
            validate(store, nftId, quantity, price)
            val script = SaitoAccessScript.parse(storePurchaseScriptBody(wallet.publicKey, store).toString())
            val body = JsonObject().apply {
                addProperty("module", "Store"); addProperty("request", "purchase-asset")
                addProperty("buyer", wallet.publicKey); addProperty("refund", wallet.publicKey)
                addProperty("nft_id", nftId); addProperty("quantity", quantity)
                addProperty("price", if (price.currency == "SAITO") price.amount.stripTrailingZeros().toPlainString() else "0")
                addProperty("fee", "0"); addProperty("access_script", script.source)
                addProperty("access_hash", script.hash); addProperty("p2sh_address", "00" + script.hash)
                if (price.currency != "SAITO") {
                    addProperty("web3_ticker", price.currency); addProperty("web3_amount", price.amount.stripTrailingZeros().toPlainString())
                }
            }
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val input = SaitoTransaction.OwnedSlip(wallet.publicKey, zero)
            val address = Base58.encode(("00" + script.hash).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            val outputs = listOf(SaitoTransaction.OwnedSlip(address, zero), SaitoTransaction.OwnedSlip(wallet.publicKey, zero.copy(index = 1)))
            return restore(SaitoTransaction.createStorePurchase(wallet, body, timestamp, listOf(input, input), outputs).encoded())
        }
        fun restore(encoded: String): SaitoStoreCarrier {
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size >= 329)
            val header = ByteBuffer.wrap(bytes)
            require(header.getInt(0) == 2 && header.getInt(4) == 2 && header.getInt(12) == 0 && header.getInt(88) == 1 && bytes[92] == 0.toByte() &&
                header.getLong(80) >= 0 && header.getInt(8) in 1..SaitoTransaction.MAX_MESSAGE_BYTES && bytes.size == 329 + header.getInt(8))
            val transaction = SaitoTransaction.fromStoreCarrierBytes(bytes)
            val signed = SaitoEnvelope.decode(transaction.envelope()); val body = signed.body
            val extra = if (body.has("web3_ticker")) setOf("web3_ticker", "web3_amount") else emptySet()
            require(body.keySet() == setOf("module", "request", "buyer", "refund", "nft_id", "quantity", "price", "fee", "access_script", "access_hash", "p2sh_address") + extra)
            require(body.get("module").asString == "Store" && body.get("request").asString == "purchase-asset" &&
                body.get("buyer").asString == signed.author && body.get("refund").asString == signed.author && body.get("fee").asString.toBigDecimal().signum() == 0)
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            val keys = JsonParser.parseString(script.source).asJsonObject.getAsJsonArray("publickeys").map { it.asString }
            require(keys.size == 2 && JsonParser.parseString(script.source) == storePurchaseScriptBody(signed.author, keys[1]) &&
                body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash)
            val price = if (extra.isEmpty()) StorePrice("SAITO", body.get("price").asString.toBigDecimal())
                else StorePrice(body.get("web3_ticker").asString, body.get("web3_amount").asString.toBigDecimal()).also {
                    require(it.currency != "SAITO" && body.get("price").asString.toBigDecimal().signum() == 0)
                }
            val quantity = body.get("quantity").asString.toBigInteger()
            require(body.get("quantity").asJsonPrimitive.isNumber)
            val id = body.get("nft_id").asString
            validate(keys[1], id, quantity, price)
            val escrow = Base58.encode(("00" + script.hash).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            repeat(4) { index ->
                val offset = 93 + 59 * index
                require(Base58.encode(bytes.copyOfRange(offset, offset + 33)) == if (index == 2) escrow else signed.author)
                val tail = bytes.copyOfRange(offset + 33, offset + 59)
                require(tail.indices.all { tail[it] == if (index == 3 && it == 24) 1.toByte() else 0.toByte() })
            }
            return SaitoStoreCarrier(transaction, signed.author, keys[1], id, quantity, price, signed.createdAt)
        }
        private fun validate(store: String, id: String, quantity: BigInteger, price: StorePrice) {
            require(SaitoWallet.isPublicKey(store) && id.matches(Regex("[0-9a-f]{66}")) && quantity.signum() > 0 && quantity <= maximum)
            require(price.currency.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}")) && price.amount.signum() > 0 && price.amount.scale() in 0..18 && price.amount.precision() <= 32)
            val total = price.amount * quantity.toBigDecimal()
            require(total <= maximum.toBigDecimal() && total.toDouble().isFinite() && total.compareTo(BigDecimal.valueOf(total.toDouble())) == 0)
            if (price.currency == "SAITO") require(total.movePointRight(8).toBigIntegerExact() <= maximum)
        }
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** Authenticate Store-created remaining inventory back to its seller's original offer. */
object SaitoStoreLineage {
    fun read(signature: String, fetch: (String) -> String?): StoreProduct? = runCatching {
        val visiting = HashSet<String>()
        var bytesRead = 0L
        fun load(id: String): String {
            require(id.matches(Regex("[0-9a-f]{128}")) && visiting.add(id) && visiting.size <= 16)
            val response = requireNotNull(fetch(id))
            bytesRead += response.toByteArray().size
            require(bytesRead <= 8L * 1024 * 1024)
            return readArchiveRows(response, { it }, { envelope -> runCatching { identity(envelope) == id }.getOrDefault(false) }, { it }).entries.single()
        }
        fun resolve(id: String): StoreProduct {
            val envelope = load(id)
            val archive = archive(envelope)
            SaitoStore.readOriginalListingArchive(id, archive)?.let { return it }
            val body = body(envelope)
            val sale = body.getAsJsonObject("fulfill_sale")
            // Official fulfillment consumes ordered lots; only the last consumed lot can retain stock.
            val contributors = sale.get("listing_signatures")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
            require(contributors.size <= 16)
            val previous = resolve(contributors.lastOrNull() ?: sale.get("prior_inventory").asString)
            val rules = JsonParser.parseString(body(previous.envelope).get("access_script").asString).asJsonObject
            val keys = rules.getAsJsonArray("publickeys").map { it.asString }
            require(keys.size == 2 && keys[0] == previous.seller && SaitoWallet.isPublicKey(keys[1]))
            val (purchase, price) = publicPayment(SaitoEnvelope.originalNetworkBytes(load(sale.get("sale_signature").asString)), keys[1])
            val wire = SaitoEnvelope.originalNetworkBytes(envelope)
            val receipt = SaitoStoreReceipt.read(purchase, Base64.getEncoder().encodeToString(wire))
            val delivered = requireNotNull(receipt.asset)
            require(receipt.kind == SaitoStoreReceipt.Kind.FULFILLMENT && keys.size == 2 && keys[0] == previous.seller && keys[1] == purchase.store &&
                purchase.nftId == previous.nftId && delivered.creator == previous.creator && sale.get("seller").asString == previous.seller &&
                price.matches(previous.price))
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            require(script.hash == SaitoAccessScript.parse(body(previous.envelope).get("access_script").asString).hash)
            val header = ByteBuffer.wrap(wire); val inputs = header.getInt(0); val outputs = header.getInt(4)
            val remaining = (inputs until inputs + outputs - 2).mapNotNull { index ->
                val before = row(wire, index); val middle = row(wire, index + 1); val after = row(wire, index + 2)
                if (before.position.type != 9 || after.position.type != 9 || middle.position.type !in setOf(0, 1) ||
                    Base58.decode(middle.publicKey).hex() != "00" + script.hash) null
                else {
                    require(before.publicKey == previous.creator && Base58.decode(after.publicKey).hex() == previous.nftId)
                    before.position.amount
                }
            }.single()
            require(remaining.signum() > 0 && remaining < previous.quantity)
            val terms = body.getAsJsonObject("listing")
            val ticker = terms.get("web3_ticker")?.asString.orEmpty().trim().uppercase().ifBlank { "SAITO" }
            require(ticker == price.currency)
            require(terms.get(if (ticker == "SAITO") "price" else "web3_amount").asString.toBigDecimal().compareTo(price.amount) == 0)
            if (ticker != "SAITO") require(terms.get("price").asString.toBigDecimal().signum() == 0)
            val title = terms.get("title")?.asString.orEmpty().also { require(it.length <= 10_000) }
            val description = terms.get("description")?.asString.orEmpty().also { require(it.length <= 200_000) }
            val category = StoreCategory.entries.firstOrNull { it.wire == terms.get("category")?.asString } ?: previous.category
            val provenance = previous.provenance + previous.envelope + purchase.transaction.envelope()
            require(provenance.size <= 30)
            return previous.copy(signature = id, title = title, description = description, category = category, quantity = remaining, envelope = envelope, provenance = java.util.Collections.unmodifiableList(provenance))
        }
        resolve(signature)
    }.getOrElse { failure ->
        if (failure is java.util.concurrent.CancellationException) throw failure
        null
    }

    /** Only used before verifying the Store-signed fulfillment. This is not a private buyer payment receipt. */
    private class PublicPayment(override val transaction: SaitoTransaction, override val buyer: String, override val store: String,
        override val escrow: String, override val nftId: String, override val quantity: BigInteger, override val amount: BigInteger,
        inputs: List<SaitoBalanceSnapshot.Slip>) : SaitoStorePayment {
        override val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    }
    private fun publicPayment(wire: ByteArray, expectedStore: String): Pair<SaitoStorePayment, StorePrice> {
        val direct = try { SaitoStorePurchase.restore(Base64.getEncoder().encodeToString(wire)) }
        catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (direct != null) return direct to StorePrice("SAITO", java.math.BigDecimal(direct.unitPrice, 8))
        val header = ByteBuffer.wrap(wire)
        val count = header.getInt(0)
        require(count in 2..255 && header.getInt(4) == 2 && header.getInt(12) == 0 && header.getInt(88) == 1 && wire[92] == 0.toByte() &&
            header.getLong(80) >= 0 && header.getInt(8) in 1..SaitoTransaction.MAX_MESSAGE_BYTES && wire.size == 93 + 59 * (count + 2) + header.getInt(8))
        val transaction = SaitoTransaction.fromFundedBytes(wire)
        val signed = SaitoEnvelope.decode(transaction.envelope())
        val body = signed.body
        val providerFields = if (body.has("inbound_payment_uuid") || body.has("transfer_uuid")) setOf("inbound_payment_uuid", "transfer_uuid") else emptySet()
        val cryptoFields = if (body.has("web3_ticker")) setOf("web3_ticker", "web3_amount") else emptySet()
        val note = if (body.has("note")) setOf("note") else emptySet()
        require(body.keySet() == setOf("module", "request", "buyer", "refund", "nft_id", "quantity", "price", "fee", "access_script", "access_hash", "p2sh_address") + providerFields + cryptoFields + note)
        require(body.get("module").asString == "Store" && body.get("request").asString == "purchase-asset")
        val buyer = body.get("buyer").asString
        require(SaitoWallet.isPublicKey(buyer) && body.get("refund").asString == buyer)
        if (providerFields.isEmpty()) require(buyer == signed.author)
        else {
            val inbound = body.get("inbound_payment_uuid").asString
            require(inbound.isNotBlank() && inbound.length <= 2048 && inbound.none(Char::isISOControl))
            require(body.get("transfer_uuid").asString.matches(Regex("[1-9][0-9]{0,15}")))
        }
        if (note.isNotEmpty()) require(body.get("note").asJsonPrimitive.isString)
        val script = SaitoAccessScript.parse(body.get("access_script").asString)
        require(JsonParser.parseString(script.source) == storePurchaseScriptBody(buyer, expectedStore) &&
            body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash)
        val escrow = Base58.encode(("00" + script.hash).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        require(wire.copyOfRange(126, 152).all { it == 0.toByte() })
        val inputs = (1 until count).map { index ->
            val offset = 93 + index * 59
            require(Base58.encode(wire.copyOfRange(offset, offset + 33)) == signed.author)
            SaitoFundingInputs.slip(wire, offset)
        }
        val total = SaitoFundingInputs.checked(inputs)
        val offset = 93 + count * 59
        val payment = SaitoFundingInputs.slip(wire, offset)
        val change = SaitoFundingInputs.slip(wire, offset + 59)
        val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
        require(Base58.encode(wire.copyOfRange(offset, offset + 33)) == escrow && Base58.encode(wire.copyOfRange(offset + 59, offset + 92)) == signed.author &&
            payment == zero.copy(amount = payment.amount) && change == zero.copy(index = 1, amount = change.amount) &&
            payment.amount.signum() > 0 && total >= payment.amount + change.amount)
        val id = body.get("nft_id").asString.also { require(it.matches(Regex("[0-9a-f]{66}"))) }
        val countText = body.get("quantity").asString
        require(body.get("quantity").asJsonPrimitive.isNumber && countText.matches(Regex("[1-9][0-9]{0,15}")))
        val quantity = countText.toBigInteger().also { require(it <= BigInteger("9007199254740991")) }
        fun decimal(name: String): java.math.BigDecimal {
            val value = body.get(name)
            require(value.asJsonPrimitive.isString && value.asString.length <= 40 && value.asString.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,18})?")))
            return value.asString.toBigDecimal()
        }
        val fee = decimal("fee").movePointRight(8).toBigIntegerExact().also { require(it.bitLength() <= 64) }
        val price = if (cryptoFields.isEmpty()) {
            val unit = decimal("price").also { require(it.signum() > 0) }
            require(unit.movePointRight(8).toBigIntegerExact() * quantity + fee == payment.amount)
            StorePrice("SAITO", unit)
        } else {
            val ticker = body.get("web3_ticker").asString.also { require(it.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}")) && it != "SAITO") }
            val amount = decimal("web3_amount").also { require(it.signum() > 0 && it.precision() <= 32 && it.scale() in -32..32) }
            require(decimal("price").signum() == 0)
            StorePrice(ticker, amount)
        }
        return PublicPayment(transaction, buyer, expectedStore, escrow, id, quantity, payment.amount, inputs) to price
    }

    fun readSaved(signature: String, envelope: String, provenance: List<String>): StoreProduct {
        require(provenance.size <= 30)
        val sources = (provenance + envelope).associateBy(::identity)
        return requireNotNull(read(signature) { sources[it]?.let(::archive) })
    }

    fun restore(product: StoreProduct): StoreProduct =
        readSaved(product.signature, product.envelope, product.provenance).also { require(it == product) }

    internal fun body(envelope: String): JsonObject = JsonParser.parseString(Base64.getDecoder().decode(
        JsonParser.parseString(envelope).asJsonObject.get("m").asString).toString(Charsets.UTF_8)).asJsonObject

    private fun archive(envelope: String): String = JsonArray().apply { add(JsonObject().apply { addProperty("tx", envelope) }) }.toString()
    private fun identity(envelope: String): String = SaitoEnvelope.originalNetworkBytes(envelope).copyOfRange(16, 80).hex()
    private fun row(wire: ByteArray, index: Int): SaitoNft.Row {
        val offset = 93 + index * 59
        return SaitoNft.Row(Base58.encode(wire.copyOfRange(offset, offset + 33)), SaitoBalanceSnapshot.Slip(
            BigInteger(1, wire.copyOfRange(offset + 41, offset + 49)), BigInteger(1, wire.copyOfRange(offset + 49, offset + 57)),
            wire[offset + 57].toInt() and 255, BigInteger(1, wire.copyOfRange(offset + 33, offset + 41)), wire[offset + 58].toInt() and 255))
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}

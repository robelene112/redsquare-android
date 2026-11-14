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
            val purchase = SaitoStorePurchase.restore(Base64.getEncoder().encodeToString(network(load(sale.get("sale_signature").asString))))
            val wire = network(envelope)
            val receipt = SaitoStoreReceipt.read(purchase, Base64.getEncoder().encodeToString(wire))
            val delivered = requireNotNull(receipt.asset)
            val rules = JsonParser.parseString(body(previous.envelope).get("access_script").asString).asJsonObject
            val keys = rules.getAsJsonArray("publickeys").map { it.asString }
            require(receipt.kind == SaitoStoreReceipt.Kind.FULFILLMENT && keys.size == 2 && keys[0] == previous.seller && keys[1] == purchase.store &&
                purchase.nftId == previous.nftId && delivered.creator == previous.creator && sale.get("seller").asString == previous.seller &&
                purchase.unitPrice == requireNotNull(previous.price).amount.movePointRight(8).toBigIntegerExact() && previous.price.currency == "SAITO")
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
            val ticker = terms.get("web3_ticker")?.asString.orEmpty()
            require(ticker.isEmpty() || ticker.equals("SAITO", ignoreCase = true))
            require(terms.get("price").asString.toBigDecimal().movePointRight(8).toBigIntegerExact() == purchase.unitPrice)
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
    private fun identity(envelope: String): String = network(envelope).copyOfRange(16, 80).hex()
    private fun network(envelope: String): ByteArray {
        require(envelope.toByteArray().size <= 2 * 1024 * 1024)
        val value = JsonParser.parseString(envelope).asJsonObject
        val header = Base64.getDecoder().decode(value.get("t").asString)
        require(header.size >= 93)
        val bytes = ByteBuffer.wrap(header); val inputs = bytes.getInt(0); val outputs = bytes.getInt(4)
        require(inputs in 1..255 && outputs in 1..255 && bytes.getInt(8) == 0 && bytes.getInt(12) == 0 && header.size == 93 + 59 * (inputs + outputs))
        val message = Base64.getDecoder().decode(value.get("m").asString)
        require(message.size in 1..SaitoTransaction.MAX_MESSAGE_BYTES)
        bytes.putInt(8, message.size)
        return header + message
    }
    private fun row(wire: ByteArray, index: Int): SaitoNft.Row {
        val offset = 93 + index * 59
        return SaitoNft.Row(Base58.encode(wire.copyOfRange(offset, offset + 33)), SaitoBalanceSnapshot.Slip(
            BigInteger(1, wire.copyOfRange(offset + 41, offset + 49)), BigInteger(1, wire.copyOfRange(offset + 49, offset + 57)),
            wire[offset + 57].toInt() and 255, BigInteger(1, wire.copyOfRange(offset + 33, offset + 41)), wire[offset + 58].toInt() and 255))
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}

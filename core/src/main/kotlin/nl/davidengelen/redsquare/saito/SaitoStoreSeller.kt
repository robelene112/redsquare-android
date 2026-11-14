package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger

data class StoreSellerListing(val summary: StoreSummary, val approved: Boolean, val sold: Boolean,
    val risk: String, val buyer: String, val note: String, val quantitySold: BigInteger)

/** Selected Store reports, not independent chain inclusion or spendable seller proceeds. */
object SaitoStoreSeller {
    fun inventoryRequest(seller: String): String {
        require(SaitoWallet.isPublicKey(seller))
        return JsonObject().apply {
            addProperty("request", "load-seller-inventory")
            add("data", JsonObject().apply { addProperty("module", "Store"); addProperty("seller", seller) })
        }.toString()
    }
    fun readInventory(response: String, seller: String): List<StoreSellerListing> {
        require(SaitoWallet.isPublicKey(seller) && response.toByteArray().size <= 2 * 1024 * 1024)
        val body = JsonParser.parseString(response).asJsonObject
        require(body.get("seller").asString == seller)
        val ids = HashSet<String>()
        return listOf(false, true).flatMap { sold ->
            val rows = body.getAsJsonArray(if (sold) "sold" else "active").also { require(it.size() <= 1000) }
            rows.map { value ->
                val row = value.asJsonObject
                val signature = row.get("listing_signature").asString
                require(ids.add(signature))
                val target = StoreLink(SaitoBackend.OWNER, seller, signature = signature)
                val summary = requireNotNull(SaitoStore.readOffer(JsonObject().apply { add("listing", row) }.toString(), target))
                val approval = row.get("approved").asString.toBigIntegerExact().also { require(it in BigInteger.ZERO..BigInteger.ONE) }
                val risk = row.get("risk")?.asString.orEmpty().also { require(it in setOf("", "Low", "Medium", "High", "Dangerous")) }
                val buyer = row.get("buyer")?.asString.orEmpty().also { require(it.isEmpty() || SaitoWallet.isPublicKey(it)) }
                val note = row.get("note")?.asString.orEmpty().also { require(it.length <= 2000) }
                val quantity = row.get("quantity_sold")?.asString?.toBigIntegerExact() ?: BigInteger.ZERO
                require(quantity.signum() >= 0 && quantity <= BigInteger("9007199254740991") && (!sold || summary.available == BigInteger.ZERO))
                StoreSellerListing(summary, approval == BigInteger.ONE, sold, risk, buyer, note, quantity)
            }
        }.also { require(it.size <= 1000) }
    }
    fun marketplaceRequest(signature: String): JsonObject {
        require(signature.matches(Regex("[0-9a-f]{128}")))
        return JsonObject().apply {
            addProperty("request", "submit-listing")
            add("data", JsonObject().apply { addProperty("module", "Store"); addProperty("signature", signature) })
        }
    }
    fun readSubmission(signature: String, response: String): Boolean {
        require(response.toByteArray().size <= 16 * 1024)
        val body = JsonParser.parseString(response).asJsonObject
        require(body.get("ok")?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true && body.get("signature").asString == signature)
        return body.get("approved").asString.toBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0
    }
    private fun String.toBigIntegerExact() = toBigDecimal().toBigIntegerExact()
}

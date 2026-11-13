package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import java.math.BigInteger
import java.math.BigDecimal

enum class StoreStock(val wire: String) { ACTIVE("active"), SOLD("sold") }
data class StoreQuery(val seller: String? = null, val category: StoreCategory? = null,
    val offset: Int = 0, val pageSize: Int = 24, val stock: StoreStock = StoreStock.ACTIVE)

data class StorePrice(val currency: String, val amount: BigDecimal)
data class StoreSummary(val nftId: String, val seller: String, val signature: String, val category: StoreCategory,
    val title: String, val description: String, val price: StorePrice?, val available: BigInteger, val total: BigInteger)
data class StorePage(val items: List<StoreSummary>, val offset: Int, val total: Int, val nextOffset: Int?, val ignored: Int)

/** Bounded Store reading; financial/listing submission contracts are separate explicit operations. */
object SaitoStore {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    /** Current catalogue/stock is the selected Store's report, not signed ownership or fulfilment. */
    fun readCatalogue(response: String, query: StoreQuery = StoreQuery()): StorePage {
        require(response.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        val body = requireNotNull(gson.fromJson(response, JsonObject::class.java))
        require(text(body, "public_key", 128) == query.seller.orEmpty() &&
            text(body, "category", 64) == query.category?.wire.orEmpty())
        val pagination = body.getAsJsonObject("pagination")
        val offset = unsigned(pagination.get("offset")).intValueExact()
        val size = unsigned(pagination.get("page_size")).intValueExact().also { require(it in 1..48) }
        val total = unsigned(pagination.get("total")).intValueExact()
        val next = pagination.get("has_next").let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); it.asBoolean }
        val nextOffset = if (next) Math.addExact(offset, size).also { require(it < total) } else null
        val rows = body.getAsJsonArray("listings").also { require(it.size() <= size) }
        var ignored = 0
        val items = linkedMapOf<String, StoreSummary>()
        for (value in rows) {
            val row = try {
                val entry = value.asJsonObject
                val seller = text(entry, "seller", 128).also {
                    require(SaitoWallet.isPublicKey(it) && (query.seller == null || query.seller == it))
                }
                val id = text(entry, "nft_id", 66).also { require(it.matches(Regex("[0-9a-f]{66}"))) }
                val signature = text(entry, "listing_signature", 128).also { require(it.matches(Regex("[0-9a-f]{128}"))) }
                val category = StoreCategory.entries.firstOrNull { it.wire == text(entry, "category", 64) } ?: StoreCategory.OTHER
                require(query.category == null || category == query.category)
                val ticker = text(entry, "web3_ticker", 16).uppercase(java.util.Locale.ROOT).ifEmpty { "SAITO" }
                require(ticker.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}")))
                val amount = if (ticker == "SAITO") BigDecimal(unsigned(entry.get("price")), 8)
                    else decimal(entry.get("web3_amount"))
                StoreSummary(id, seller, signature, category, text(entry, "title", 10_000),
                    text(entry, "description", 200_000), amount.takeIf { it.signum() > 0 }?.let { StorePrice(ticker, it) },
                    unsigned(entry.get("quantity_available")), unsigned(entry.get("quantity_total")))
            } catch (_: Exception) { ignored++; continue }
            val prior = items[row.signature]
            if (prior == null) items[row.signature] = row
            else if (prior != row) throw IllegalArgumentException("Conflicting Store listing rows")
            else ignored++
        }
        return StorePage(java.util.Collections.unmodifiableList(items.values.toList()), offset, total, nextOffset, ignored)
    }

    fun catalogueRequest(query: StoreQuery = StoreQuery()): String {
        require(query.seller == null || SaitoWallet.isPublicKey(query.seller))
        return JsonObject().apply {
            addProperty("request", "load-listings")
            add("data", JsonObject().apply {
                addProperty("module", "Store"); addProperty("public_key", query.seller.orEmpty())
                addProperty("category", query.category?.wire.orEmpty()); addProperty("offset", query.offset.coerceAtLeast(0))
                addProperty("page_size", if (query.pageSize <= 0) 24 else query.pageSize.coerceAtMost(48))
                addProperty("status", query.stock.wire)
            })
        }.toString()
    }

    private fun text(row: JsonObject, key: String, limit: Int): String {
        val value = row.get(key) ?: return ""
        if (value.isJsonNull) return ""
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.length <= limit)
        return value.asString
    }
    private fun decimal(value: JsonElement): BigDecimal {
        require(value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || value.asJsonPrimitive.isString) && value.asString.length <= 80)
        return value.asString.toBigDecimal().also { require(it.signum() >= 0 && it.precision() <= 40 && it.scale() in -24..24) }
    }
    private fun unsigned(value: JsonElement): BigInteger = decimal(value).toBigIntegerExact().also { require(it.bitLength() <= 64) }
}

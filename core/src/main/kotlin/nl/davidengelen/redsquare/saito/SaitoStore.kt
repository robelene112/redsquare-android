package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

enum class StoreStock(val wire: String) { ACTIVE("active"), SOLD("sold") }
data class StoreQuery(val seller: String? = null, val category: StoreCategory? = null,
    val offset: Int = 0, val pageSize: Int = 24, val stock: StoreStock = StoreStock.ACTIVE)

/** Bounded Store reading; financial/listing submission contracts are separate explicit operations. */
object SaitoStore {
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
}

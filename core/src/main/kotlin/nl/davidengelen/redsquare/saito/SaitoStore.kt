package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import java.math.BigInteger
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.util.Base64

enum class StoreStock(val wire: String) { ACTIVE("active"), SOLD("sold") }
data class StoreQuery(val seller: String? = null, val category: StoreCategory? = null,
    val offset: Int = 0, val pageSize: Int = 24, val stock: StoreStock = StoreStock.ACTIVE)

data class StorePrice(val currency: String, val amount: BigDecimal) {
    fun matches(other: StorePrice?): Boolean = other != null && currency == other.currency && amount.compareTo(other.amount) == 0
}
data class StoreSummary(val nftId: String, val seller: String, val signature: String, val category: StoreCategory,
    val title: String, val description: String, val price: StorePrice?, val available: BigInteger, val total: BigInteger)
data class StorePage(val items: List<StoreSummary>, val offset: Int, val total: Int, val nextOffset: Int?, val ignored: Int)

data class StoreProduct(val nftId: String, val seller: String, val signature: String, val title: String,
    val description: String, val category: StoreCategory, val price: StorePrice?, val quantity: BigInteger,
    val assetType: String, val envelope: String, val image: String? = null, val creator: String) {
    val browserOnly: Boolean get() = assetType in setOf("js", "css", "stack", "nwasm-nft-mod", "saito-app")
}

/** Bounded Store reading; financial/listing submission contracts are separate explicit operations. */
object SaitoStore {
    /** Authenticate original terms, not current unspent custody, Store acceptance or delivery. */
    fun readListing(summary: StoreSummary, envelope: String): StoreProduct? = readOriginalListing(summary.signature, envelope)?.takeIf {
        it.seller == summary.seller && it.nftId == summary.nftId
    }

    private fun readOriginalListing(signature: String, envelope: String): StoreProduct? = runCatching {
        val signed = SaitoEnvelope.decodeBound(envelope)
        require(signed.id == signature)
        val body = signed.body
        require(text(body, "module", 64) == "Store" && text(body, "request", 64) == "list-asset")
        val bytes = Base64.getDecoder().decode(gson.fromJson(envelope, JsonObject::class.java).get("t").asString)
        val inputs = ByteBuffer.wrap(bytes).getInt(0)
        val outputs = ByteBuffer.wrap(bytes).getInt(4)
        val source = (0 until (inputs - 2).coerceAtLeast(0)).mapNotNull { index ->
            val before = row(bytes, index); val middle = row(bytes, index + 1); val after = row(bytes, index + 2)
            if (before.position.type != 9 || after.position.type != 9 || middle.position.type !in setOf(0, 1)) null
            else SaitoNft.fromSnapshot(middle, before, after)
        }
        val nftId = source.first().id
        require(source.all { it.id == nftId && it.owner == signed.author } &&
            source.map { it.creator }.distinct().size == 1 && source.map { it.type }.distinct().size == 1)
        val script = SaitoAccessScript.parse(text(body, "access_script", 64 * 1024))
        require(text(body, "access_hash", 64) == script.hash)
        val custody = "00" + script.hash
        require(text(body, "p2sh_address", 66) == custody)
        val asset = source.first()
        var listed = BigInteger.ZERO
        for (index in inputs until (inputs + outputs - 2).coerceAtLeast(inputs)) {
            val before = row(bytes, index); val middle = row(bytes, index + 1); val after = row(bytes, index + 2)
            if (before.position.type != 9 || after.position.type != 9 || middle.position.type !in setOf(0, 1)) continue
            val id = Base58.decode(after.publicKey).joinToString("") { "%02x".format(it) }
            if (id == nftId && Base58.decode(middle.publicKey).joinToString("") { "%02x".format(it) } == custody) {
                require(before.publicKey == asset.creator)
                listed += before.position.amount
            }
        }
        val listing = body.getAsJsonObject("listing")
        val quantity = unsigned(listing.get("quantity_total"))
        require(quantity.signum() > 0 && listed == quantity && quantity <= source.fold(BigInteger.ZERO) { sum, nft -> sum + nft.quantity })
        val category = StoreCategory.entries.firstOrNull { it.wire == text(listing, "category", 64) }
            ?: StoreCategory.forType(asset.type)
        val ticker = text(listing, "web3_ticker", 16).uppercase(java.util.Locale.ROOT).ifEmpty { "SAITO" }
        require(ticker.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}")))
        val amount = if (ticker == "SAITO") (listing.get("price") ?: body.get("price"))?.let {
            decimal(it).also { value ->
                require(value.multiply(BigDecimal("100000000")).toBigIntegerExact().bitLength() <= 64)
            }
        } else listing.get("web3_amount")?.let(::decimal)
        StoreProduct(asset.id, signed.author, signed.id,
            text(listing, "title", 10_000).ifBlank { text(body, "title", 10_000) },
            text(listing, "description", 200_000).ifBlank { text(body, "description", 200_000) }, category,
            amount?.takeIf { it.signum() > 0 }?.let { StorePrice(ticker, it) }, quantity, asset.type, envelope, body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("image")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(RedSquareImages::safeSource), asset.creator)
    }.getOrNull()

    fun listingRequest(target: StoreLink): String {
        require(target.signature != null || (target.seller != null && target.nftId != null))
        require(StoreLink.parse(target.url()) == target.copy(category = ""))
        return JsonObject().apply {
            addProperty("request", "load-listing")
            add("data", JsonObject().apply {
                addProperty("module", "Store")
                if (target.signature != null) addProperty("signature", target.signature)
                else { addProperty("seller", target.seller); addProperty("nft_id", target.nftId) }
            })
        }.toString()
    }

    fun readOffer(response: String, target: StoreLink): StoreSummary? {
        require(response.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        val body = requireNotNull(gson.fromJson(response, JsonObject::class.java))
        if (!body.has("listing")) { require(text(body, "err", 2000) == "Listing unavailable"); return null }
        // Use the same bounded summary reader for the protocol's single-offer response.
        val page = JsonObject().apply {
            addProperty("public_key", target.seller.orEmpty()); addProperty("category", "")
            add("listings", com.google.gson.JsonArray().apply { add(body.get("listing")) })
            add("pagination", JsonObject().apply {
                addProperty("offset", 0); addProperty("page_size", 1); addProperty("total", 1); addProperty("has_next", false)
            })
        }
        return readCatalogue(page.toString(), StoreQuery(seller = target.seller)).items.singleOrNull()?.takeIf {
            (target.nftId == null || target.nftId == it.nftId) && (target.signature == null || target.signature == it.signature)
        }
    }

    fun listingArchiveRequest(signature: String): String {
        require(signature.matches(Regex("[0-9a-f]{128}")))
        return JsonObject().apply {
            addProperty("request", "archive")
            add("data", JsonObject().apply {
                addProperty("request", "load"); addProperty("sig", signature); addProperty("limit", 1)
            })
        }.toString()
    }

    fun readListingArchive(summary: StoreSummary, response: String): StoreProduct? =
        readArchiveRows(response, { requireNotNull(readListing(summary, it)) }, { true }, { it.signature }).entries.singleOrNull()

    /** Historical original terms stay readable; this does not turn them into an available offer. */
    fun readOriginalListingArchive(signature: String, response: String): StoreProduct? {
        require(signature.matches(Regex("[0-9a-f]{128}")))
        return readArchiveRows(response, { requireNotNull(readOriginalListing(signature, it)) }, { true }, { it.signature }).entries.singleOrNull()
    }

    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    /** Current catalogue/stock is the selected Store's report, not signed ownership or fulfilment. */
    fun readCatalogue(response: String, query: StoreQuery = StoreQuery()): StorePage {
        require(response.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        val body = requireNotNull(gson.fromJson(response, JsonObject::class.java))
        require(text(body, "public_key", 128) == query.seller.orEmpty() &&
            text(body, "category", 64) == query.category?.wire.orEmpty())
        val pagination = body.getAsJsonObject("pagination")
        val offset = pageInteger(pagination.get("offset"))
        val size = pageInteger(pagination.get("page_size")).also { require(it in 1..48) }
        val total = pageInteger(pagination.get("total"))
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
    private fun pageInteger(value: JsonElement): Int = unsigned(value).also { require(it.bitLength() <= 31) }.toInt()

    private fun unsigned(value: JsonElement): BigInteger = decimal(value).toBigIntegerExact().also { require(it.bitLength() <= 64) }

    private fun row(bytes: ByteArray, index: Int): SaitoNft.Row {
        val offset = 93 + index * 59
        return SaitoNft.Row(Base58.encode(bytes.copyOfRange(offset, offset + 33)), SaitoBalanceSnapshot.Slip(
            BigInteger(1, bytes.copyOfRange(offset + 41, offset + 49)), BigInteger(1, bytes.copyOfRange(offset + 49, offset + 57)),
            bytes[offset + 57].toInt() and 255, BigInteger(1, bytes.copyOfRange(offset + 33, offset + 41)), bytes[offset + 58].toInt() and 255))
    }
}

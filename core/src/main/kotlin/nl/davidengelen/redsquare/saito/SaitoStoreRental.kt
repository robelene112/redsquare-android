package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** The protected file and immutable terms carried by a disposable Store rental NFT. */
data class StoreRentalSource(val creator: String, val fileId: String, val name: String,
    val masterSource: String, val hours: Int, val rights: String = "all") {
    init {
        require(SaitoWallet.isPublicKey(creator) && fileId.matches(Regex("[0-9a-f]{128}")))
        require(name.isNotBlank() && name.length <= 1024 && hours in 1..24 && rights == "all")
        require(SaitoAccessScript.parse(masterSource).withoutWitnesses().hash == SaitoVaultRules.rental(creator).hash)
    }
    val durationMillis: Long get() = hours * 3_600_000L
    fun data(image: String): JsonObject = JsonObject().apply {
        addProperty("module", "Store"); addProperty("link", "https://saito.io/vault")
        addProperty("nft_type", "vault-nft-rental"); addProperty("filename", name); addProperty("file_id", fileId)
        addProperty("file_access_script", masterSource); addProperty("duration_hours", hours)
        addProperty("duration_ms", durationMillis); addProperty("rights", rights); addProperty("image", image)
    }
    companion object {
        fun fromKey(wallet: SaitoWallet, key: VaultKey, hours: Int): StoreRentalSource {
            require(key.holding.type == "vault-nft-rental" && key.holding.owner == wallet.publicKey && key.holding.creator == wallet.publicKey)
            return StoreRentalSource(wallet.publicKey, key.fileId, key.name, requireNotNull(key.customScript), hours)
        }
        fun read(creator: String, data: JsonObject): StoreRentalSource {
            require(data.get("module").asString == "Store" && data.get("link").asString == "https://saito.io/vault" && data.get("nft_type").asString == "vault-nft-rental")
            val script = data.get("file_access_script").let { if (it.isJsonPrimitive) it.asString else it.toString() }
            val hours = data.get("duration_hours").asString.toInt()
            return StoreRentalSource(creator, data.get("file_id").asString, data.get("filename").asString,
                script, hours, data.get("rights").asString).also {
                require(data.get("duration_ms").asString.toLong() == it.durationMillis)
            }
        }
    }
}

/** A delivered temporary right is authenticated against the original order and seller delegation. */
object SaitoStoreRental {
    data class Offer(val source: StoreRentalSource, val path: SaitoVaultRental, val expiresAt: Long)
    fun offer(product: StoreProduct): Offer? {
        if (product.assetType != "store-nft-rental") return null
        val authenticated = SaitoStoreLineage.restore(product)
        val body = SaitoStoreLineage.body(authenticated.envelope)
        val data = body.getAsJsonObject("data")
        val source = StoreRentalSource.read(product.creator, data)
        val path = SaitoVaultRental.parse(source.creator, source.fileId, source.masterSource, data.getAsJsonArray("path").toString())
        val expires = data.get("expires_at").asString.toLong().also { require(it >= 0) }
        require(body.getAsJsonObject("listing").get("listing_mode").asString == "rent")
        return Offer(source, path, expires)
    }
    fun entitlement(purchase: SaitoStorePayment, product: StoreProduct, receipt: String): SaitoVaultRental {
        require(purchase.nftId == product.nftId)
        val original = requireNotNull(offer(product))
        val received = SaitoStoreReceipt.read(purchase, receipt)
        val asset = requireNotNull(received.asset)
        require(received.kind == SaitoStoreReceipt.Kind.FULFILLMENT && asset.type == "store-nft-rental" && asset.creator == product.creator)
        val bytes = java.util.Base64.getDecoder().decode(receipt)
        val header = java.nio.ByteBuffer.wrap(bytes)
        val start = 93 + 59 * (header.getInt(0) + header.getInt(4))
        val body = JsonParser.parseString(bytes.copyOfRange(start, start + header.getInt(8)).toString(Charsets.UTF_8)).asJsonObject
        val data = body.getAsJsonObject("data")
        require(StoreRentalSource.read(asset.creator, data) == original.source)
        val rows = data.getAsJsonArray("path")
        val prefix = JsonParser.parseString(original.path.source).asJsonArray
        require(rows.size() == prefix.size() + 1 && (0 until prefix.size()).all { rows[it] == prefix[it] })
        val path = SaitoVaultRental.parse(asset.creator, original.source.fileId, original.source.masterSource, rows.toString())
        val grant = requireNotNull(path.grant)
        require(grant.renter == purchase.buyer && grant.expiresAt == original.expiresAt && data.get("expires_at").asLong == grant.expiresAt)
        return path
    }
}

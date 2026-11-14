package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** The protected file and immutable terms carried by a disposable Store rental NFT. */
data class StoreRentalSource(val creator: String, val fileId: String, val name: String,
    val masterSource: String, val hours: Int, val rights: String = "all", val link: String = "https://saito.io/vault") {
    init {
        require(SaitoWallet.isPublicKey(creator) && fileId.matches(Regex("[0-9a-f]{128}")))
        require(name.isNotBlank() && name.length <= 1024 && hours in 1..24 && rights == "all")
        val uri = java.net.URI(link)
        require(link.length <= 4096 && link.none { it.isWhitespace() || it.isISOControl() } && uri.scheme?.lowercase() in setOf("http", "https") &&
            uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null && uri.rawQuery == null && uri.path.removeSuffix("/") == "/vault")
        require(SaitoAccessScript.parse(masterSource).withoutWitnesses().hash == SaitoVaultRules.rental(creator).hash)
    }
    val durationMillis: Long get() = hours * 3_600_000L
    fun data(image: String): JsonObject = JsonObject().apply {
        addProperty("module", "Store"); addProperty("link", link)
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
            require(data.get("module").asString == "Store" && data.get("nft_type").asString == "vault-nft-rental")
            val script = data.get("file_access_script").let { if (it.isJsonPrimitive) it.asString else it.toString() }
            val hours = data.get("duration_hours").asString.toInt()
            return StoreRentalSource(creator, data.get("file_id").asString, data.get("filename").asString,
                script, hours, data.get("rights").asString, data.get("link").asString).also {
                require(data.get("duration_ms").asString.toLong() == it.durationMillis)
            }
        }
    }
}

/** A delivered temporary right is authenticated against the original order and seller delegation. */
object SaitoStoreRental {
    data class PublicReceipt(val inventory: String, val payment: String)
    /** Bounded public lookup hints. heldEntitlement authenticates the receipt after resolving them. */
    fun publicReceipt(envelope: String): PublicReceipt {
        require(envelope.toByteArray().size <= 2 * 1024 * 1024)
        val body = SaitoStoreLineage.body(envelope)
        require(body.get("module")?.asString == "Store" && body.get("request")?.asString == "list-asset")
        val sale = body.getAsJsonObject("fulfill_sale")
        val contributors = sale.getAsJsonArray("listing_signatures")?.also { require(it.size() <= 16) }?.map { it.asString }.orEmpty()
        return PublicReceipt(contributors.lastOrNull() ?: sale.get("prior_inventory").asString, sale.get("sale_signature").asString).also {
            require(listOf(it.inventory, it.payment).all { id -> id.matches(Regex("[0-9a-f]{128}")) })
        }
    }
    fun heldEntitlement(holding: SaitoNft, product: StoreProduct, receipt: String, paymentArchive: String): SaitoVaultRental {
        require(holding.type == "store-nft-rental" && holding.id == product.nftId && holding.creator == product.creator)
        return creatorEntitlement(product, receipt, paymentArchive).also {
            require(requireNotNull(it.grant).renter == holding.owner) { "The first renter retains access after delegation" }
        }
    }

    /** Public signed history is sufficient for creator recovery; no buyer invoice is required. */
    fun creatorEntitlement(product: StoreProduct, receiptEnvelope: String, paymentArchive: String): SaitoVaultRental {
        publicReceipt(receiptEnvelope)
        SaitoStoreLineage.restore(product)
        val sale = SaitoStoreLineage.body(receiptEnvelope).getAsJsonObject("fulfill_sale")
        val paymentId = sale.get("sale_signature").asString.also { require(it.matches(Regex("[0-9a-f]{128}"))) }
        val contributors = sale.getAsJsonArray("listing_signatures")?.map { it.asString }.orEmpty()
        require(sale.get("prior_inventory")?.asString == product.signature || product.signature in contributors)
        val rules = JsonParser.parseString(SaitoStoreLineage.body(product.envelope).get("access_script").asString).asJsonObject
        val keys = rules.getAsJsonArray("publickeys").map { it.asString }
        require(keys.size == 2 && keys[0] == product.seller && SaitoWallet.isPublicKey(keys[1]))
        val payment = requireNotNull(readArchiveRows(paymentArchive, { it }, {
            runCatching { SaitoEnvelope.originalNetworkBytes(it).copyOfRange(16, 80).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) } == paymentId }.getOrDefault(false)
        }, { it }).entries.singleOrNull())
        val (funded, price) = SaitoStoreLineage.publicPayment(SaitoEnvelope.originalNetworkBytes(payment), keys[1])
        require(price.matches(product.price))
        return entitlement(funded, product, java.util.Base64.getEncoder().encodeToString(SaitoEnvelope.originalNetworkBytes(receiptEnvelope)))
    }

    data class Offer(val source: StoreRentalSource, val path: SaitoVaultRental, val expiresAt: Long) {
        /** An already assigned remainder cannot create a fresh FIRST-renter lease. */
        fun availableAt(now: Long): Boolean = now >= 0 && now < expiresAt && path.grant == null
    }
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

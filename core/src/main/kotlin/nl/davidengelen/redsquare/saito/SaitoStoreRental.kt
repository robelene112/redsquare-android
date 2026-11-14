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

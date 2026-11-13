package nl.davidengelen.redsquare.saito

import java.net.URI
import java.net.URLDecoder

enum class StoreCategory(val wire: String, private vararg val types: String) {
    APPS("Apps & Games", "stack", "nwasm-nft-mod", "saito-app"),
    EXTENSIONS("Extensions", "js"), THEMES("Themes", "css"), IMAGES("Image NFTs", "image"),
    TOKENS("Tokens", "token"), ACCESS("Access Keys", "vault-nft-key", "vault-nft-rental", "store-nft-rental"),
    MERCHANDISE("Merchandise"), OTHER("Other");

    companion object {
        fun forType(type: String): StoreCategory = entries.firstOrNull { type.trim() in it.types } ?: OTHER
    }
}

/** A read destination only. It never selects a saved node or submits a Store operation. */
data class StoreLink(val backend: SaitoBackend, val seller: String? = null, val nftId: String? = null,
    val signature: String? = null, val category: String = "") : java.io.Serializable {
    companion object {
        fun parse(url: String): StoreLink? = runCatching {
            require(url.length <= 4096 && url.none { it.isWhitespace() || it.isISOControl() })
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http")
            require(uri.rawUserInfo == null && uri.rawFragment == null &&
                (uri.port == -1 || uri.port == if (scheme == "https") 443 else 80))
            val backend = SaitoBackend.entries.singleOrNull { it.domain.equals(uri.host, true) } ?: return null
            require(uri.rawPath == uri.path)
            val query = uri.rawQuery?.split('&')?.map { item ->
                URLDecoder.decode(item.substringBefore('='), "UTF-8") to URLDecoder.decode(item.substringAfter('=', ""), "UTF-8")
            }.orEmpty()
            require(query.all { (key, value) -> (key == "type" || key.startsWith("utm_")) && value.none { it.isISOControl() } })
            require(query.count { it.first == "type" } <= 1)
            val type = query.singleOrNull { it.first == "type" }?.second.orEmpty()
            val category = if (type.isEmpty()) "" else StoreCategory.forType(type).wire
            val parts = uri.path.removeSuffix("/").split('/')
            require(parts.size in 2..4 && parts[0].isEmpty() && parts[1] == "store")
            if (parts.size == 2) StoreLink(backend, category = category)
            else if (parts.size == 4 && parts[2] == "listing") {
                require(parts[3].matches(Regex("[0-9a-f]{128}")))
                StoreLink(backend, signature = parts[3], category = category)
            } else {
                val seller = parts[2].also { require(SaitoWallet.isPublicKey(it)) }
                val nft = parts.getOrNull(3)?.also { require(it.matches(Regex("[0-9a-f]{66}"))) }
                StoreLink(backend, seller, nft, category = category)
            }
        }.getOrNull()
    }
}

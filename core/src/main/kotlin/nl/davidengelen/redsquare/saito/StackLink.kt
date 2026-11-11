package nl.davidengelen.redsquare.saito

import java.net.URI

/** An approved public Stack reading destination, never a publication or access action. */
data class StackLink(val backend: SaitoBackend, val author: String? = null, val signature: String? = null) : java.io.Serializable {
    companion object {
        fun parse(url: String): StackLink? = runCatching {
            require(url.length <= 4096 && url.none { it.isWhitespace() || it.isISOControl() })
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http")
            require(uri.rawUserInfo == null && (uri.port == -1 || uri.port == if (scheme == "https") 443 else 80))
            val backend = SaitoBackend.entries.singleOrNull { it.domain.equals(uri.host, true) } ?: return null
            require(uri.rawPath == uri.path)
            require(uri.rawQuery == null || uri.rawQuery.split('&').all { it.substringBefore('=').startsWith("utm_") })
            val parts = uri.path.removeSuffix("/").split('/')
            require(parts.size in 2..4 && parts[0].isEmpty() && parts[1] == "stack")
            val author = parts.getOrNull(2)?.also { require(SaitoWallet.isPublicKey(it)) }
            val signature = parts.getOrNull(3)?.also { require(Regex("[0-9a-f]{128}").matches(it)) }
            StackLink(backend, author, signature)
        }.getOrNull()
    }
}

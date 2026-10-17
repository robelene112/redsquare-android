package nl.davidengelen.redsquare.gateway

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import java.io.Closeable

/** Read-only Archive access using a gateway-owned identity, never a subscriber's signing key.
 * Each read has a twelve-second connection/authentication/response budget. Close after use;
 * a failed connection is not reusable. A bounded head read does not promise complete history.
 * localOrigin is restricted to an owned loopback HTTP fixture, not another backend choice.
 */
class GatewayArchive(identity: SaitoWallet, val backend: SaitoBackend,
    localOrigin: String? = null, fixtureRegistrar: Boolean = false) : Closeable {
    init { require(!fixtureRegistrar || localOrigin != null) }
    private val registrar = if (fixtureRegistrar) "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" else SaitoRegistry.REGISTRAR
    private val peer = GatewayPeer(identity, backend, localOrigin)
    fun recentSocial(): ArchiveFeedPage = RedSquareFeed.readSearch(archive(null))

    internal data class VerifiedSocial(val post: FeedPost, val parentAuthor: String?, val namedRecipients: Set<String>, val unavailableNames: Boolean)

    /** Head rows are discovery hints. Exact candidate and parent reads establish current availability. */
    internal fun verifiedSocial(signatures: List<String>? = null): List<VerifiedSocial> {
        // Complete independent Archive evidence first. Registry shares this socket and may disconnect it.
        val discovered = signatures ?: recentSocial().posts.map { it.id }
        require(discovered.size <= 100 && discovered.all { it.matches(Regex("[0-9a-f]{128}")) })
        val verified = discovered.distinct().mapNotNull { signature ->
            val post = exact(signature) ?: return@mapNotNull null
            if (SaitoEnvelope.decode(post.envelope).body.get("request").asString != "create tweet") return@mapNotNull null
            VerifiedSocial(post, post.parentId.takeIf { it.isNotEmpty() }?.let { exact(it)?.author }, emptySet(), false)
        }
        return verified.map { candidate ->
            val post = candidate.post
            val names = NAME_MENTION.findAll(post.text).map { it.groupValues[1] }.distinct().take(51).toList()
            var unavailableNames = names.size > 50
            val recipients = (if (unavailableNames) emptyList() else names).mapNotNull { identifier ->
                val query = JsonObject().apply {
                    addProperty("request", "registry query")
                    add("data", JsonObject().apply { addProperty("request", "registry namecheck"); addProperty("identifier", identifier) })
                }
                try {
                    SaitoRegistry.readNamecheck(peer.request(query.toString(), "registry", true), identifier, registrar)
                        ?.takeIf { it.active }?.publicKey
                } catch (failure: Exception) {
                    if (Thread.currentThread().isInterrupted) throw failure
                    unavailableNames = true
                    null // Missing optional name evidence cannot invalidate the independent reply/public-key evidence.
                }
            }.toSet()
            candidate.copy(namedRecipients = recipients, unavailableNames = unavailableNames)
        }
    }

    private fun exact(signature: String): FeedPost? {
        val page = RedSquareFeed.readTarget(archive(signature), signature)
        require(page.ignored == 0 && page.deletions.isEmpty() && page.rowCount == page.posts.size && page.posts.size <= 1)
        return page.posts.singleOrNull()
    }

    private fun archive(signature: String?): String {
        val criteria = JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "RedSquare")
            addProperty("flagged_ne", 1); addProperty("limit", if (signature == null) 100 else 1)
            if (signature != null) addProperty("sig", signature)
        }
        return peer.request(JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }.toString(), "archive")
    }

    override fun close() = peer.close()

    companion object {
        private val NAME_MENTION = Regex("(?<![\\p{L}\\p{N}_@])@([A-Za-z0-9]{1,45}@saito)(?![\\p{L}\\p{N}_@])")
    }
}

package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Short-lived provider permission. It carries no account, filename, mime type, or file key. */
class SpaceFileGrant private constructor(
    val envelope: String,
    val space: String,
    val lease: String,
    val action: Action,
    val objectId: String,
    val sha256: String,
    val bytes: Long,
    val issuedAt: Long,
    val expiresAt: Long,
) {
    enum class Action(val wire: String) { PUT("put"), GET("get") }

    fun requireCurrent(at: Long) {
        require(at in issuedAt until expiresAt) { "File transfer permission expired" }
    }

    override fun toString() = "Signed Space file transfer permission"

    companion object {
        fun create(authority: SaitoWallet, provider: String, space: String, lease: String, action: Action,
            objectId: String, sha256: String, bytes: Long, now: Long, expiresAt: Long): SpaceFileGrant {
            val body = JsonObject().apply {
                addProperty("module", "SpacesStorage")
                addProperty("version", 1)
                addProperty("request", "file")
                addProperty("space", space)
                addProperty("lease", lease)
                addProperty("action", action.wire)
                addProperty("object", objectId)
                addProperty("sha256", sha256)
                addProperty("bytes", bytes)
                addProperty("expires_at", expiresAt)
            }
            return read(SaitoTransaction.create(authority, body, now, listOf(provider)).envelope(),
                authority.publicKey, provider)
        }

        fun read(envelope: String, authority: String, provider: String): SpaceFileGrant {
            require(envelope.length <= 8192 && authority != provider)
            val signed = SaitoEnvelope.decode(envelope)
            require(signed.author == authority && signed.recipients == listOf(authority, provider))
            val body = signed.body
            require(body.keySet() == setOf("module", "version", "request", "space", "lease", "action", "object",
                "sha256", "bytes", "expires_at"))
            require(body.storageText("module") == "SpacesStorage" && body.get("version").toString() == "1" &&
                body.storageText("request") == "file")
            val action = Action.entries.singleOrNull { it.wire == body.storageText("action") }
            val expiresAt = body.storageNumber("expires_at")
            require(expiresAt - signed.createdAt in 1..MAX_GRANT_MILLIS)
            return SpaceFileGrant(envelope, body.storageText("space").also { require(it.matches(FILE_ID)) },
                body.storageText("lease").also { require(it.matches(FILE_SIGNATURE)) }, requireNotNull(action),
                body.storageText("object").also { require(it.matches(FILE_ID)) },
                body.storageText("sha256").also { require(it.matches(FILE_ID)) },
                body.storageNumber("bytes").also { require(it in 16..SpaceFileCipher.MAX_CIPHERTEXT_BYTES) },
                signed.createdAt, expiresAt)
        }

        const val MAX_GRANT_MILLIS = 5 * 60_000L
    }
}

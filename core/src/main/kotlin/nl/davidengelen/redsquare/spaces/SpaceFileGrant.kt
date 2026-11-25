package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.security.SecureRandom

/** Short-lived provider permission. It carries no account, filename, mime type, or file key. */
class SpaceFileGrant private constructor(
    val envelope: String,
    val id: String,
    val space: String,
    val lease: String,
    val action: Action,
    val objectId: String,
    val sha256: String,
    val bytes: Long,
    val issuedAt: Long,
    val expiresAt: Long,
) {
    enum class Action(val wire: String) { PUT("put"), GET("get"), DELETE("delete"), STATUS("status") }

    fun requireCurrent(at: Long, allowClockSkew: Boolean = false) {
        val earliest = if (allowClockSkew) issuedAt - CLOCK_SKEW_MILLIS else issuedAt
        require(at in earliest until expiresAt) { "File transfer permission is outside its time window" }
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
                if (action != Action.STATUS) {
                    addProperty("object", objectId)
                    addProperty("sha256", sha256)
                    addProperty("bytes", bytes)
                } else require(objectId.isEmpty() && sha256.isEmpty() && bytes == 0L)
                addProperty("expires_at", expiresAt)
                addProperty("nonce", ByteArray(32).also(SecureRandom()::nextBytes).fileHex())
            }
            return read(SaitoTransaction.create(authority, body, now, listOf(provider)).envelope(),
                authority.publicKey, provider)
        }

        fun read(envelope: String, authority: String, provider: String): SpaceFileGrant {
            require(envelope.length <= 8192 && authority != provider)
            val signed = SaitoEnvelope.decode(envelope)
            require(signed.author == authority && signed.recipients == listOf(authority, provider))
            val body = signed.body
            require(body.storageText("module") == "SpacesStorage" && body.get("version").toString() == "1" &&
                body.storageText("request") == "file")
            val action = requireNotNull(Action.entries.singleOrNull { it.wire == body.storageText("action") })
            val hasObject = action != Action.STATUS
            require(body.keySet() == setOf("module", "version", "request", "space", "lease", "action", "expires_at", "nonce") +
                if (hasObject) setOf("object", "sha256", "bytes") else emptySet())
            require(body.storageText("nonce").matches(FILE_ID))
            val expiresAt = body.storageNumber("expires_at")
            require(expiresAt - signed.createdAt in 1..MAX_GRANT_MILLIS)
            return SpaceFileGrant(envelope, signed.id, body.storageText("space").also { require(it.matches(FILE_ID)) },
                body.storageText("lease").also { require(it.matches(FILE_SIGNATURE)) }, action,
                if (hasObject) body.storageText("object").also { require(it.matches(FILE_ID)) } else "",
                if (hasObject) body.storageText("sha256").also { require(it.matches(FILE_ID)) } else "",
                if (hasObject) body.storageNumber("bytes").also {
                    require(it in SpaceFileCipher.MIN_CIPHERTEXT_BYTES..SpaceFileCipher.MAX_CIPHERTEXT_BYTES)
                } else 0L,
                signed.createdAt, expiresAt)
        }

        const val MAX_GRANT_MILLIS = 5 * 60_000L
        const val CLOCK_SKEW_MILLIS = 30_000L
    }
}

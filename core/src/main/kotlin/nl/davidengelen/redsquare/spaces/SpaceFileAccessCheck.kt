package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.security.SecureRandom

/** The provider asks only whether an opaque transfer is still authorized. */
class SpaceFileAccessCheck private constructor(
    val id: String,
    val provider: String,
    val permission: SpaceFileGrant,
) {
    companion object {
        fun request(provider: SaitoWallet, authority: String, grant: SpaceFileGrant, now: Long): SaitoTransaction =
            SaitoTransaction.create(provider, JsonObject().apply {
                addProperty("module", "SpacesStorage")
                addProperty("request", "check-file-permission")
                addProperty("version", 1)
                addProperty("permission", grant.envelope)
                addProperty("nonce", ByteArray(32).also(SecureRandom()::nextBytes).fileHex())
            }, now, listOf(authority))

        fun read(request: String, authority: String, now: Long): SpaceFileAccessCheck {
            require(request.length <= 16 * 1024)
            val original = SaitoEnvelope.decode(request)
            val body = original.body
            require(original.author != authority && original.recipients == listOf(original.author, authority))
            require(original.createdAt >= now - CHECK_AGE && original.createdAt <= now + CHECK_AGE)
            require(body.keySet() == setOf("module", "request", "version", "permission", "nonce"))
            require(body.storageText("module") == "SpacesStorage" && body.storageNumber("version") == 1L &&
                body.storageText("request") == "check-file-permission" && body.storageText("nonce").matches(FILE_ID))
            val permission = SpaceFileGrant.read(body.storageText("permission"), authority, original.author)
            require(permission.action in setOf(SpaceFileGrant.Action.GET, SpaceFileGrant.Action.PUT))
            return SpaceFileAccessCheck(original.id, original.author, permission)
        }

        const val CHECK_AGE = 30_000L
    }

    fun reply(authority: SaitoWallet, allowed: Boolean, now: Long): String =
        SaitoTransaction.create(authority, JsonObject().apply {
            addProperty("module", "SpacesStorage")
            addProperty("request", "file-permission-status")
            addProperty("version", 1)
            addProperty("check", id)
            addProperty("permission", permission.id)
            addProperty("allowed", allowed)
        }, now, listOf(provider)).envelope()

    fun requireAllowed(reply: String, authority: String, now: Long) {
        require(reply.length <= 8192)
        val signed = SaitoEnvelope.decode(reply)
        val body = signed.body
        require(signed.author == authority && signed.recipients == listOf(authority, provider))
        require(signed.createdAt >= now - CHECK_AGE && signed.createdAt <= now + CHECK_AGE)
        require(body.keySet() == setOf("module", "request", "version", "check", "permission", "allowed"))
        require(body.storageText("module") == "SpacesStorage" && body.storageNumber("version") == 1L &&
            body.storageText("request") == "file-permission-status" && body.storageText("check") == id &&
            body.storageText("permission") == permission.id)
        val allowed = body.get("allowed")
        require(allowed.isJsonPrimitive && allowed.asJsonPrimitive.isBoolean && allowed.asBoolean) {
            "The Space authority no longer permits this transfer"
        }
    }
}

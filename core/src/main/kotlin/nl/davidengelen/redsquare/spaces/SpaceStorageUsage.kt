package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet

/** A fresh signed provider observation. Quoted capacity alone says nothing about free space. */
class SpaceStorageUsage private constructor(val used: Long, val capacity: Long, val expiresAt: Long,
    val observedAt: Long) {
    val available get() = (capacity - used).coerceAtLeast(0)

    companion object {
        fun sign(wallet: SaitoWallet, authority: String, grant: SpaceFileGrant, used: Long,
            capacity: Long, expiresAt: Long, at: Long): String {
            require(grant.action == SpaceFileGrant.Action.STATUS)
            val body = JsonObject().apply {
                addProperty("module", "SpacesStorage")
                addProperty("version", 1)
                addProperty("request", "usage")
                addProperty("permission", grant.id)
                addProperty("lease", grant.lease)
                addProperty("space", grant.space)
                addProperty("used", used)
                addProperty("capacity", capacity)
                addProperty("expires_at", expiresAt)
            }
            return SaitoTransaction.create(wallet, body, at, listOf(authority)).envelope()
        }

        fun read(envelope: String, lease: SpaceFileLease, grant: SpaceFileGrant, now: Long): SpaceStorageUsage {
            require(envelope.length <= 8192 && grant.action == SpaceFileGrant.Action.STATUS && grant.lease == lease.id)
            grant.requireCurrent(now, allowClockSkew = true)
            val offer = lease.offer
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(signed.author == offer.provider && signed.recipients == listOf(offer.provider, offer.service))
            require(body.keySet() == setOf("module", "version", "request", "permission", "lease", "space",
                "used", "capacity", "expires_at"))
            require(body.storageText("module") == "SpacesStorage" && body.storageNumber("version") == 1L &&
                body.storageText("request") == "usage" && body.storageText("permission") == grant.id &&
                body.storageText("lease") == lease.id && body.storageText("space") == offer.space &&
                body.storageNumber("capacity") == offer.capacityBytes && body.storageNumber("expires_at") == offer.expiresAt)
            require(signed.createdAt in (grant.issuedAt - SpaceFileGrant.CLOCK_SKEW_MILLIS) until grant.expiresAt &&
                signed.createdAt <= now + SpaceFileGrant.CLOCK_SKEW_MILLIS && now < offer.expiresAt)
            return SpaceStorageUsage(body.storageNumber("used"), offer.capacityBytes, offer.expiresAt, signed.createdAt)
        }
    }
}

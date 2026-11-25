package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoEnvelope

/** Provider's commitment to already accepted terms. The original consent stays independently verifiable. */
class SpaceFileLease private constructor(val envelope: String, val id: String, val approval: SpaceStorageApproval) {
    val offer get() = approval.offer

    override fun toString() = "Signed Space file storage lease"

    companion object {
        fun read(approval: SpaceStorageApproval, envelope: String): SpaceFileLease {
            require(envelope.length <= 8192)
            val offer = approval.offer
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(signed.author == offer.provider &&
                signed.recipients == listOf(offer.provider, offer.owner, offer.service).distinct())
            require(signed.createdAt < offer.expiresAt)
            require(body.keySet() == setOf("module", "version", "request", "approval", "offer", "space",
                "capacity_bytes", "expires_at"))
            require(body.storageText("module") == "SpacesStorage" && body.get("version").toString() == "1" &&
                body.storageText("request") == "lease" && body.storageText("approval") == approval.operation &&
                body.storageText("offer") == offer.id && body.storageText("space") == offer.space &&
                body.storageNumber("capacity_bytes") == offer.capacityBytes && body.storageNumber("expires_at") == offer.expiresAt)
            return SpaceFileLease(envelope, signed.id, approval)
        }
    }
}

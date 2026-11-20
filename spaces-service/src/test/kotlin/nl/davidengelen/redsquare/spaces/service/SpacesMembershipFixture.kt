package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock

/** Public owned community: Android drives both parties; the separate member
 * observes the canonical result. This fixture does not claim private handover. */
internal class SpacesMembershipFixture(private val owner: SpaceClient, private val reader: SpaceClient,
    private val peer: ControlledFeedPeer, private val remote: SpacesServiceStore,
    private val service: SaitoWallet, private val clock: Clock, private val root: Path) {
    private lateinit var space: String
    private val fixtures = JsonObject()
    private val kinds = setOf("set-role", "remove-member", "ban-member", "unban-member", "leave",
        "propose-owner", "accept-owner", "decline-owner", "cancel-owner")
    val names = mutableMapOf<String, String>()

    suspend fun seed() {
        space = owner.create(SpaceProfile("Native Membership Lab", "Owned member decisions and explicit two-party ownership.",
            visibility = SpaceVisibility.PUBLIC), listOf(SpaceChannelProfile("general")))
        owner.flush(space)
        repeat(18) { owner.send(space, "Membership message $it") }; owner.flush(space)
        reader.openPublic(space); reader.joinPublic(space); reader.flush(space)
        fixtures.addProperty("space", space); fixtures.addProperty("link", owner.publicLink(space))
        fixtures.addProperty("general", owner.community(space).channel)
        fixtures.addProperty("owner", SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey)
        fixtures.addProperty("member", SaitoWallet.fromPrivateKey("0".repeat(63) + "4").publicKey)
        for ((scalar, label) in listOf("5" to "removed", "6" to "banned")) {
            val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + scalar)
            names[wallet.publicKey] = if (label == "removed") "RemoveFromTheLab@saito" else "BanFromTheLab@saito"
            fixtures.addProperty(label, wallet.publicKey); fixtures.addProperty(label + "Name", names.getValue(wallet.publicKey))
            SqliteClientFixture(root.resolve("membership-$label.sqlite").toString()).use { store ->
                val participant = SpaceClient(wallet, "https://saito.davidengelen.nl", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                participant.openPublic(space); participant.joinPublic(space); participant.flush(space)
            }
        }
        owner.refresh(space); reader.refresh(space)
        check(reader.members(space).size == 4)
    }

    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/membership-fixtures" -> fixtures.deepCopy()
        "/membership-drop-next" -> {
            require(body.keySet() == setOf("kind"))
            val kind = body.get("kind").asString.also { require(it in kinds) }
            check(peer.withholdNextSpacesControlReply.compareAndSet(null, kind))
            JsonObject().apply { addProperty("armed", kind) }
        }
        "/membership-status" -> status()
        else -> null
    }

    suspend fun maintain() { reader.refresh(space); reader.flush(space) }

    private suspend fun status(): JsonObject {
        maintain()
        return JsonObject().apply {
            addProperty("space", space)
            add("members", JsonObject().apply { reader.members(space).forEach { addProperty(it.publicKey, it.role.name) } })
            add("banned", JsonArray().apply { reader.bannedMembers(space).sorted().forEach(::add) })
            add("offer", reader.ownershipOffer(space)?.let { offer -> JsonObject().apply {
                addProperty("id", offer.id); addProperty("member", offer.member)
                offer.acceptedId?.let { addProperty("acceptedId", it) }
            } } ?: com.google.gson.JsonNull.INSTANCE)
            add("messages", JsonArray().apply { reader.messages(space, fixtures.get("general").asString).forEach(::add) })
            add("dropped", JsonArray().apply { peer.withheldSpacesControls.forEach(::add) })
            add("originals", JsonArray().apply { peer.spacesRequests.toList().forEach { original ->
                val signed = SaitoEnvelope.decode(original.envelope()); val body = signed.body
                if (body.get("request")?.asString == "event" && body.get("kind")?.asString in kinds) add(JsonObject().apply {
                    addProperty("id", original.id); addProperty("actor", signed.author)
                    addProperty("space", body.get("space_id").asString); addProperty("kind", body.get("kind").asString)
                    for (key in listOf("member", "proposal_id", "role")) body.get(key)?.let { add(key, it.deepCopy()) }
                    addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(original.envelope().toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) })
                })
            } })
        }
    }
}

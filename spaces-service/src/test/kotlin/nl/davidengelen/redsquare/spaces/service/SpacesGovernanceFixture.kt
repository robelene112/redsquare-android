package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import java.security.MessageDigest
import java.time.Clock

/** An independent admitted member and authenticated control reader. No extra
 * device is admitted under the Android owner's identity. All traffic is owned. */
internal class SpacesGovernanceFixture(private val owner: SpaceClient, private val reader: SpaceClient,
    private val peer: ControlledFeedPeer, private val remote: SpacesServiceStore,
    private val service: SaitoWallet, private val clock: Clock, private val categoryAccess: Boolean = false) {
    private val actor = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private lateinit var space: String
    private lateinit var journal: SpaceJournal
    private val controls = mutableListOf<String>()
    private var seedControlIds = emptySet<String>()
    private val fixtures = JsonObject()
    private var requestSequence = 0L
    private val kinds = setOf("set-visibility", "set-listed", "create-category", "edit-category", "dissolve") +
        if (categoryAccess) setOf("create-channel", "edit-channel") else emptySet()

    suspend fun seed() {
        space = owner.create(SpaceProfile("Native Governance Lab", "Owned visibility, private category and terminal recovery journeys.",
            visibility = SpaceVisibility.PUBLIC), listOf(SpaceChannelProfile("general"), SpaceChannelProfile("workshop")))
        owner.flush(space)
        repeat(18) { owner.send(space, "Governance message $it") }; owner.flush(space)
        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); owner.refresh(space)
        val members = owner.members(space)
        check(members.single { it.role == SpaceRole.OWNER }.publicKey == actor.publicKey)
        fixtures.addProperty("space", space); fixtures.addProperty("link", owner.publicLink(space))
        fixtures.addProperty("owner", actor.publicKey)
        fixtures.addProperty("visitor", SaitoWallet.fromPrivateKey("0".repeat(63) + "5").publicKey)
        fixtures.addProperty("member", members.single { it.role == SpaceRole.MEMBER }.publicKey)
        if (categoryAccess) fixtures.addProperty("memberName", "MemberOfTheLab@saito")
        fixtures.addProperty("name", "Native Governance Lab")
        owner.channels(space).forEach { fixtures.addProperty(it.profile.name, it.id) }
        journal = SpaceJournal(space, service.publicKey)
        synchronizeControls()
        if (categoryAccess) {
            // The independently verified initial Workshop creation predates
            // Android. Keep its identity explicit instead of counting it as a
            // newly submitted native create-channel operation.
            seedControlIds = controls.map { SaitoEnvelope.decode(SaitoEnvelope.decode(it).body.get("event").asString).id }.toSet()
            fixtures.add("seedControlIds", JsonArray().apply { seedControlIds.forEach(::add) })
        }
    }

    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/governance-fixtures" -> fixtures.deepCopy()
        "/governance-drop-next" -> {
            require(body.keySet() == setOf("kind"))
            val kind = body.get("kind").asString.also { require(it in kinds) }
            check(peer.withholdNextSpacesControlReply.compareAndSet(null, kind))
            JsonObject().apply { addProperty("armed", kind) }
        }
        "/governance-status" -> status()
        else -> null
    }

    suspend fun maintain() {
        // This member has a different identity and store. The actual Android
        // owner must admit its signed key requests through the normal protocol.
        reader.refresh(space); reader.flush(space)
        // Reading the old public General does not consume the new private
        // lobby's committed history-key delivery. Observe each actual private
        // channel through its normal authenticated reader before checking ready.
        synchronizeControls()
        for ((id, channel) in journal.snapshot.channels) if (channel.visibility == SpaceVisibility.PRIVATE)
            reader.refresh(space, id)
    }

    private fun synchronizeControls() {
        repeat(10) {
            val scope = JsonObject().apply {
                addProperty("space_id", space); addProperty("stream", "control")
                addProperty("cursor", journal.snapshot.nextPosition)
            }
            val id = (++requestSequence).toString(16).padStart(64, '0')
            fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(actor, JsonObject().apply {
                addProperty("module", "Spaces"); addProperty("version", 1); addProperty("request", kind)
                addProperty("request_id", id); add("scope", scope.deepCopy())
                nonce?.let { addProperty("nonce", it) }
            }, clock.millis(), listOf(service.publicKey))
            fun verified(original: SaitoTransaction, kind: String): JsonObject {
                val reply = SaitoEnvelope.decode(remote.exchange(original.envelope()))
                val body = reply.body
                check(reply.author == service.publicKey && reply.recipients == listOf(service.publicKey, actor.publicKey))
                check(body.get("module").asString == "Spaces" && body.get("version").asInt == 1 &&
                    body.get("request").asString == kind && body.get("request_id").asString == id &&
                    body.get("caller").asString == actor.publicKey && body.getAsJsonObject("scope") == scope)
                return body
            }
            val challenge = verified(request("challenge"), "challenge")
            check(challenge.get("expires_at").asLong > clock.millis())
            val nonce = challenge.get("nonce").asString
            val page = verified(request("read", nonce), "page")
            check(page.get("nonce").asString == nonce)
            val items = page.getAsJsonArray("items")
            check(items.size() <= 50)
            for (item in items) {
                val receipt = item.asString
                journal.receive(receipt); controls += receipt
            }
            check(journal.snapshot.nextPosition == page.get("next_cursor").asLong)
            if (items.size() < 50) return
        }
        error("Owned governance control fixture exceeded its declared page bound")
    }

    private suspend fun status(): JsonObject {
        maintain(); synchronizeControls()
        val snapshot = journal.snapshot
        val memberChannels = reader.channels(space)
        val memberCategories = reader.categories(space).associateBy { it.id }
        return JsonObject().apply {
            addProperty("space", space); addProperty("entry", snapshot.entryChannelId)
            addProperty("visibility", snapshot.visibility.name); addProperty("dissolved", snapshot.dissolved)
            addProperty("listed", snapshot.publicProfile?.listed == true)
            addProperty("memberJoined", reader.community(space).joined)
            addProperty("memberEntryReady", memberChannels.any { it.id == snapshot.entryChannelId && it.ready })
            add("members", JsonObject().apply { snapshot.members.forEach { (key, role) -> addProperty(key, role.name) } })
            add("channels", JsonObject().apply { snapshot.channels.forEach { (id, channel) -> add(id, JsonObject().apply {
                addProperty("visibility", channel.visibility.name); addProperty("archived", channel.archived)
                if (categoryAccess) addProperty("categoryId", channel.categoryId)
                memberChannels.firstOrNull { it.id == id }?.let { addProperty("memberName", it.profile.name); addProperty("memberReady", it.ready) }
            }) } })
            add("categories", JsonObject().apply { snapshot.categories.forEach { (id, category) -> add(id, JsonObject().apply {
                addProperty("isPrivate", category.profile == null)
                memberCategories[id]?.let { addProperty("memberName", it.profile.name) }
            }) } })
            add("accepted", JsonArray().apply { controls.forEach { receipt ->
                val original = SaitoEnvelope.decode(receipt).body.get("event").asString
                val event = SaitoEnvelope.decode(original)
                if (event.id !in seedControlIds && event.body.get("kind").asString in kinds) add(row(event.id, event.author, event.body, original))
            } })
            add("originals", JsonArray().apply { peer.spacesRequests.toList().forEach { original ->
                val event = SaitoEnvelope.decode(original.envelope())
                if (event.body.get("request")?.asString == "event" && event.body.get("kind")?.asString in kinds)
                    add(row(original.id, event.author, event.body, original.envelope()))
            } })
            add("memberGeneralMessages", JsonArray().apply { reader.messages(space, fixtures.get("general").asString).forEach(::add) })
            add("directory", JsonArray().apply { reader.discover().items.forEach { add(it.id) } })
            add("dropped", JsonArray().apply { peer.withheldSpacesControls.forEach(::add) })
        }
    }

    private fun row(id: String, author: String, body: JsonObject, envelope: String) = JsonObject().apply {
        addProperty("id", id); addProperty("actor", author); addProperty("space", body.get("space_id").asString)
        addProperty("kind", body.get("kind").asString)
        addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(envelope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) })
        for (key in listOf("channel_id", "category_id", "visibility")) body.get(key)?.let { add(key, it.deepCopy()) }
        body.getAsJsonObject("public_profile")?.let { addProperty("listed", it.get("listed").asBoolean) }
        if (body.get("kind").asString in setOf("create-category", "edit-category")) {
            addProperty("encryptedMetadata", body.has("category_metadata") && !body.has("category_profile"))
            addProperty("containsPrivateName", listOf("Native private conversations", "Compact private conversations").any { body.toString().contains(it) })
        }
        if (categoryAccess && body.get("kind").asString in setOf("create-channel", "edit-channel")) {
            addProperty("encryptedMetadata", body.has("channel_metadata") && !body.has("channel_profile"))
            addProperty("containsPrivateName", listOf("private-category-room", "private-category-edited", "Compact private conversations").any { body.toString().contains(it) })
        }
    }
}

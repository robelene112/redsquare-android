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

/** Owned SQLite peers and published unsafe identities, never live network state. */
internal class SpacesPrivateManagementFixture(private val owner: SpaceClient, private val reader: SpaceClient,
    private val peer: ControlledFeedPeer, private val root: Path, private val service: SaitoWallet,
    private val clock: Clock, private val remote: SpacesServiceStore) {
    private lateinit var space: String
    private lateinit var memberKey: String
    private val fixtures = JsonObject()
    private var others = emptyList<SaitoWallet>()
    val names = mutableMapOf<String, String>()

    suspend fun seed() {
        space = owner.create(SpaceProfile("Private Management Lab", "Real restricted channels and independently received encrypted messages.",
            visibility = SpaceVisibility.PUBLIC), listOf(SpaceChannelProfile("general")))
        owner.flush(space)
        repeat(18) { owner.send(space, "Private management General $it") }; owner.flush(space)
        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); owner.refresh(space)
        val members = owner.members(space)
        memberKey = members.single { it.role == SpaceRole.MEMBER }.publicKey
        // Put the observed member last in the real sorted member roster.
        others = (5..512).asSequence().map { SaitoWallet.fromPrivateKey(it.toString(16).padStart(64, '0')) }
            .filter { it.publicKey < memberKey }.take(24).toList()
        check(others.size == 24)
        for ((index, wallet) in others.withIndex()) {
            names[wallet.publicKey] = "LabMember${index + 1}@saito"
            SqliteClientFixture(root.resolve("roster-$index.sqlite").toString()).use { store ->
                val participant = SpaceClient(wallet, "https://saito.davidengelen.nl", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                participant.openPublic(space); participant.joinPublic(space); participant.flush(space)
            }
        }
        owner.refresh(space); owner.setAdministrator(space, others.last().publicKey, true); owner.flush(space); reader.refresh(space)
        fixtures.addProperty("admin", others.last().publicKey); fixtures.addProperty("adminName", names.getValue(others.last().publicKey))
        val general = owner.community(space).channel
        fixtures.addProperty("space", space); fixtures.addProperty("general", general); fixtures.addProperty("link", owner.publicLink(space))
        fixtures.addProperty("owner", owner.members(space).single { it.role == SpaceRole.OWNER }.publicKey)
        fixtures.addProperty("member", memberKey); fixtures.addProperty("memberName", "MemberOfTheLab@saito")
        fixtures.addProperty("outsider", others.first().publicKey); fixtures.addProperty("memberCount", 26)
        check(owner.members(space).last().publicKey == memberKey)
    }

    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/private-management-fixtures" -> fixtures.deepCopy()
        "/private-management-drop-next" -> {
            require(body.keySet() == setOf("kind"))
            val kind = body.get("kind").asString.also { require(it in setOf("create-channel", "grant-channel-access", "revoke-channel-access")) }
            check(peer.withholdNextSpacesControlReply.compareAndSet(null, kind))
            JsonObject().apply { addProperty("armed", kind) }
        }
        "/private-management-status" -> status()
        else -> null
    }

    private suspend fun status(): JsonObject {
        reader.refresh(space); reader.flush(space)
        val originals = peer.spacesRequests.toList().filter {
            val body = SaitoEnvelope.decode(it.envelope()).body
            body.get("request")?.asString == "event" && body.get("kind")?.asString in
                setOf("create-channel", "grant-channel-access", "revoke-channel-access")
        }
        val created = originals.lastOrNull { SaitoEnvelope.decode(it.envelope()).body.get("kind").asString == "create-channel" }
        val channel = created?.let { SaitoEnvelope.decode(it.envelope()).body.get("channel_id").asString }
        if (channel != null && reader.channels(space).any { it.id == channel && it.ready }) reader.refresh(space, channel)
        val outsiderChannels = SqliteClientFixture(root.resolve("roster-0.sqlite").toString()).use { store ->
            val participant = SpaceClient(others.first(), "https://saito.davidengelen.nl", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
            participant.refresh(space); participant.channels(space)
        }
        return JsonObject().apply {
            addProperty("space", space)
            if (channel != null) addProperty("privateChannel", channel)
            add("members", JsonObject().apply { reader.members(space).forEach { addProperty(it.publicKey, it.role.name) } })
            add("channels", JsonObject().apply { reader.channels(space).forEach { value ->
                add(value.id, JsonObject().apply {
                    addProperty("name", value.profile.name); addProperty("category", value.profile.category)
                    addProperty("ready", value.ready); addProperty("visibility", value.visibility.name)
                })
            } })
            add("outsiderChannels", JsonArray().apply { outsiderChannels.forEach { add(it.id) } })
            add("privateMessages", JsonArray().apply { if (channel != null) reader.messages(space, channel).forEach(::add) })
            add("dropped", JsonArray().apply { peer.withheldSpacesControls.forEach(::add) })
            add("originals", JsonArray().apply { originals.forEach { original ->
                val signed = SaitoEnvelope.decode(original.envelope()); val body = signed.body
                add(JsonObject().apply {
                    addProperty("id", original.id); addProperty("actor", signed.author); addProperty("space", body.get("space_id").asString)
                    addProperty("kind", body.get("kind").asString); addProperty("channel", body.get("channel_id").asString)
                    if (body.has("member")) addProperty("member", body.get("member").asString)
                    if (body.has("visibility")) addProperty("visibility", body.get("visibility").asString)
                    if (body.has("allowed_members")) add("allowedMembers", body.getAsJsonArray("allowed_members").deepCopy())
                    if (body.has("epoch")) addProperty("epoch", body.get("epoch").asLong)
                    if (body.has("devices")) addProperty("deviceCount", body.getAsJsonObject("devices").size())
                    addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(original.envelope().toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) })
                })
            } })
        }
    }
}

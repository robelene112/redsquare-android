package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.spaces.*
import java.security.MessageDigest

/** Public disposable data only. The Android owner and the separately stored
 * reader observe actual signed service effects; no UI result is injected. */
internal class SpacesManagementFixture(private val owner: SpaceClient, private val reader: SpaceClient,
    private val peer: ControlledFeedPeer) {
    private lateinit var space: String
    private val fixtures = JsonObject()

    suspend fun seed() {
        space = owner.create(SpaceProfile("Native Management Lab", "A real owned community for management journeys.", visibility = SpaceVisibility.PUBLIC),
            listOf(SpaceChannelProfile("general"), SpaceChannelProfile("workshop"), SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT)))
        owner.flush(space)
        repeat(18) { owner.send(space, "Management message $it") }; owner.flush(space)
        val workshop = owner.channels(space).single { it.profile.name == "workshop" }.id
        owner.send(space, "A workshop message kept through archive", workshop); owner.flush(space)
        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); reader.refresh(space, workshop); owner.refresh(space)
        val members = reader.members(space)
        check(members.size == 2)
        check(reader.messages(space, workshop) == listOf("A workshop message kept through archive"))
        fixtures.addProperty("space", space); fixtures.addProperty("link", owner.publicLink(space))
        fixtures.addProperty("owner", members.single { it.role == SpaceRole.OWNER }.publicKey)
        fixtures.addProperty("member", members.single { it.role == SpaceRole.MEMBER }.publicKey)
        owner.channels(space).forEach { fixtures.addProperty(it.profile.name, it.id) }
    }

    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/management-fixtures" -> fixtures.deepCopy()
        "/management-drop-next" -> {
            require(body.keySet() == setOf("kind"))
            val kind = body.get("kind").asString.also { require(it in setOf("set-role", "archive-channel", "edit-channel", "create-channel")) }
            check(peer.withholdNextSpacesControlReply.compareAndSet(null, kind))
            JsonObject().apply { addProperty("armed", kind) }
        }
        "/management-status" -> status()
        else -> null
    }

    private suspend fun status(): JsonObject {
        reader.refresh(space)
        return JsonObject().apply {
            addProperty("space", space)
            add("members", JsonObject().apply { reader.members(space).forEach { addProperty(it.publicKey, it.role.name) } })
            add("channels", JsonObject().apply { reader.channels(space).forEach { channel ->
                add(channel.id, JsonObject().apply { addProperty("name", channel.profile.name); addProperty("category", channel.profile.category); addProperty("archived", channel.archived) })
            } })
            add("dropped", JsonArray().apply { peer.withheldSpacesControls.forEach(::add) })
            add("originals", JsonArray().apply {
                peer.spacesRequests.toList().forEach { original ->
                    val signed = SaitoEnvelope.decode(original.envelope())
                    val body = signed.body
                    if (body.get("request")?.asString == "event" && body.get("kind")?.asString in setOf("set-role", "archive-channel", "edit-channel", "create-channel")) {
                        add(JsonObject().apply {
                            addProperty("id", original.id); addProperty("actor", signed.author); addProperty("space", body.get("space_id").asString)
                            addProperty("kind", body.get("kind").asString)
                            if (body.has("channel_id")) addProperty("channel", body.get("channel_id").asString)
                            if (body.has("member")) addProperty("member", body.get("member").asString)
                            if (body.has("role")) addProperty("role", body.get("role").asString)
                            if (body.has("archived")) addProperty("archived", body.get("archived").asBoolean)
                            if (body.has("channel_profile")) add("profile", body.getAsJsonObject("channel_profile").deepCopy())
                            addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(original.envelope().toByteArray(Charsets.UTF_8))
                                .joinToString("") { "%02x".format(it.toInt() and 255) })
                        })
                    }
                }
            })
        }
    }
}

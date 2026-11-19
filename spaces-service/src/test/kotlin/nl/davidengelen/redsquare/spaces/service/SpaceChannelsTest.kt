package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceChannelsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test(timeout = 45_000) fun incompletePublicChannelEditsAreDurablyRejectedWithoutInterruptingWriting() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("invalid-channel-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("invalid-channel-edit-owner.sqlite").absolutePath).use { ownerStore ->
                var head: String? = null
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { response ->
                        val receipt = SaitoEnvelope.decode(response).body
                        if (receipt.get("stream")?.asString == "control") head = SaitoEnvelope.decode(receipt.get("event").asString).id
                    }
                }
                val space = creator.create(SpaceProfile("Invalid metadata does not block writing", visibility = SpaceVisibility.PUBLIC))
                creator.flush(space)
                val channel = creator.community(space).channel
                val queued = creator.send(space, "Valid writing survives an invalid edit")
                for ((index, invalid) in listOf(null, JsonPrimitive("This is not a channel profile object")).withIndex()) {
                    val original = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "edit-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                        addProperty("control_id", requireNotNull(head)); addProperty("operation_id", (index + 1).toString().repeat(64))
                        if (invalid != null) add("channel_profile", invalid)
                    }, clock.millis(), listOf(service.publicKey))
                    val rejected = remote.exchange(original.envelope())
                    assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                    assertEquals("Invalid original keeps its terminal disposition", rejected, remote.exchange(original.envelope()))
                }
                creator.flush(space); creator.refresh(space)
                assertTrue(creator.community(space).canSend)
                assertEquals("general", creator.channels(space).single().profile.name)
                assertEquals(listOf(queued), creator.timeline(space).map { it.id })
            }
        }
    }

    @Test(timeout = 45_000) fun authorizedPublicChannelEditsConvergeWithoutChangingIdentityHistoryOrDrafts() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-edit-member.sqlite").absolutePath).use { memberStore ->
                    var head: String? = null
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { response ->
                        val receipt = SaitoEnvelope.decode(response).body
                        if (receipt.get("stream")?.asString == "control") head = SaitoEnvelope.decode(receipt.get("event").asString).id
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    fun reader() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val first = reader()
                    val originalProfile = SpaceChannelProfile("workshop", category = "Conversations")
                    val space = creator.create(SpaceProfile("A community with editable channels", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("general"), originalProfile))
                    creator.flush(space); first.openPublic(space); first.joinPublic(space); first.flush(space); creator.refresh(space)
                    val channel = creator.channels(space).single { it.profile == originalProfile }.id
                    val message = creator.send(space, "This history belongs to the same channel", channel); creator.flush(space)
                    first.refresh(space, channel); first.selectChannel(space, channel)
                    first.saveDraft(space, "A draft survives channel renaming", channel)
                    assertEquals(originalProfile, first.channels(space).single { it.id == channel }.profile)
                    var operation = 0
                    fun edit(actor: SaitoWallet, profile: SpaceChannelProfile, reference: String = requireNotNull(head)) =
                        SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "edit-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                            addProperty("control_id", reference); addProperty("operation_id", (++operation).toString(16).padStart(64, '0'))
                            add("channel_profile", profile.json())
                        }, clock.millis(), listOf(service.publicKey))
                    val unauthorized = edit(member, SpaceChannelProfile("An ordinary member cannot rename this"))
                    val rejected = deliver(unauthorized)
                    assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                    assertEquals(rejected, deliver(unauthorized))
                    val renamed = SpaceChannelProfile("build-together", category = "Projects")
                    val ownerEdit = edit(owner, renamed)
                    val accepted = deliver(ownerEdit)
                    assertEquals("An owner can edit an existing public channel", "control", SaitoEnvelope.decode(accepted).body.get("stream").asString)
                    assertEquals(accepted, deliver(ownerEdit))
                    first.refresh(space, channel)
                    assertEquals("Accepted metadata replaces a previously cached channel label", renamed, first.channels(space).single { it.id == channel }.profile)
                    val restored = reader()
                    assertEquals(channel, restored.community(space).channel)
                    assertEquals("A draft survives channel renaming", restored.draft(space, channel))
                    assertEquals(listOf(message), restored.timeline(space, channel = channel).map { it.id })
                    creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); restored.refresh(space)
                    val old = edit(owner, SpaceChannelProfile("An older concurrent label", category = "Projects"))
                    val finalProfile = SpaceChannelProfile("builders", category = "Community")
                    val administratorEdit = edit(member, finalProfile)
                    assertEquals("control", SaitoEnvelope.decode(deliver(administratorEdit)).body.get("stream").asString)
                    val stale = deliver(old)
                    assertEquals("A reversed stale change must not overwrite the accepted label", "rejection", SaitoEnvelope.decode(stale).body.get("stream").asString)
                    assertEquals(stale, deliver(old))
                    val changeKind = edit(owner, finalProfile.copy(kind = SpaceChannelKind.ANNOUNCEMENT))
                    assertEquals("Metadata editing cannot silently change contribution rules", "rejection", SaitoEnvelope.decode(deliver(changeKind)).body.get("stream").asString)
                    creator.refresh(space, channel); restored.refresh(space, channel)
                    assertEquals(finalProfile, creator.channels(space).single { it.id == channel }.profile)
                    assertEquals(finalProfile, restored.channels(space).single { it.id == channel }.profile)
                    assertEquals("A draft survives channel renaming", restored.draft(space, channel))
                    assertEquals(listOf(message), creator.timeline(space, channel = channel).map { it.id })
                }
            }
        }
    }
}

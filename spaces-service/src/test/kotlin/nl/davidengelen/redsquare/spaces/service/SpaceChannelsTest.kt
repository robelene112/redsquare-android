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
import nl.davidengelen.redsquare.spaces.SpaceCrypto
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

    @Test(timeout = 45_000) fun aWithdrawnAdminRequestDoesNotBlockAnEligibleMemberWhenControlsChangedElsewhere() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-withdrawn-request-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-withdrawn-request-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-withdrawn-request-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-withdrawn-request-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Pending access follows current roles", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Selected active participants"), setOf(participant.publicKey)); creator.flush(space)
                        admin.refresh(space); admin.flush(space)
                        val demotion = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "set-role"); addProperty("space_id", space); addProperty("operation_id", "d".repeat(64))
                            addProperty("control_id", requireNotNull(creator.channelCreation(space)).id)
                            addProperty("member", member.publicKey); addProperty("role", "member")
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("control", SaitoEnvelope.decode(remote.exchange(demotion.envelope())).body.get("stream").asString)
                        reader.refresh(space); reader.flush(space)
                        // The normal owner worker has not performed a UI refresh since the external change.
                        creator.admitPending(space); creator.flush(space); reader.refresh(space); admin.refresh(space)
                        assertTrue("The eligible request after an obsolete request must complete", reader.channels(space).any { it.id == channel })
                        assertFalse("The withdrawn admin must not acquire channel keys", admin.channels(space).any { it.id == channel })
                        assertEquals(nl.davidengelen.redsquare.spaces.SpaceRole.MEMBER, creator.members(space).single { it.publicKey == member.publicKey }.role)
                        creator.send(space, "Current permissions govern admission", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("Current permissions govern admission"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anUnreadyPrivateChannelDoesNotBlockOtherAdmissionsAndItsRequestSurvives() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-deferred-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-deferred-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-deferred-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-deferred-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("One unavailable creator does not pause a community", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                        val unready = admin.createPrivateChannel(space, SpaceChannelProfile("Waiting for its creator"), emptySet()); admin.flush(space)
                        creator.refresh(space); creator.flush(space)
                        // The admin now goes offline with the only keys to the first requested channel.
                        val available = creator.createPrivateChannel(space, SpaceChannelProfile("Available owner-managed channel"), setOf(participant.publicKey)); creator.flush(space)
                        reader.refresh(space); reader.flush(space)
                        creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        assertTrue("A later request for a ready channel must be admitted", reader.channels(space).any { it.id == available })
                        assertEquals("Available owner-managed channel", reader.channels(space).single { it.id == available }.profile.name)
                        // Returning later must still process the earlier request exactly once.
                        admin.refresh(space); admin.flush(space); admin.admitPending(space); admin.flush(space); creator.refresh(space)
                        creator.admitPending(space); creator.flush(space)
                        assertTrue("The deferred request must survive until its key holder returns", creator.channels(space).any { it.id == unready })
                        assertEquals("Waiting for its creator", creator.channels(space).single { it.id == unready }.profile.name)
                        creator.send(space, "Both channels keep moving", available); creator.flush(space)
                        reader.selectChannel(space, available); reader.refresh(space)
                        assertEquals(listOf("Both channels keep moving"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aNewlyAdmittedOwnerCanAdmitAnotherMemberWithoutOpeningTheChannelListFirst() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-admin-profile-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-admin-profile-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-admin-profile-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-admin-profile-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Administration does not require visiting a list", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                        val profile = SpaceChannelProfile("Admin-created planning")
                        val channel = admin.createPrivateChannel(space, profile, setOf(participant.publicKey)); admin.flush(space)
                        creator.refresh(space); creator.flush(space)
                        reader.refresh(space); reader.flush(space)
                        admin.admitPending(space); admin.flush(space)
                        // Refresh installs the actual owner Welcome. It must not require a UI list read.
                        creator.refresh(space); creator.admitPending(space); creator.flush(space)
                        reader.refresh(space)
                        assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                        creator.send(space, "The owner can continue administration", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("The owner can continue administration"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun restrictedChannelControlsAndRequestsRequireTheCorrectAudienceAndEncryptedScope() = runBlocking<Unit> {
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-boundary-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-boundary-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-boundary-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("restricted-boundary-other.sqlite").absolutePath).use { otherStore ->
                        lateinit var creation: SaitoTransaction
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") creation = original
                            remote.exchange(original.envelope())
                        }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val excluded = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Receiving permissions are authoritative", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(reader, excluded)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Selected readers only"), setOf(member.publicKey)); creator.flush(space)
                        val accepted = SaitoEnvelope.decode(creation.envelope())
                        fun rejected(original: SaitoTransaction) {
                            val response = remote.exchange(original.envelope())
                            assertEquals("rejection", SaitoEnvelope.decode(response).body.get("stream").asString)
                            assertEquals("Rejected originals keep their disposition", response, remote.exchange(original.envelope()))
                        }
                        val foreignMetadata = accepted.body.deepCopy().apply {
                            addProperty("channel_id", "a".repeat(64)); addProperty("operation_id", "b".repeat(64)); addProperty("control_id", creation.id)
                        }
                        rejected(SaitoTransaction.create(owner, foreignMetadata, clock.millis(), listOf(service.publicKey)))
                        val invalidAudience = accepted.body.deepCopy().apply {
                            addProperty("channel_id", "c".repeat(64)); addProperty("operation_id", "d".repeat(64)); addProperty("control_id", creation.id)
                            addProperty("allowed_members", "This must be a member list")
                        }
                        rejected(SaitoTransaction.create(owner, invalidAudience, clock.millis(), listOf(service.publicKey)))
                        val scope = SpaceCrypto.Scope(service.publicKey, space, channel, accepted.body.get("key_generation").asString)
                        val packageData = SpaceCrypto.device(other.publicKey, "7".repeat(64)).keyPackage(scope, other)
                        val unauthorized = SaitoTransaction.create(other, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "join-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                            addProperty("control_id", creation.id); addProperty("operation_id", "e".repeat(64))
                            add("key_package", JsonObject().apply {
                                addProperty("wallet", other.publicKey); addProperty("device", packageData.deviceId)
                                addProperty("package", packageData.packageBytes); addProperty("signer", packageData.signer)
                                addProperty("signature", packageData.walletSignature)
                            })
                        }, clock.millis(), listOf(service.publicKey))
                        rejected(unauthorized)
                        excluded.refresh(space); creator.refresh(space)
                        assertFalse(excluded.channels(space).any { it.id == channel })
                        assertEquals(2, creator.channels(space).size)
                        assertTrue(creator.community(space).canSend)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun selectedMembersReadAPrivateChannelInsideAPublicSpaceWithoutExposingItsNameToOthers() = runBlocking<Unit> {
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-channel-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-channel-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-channel-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("restricted-channel-other.sqlite").absolutePath).use { otherStore ->
                        val controls = mutableListOf<String>()
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            val response = remote.exchange(original.envelope())
                            if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") controls.add(original.envelope())
                            response
                        }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val excluded = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Public with selected private conversations", visibility = SpaceVisibility.PUBLIC))
                        creator.flush(space)
                        for (client in listOf(reader, excluded)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space)
                        val general = creator.community(space).channel
                        creator.saveDraft(space, "Public writing survives a private channel", general)
                        val profile = SpaceChannelProfile("Private planning room", category = "Planning")
                        val channel = creator.createPrivateChannel(space, profile, setOf(member.publicKey))
                        creator.flush(space)
                        assertEquals(profile, creator.channels(space).single { it.id == channel }.profile)
                        assertEquals(SpaceVisibility.PRIVATE, creator.channels(space).single { it.id == channel }.visibility)
                        assertFalse("The control stream must not expose the private channel name", controls.single().contains("Private planning room"))
                        reader.refresh(space); excluded.refresh(space)
                        assertFalse(excluded.channels(space).any { it.id == channel })
                        try { excluded.selectChannel(space, channel); fail("Other Space members cannot request a restricted channel") }
                        catch (_: IllegalArgumentException) { }
                        reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                        creator.send(space, "Only admitted readers can decrypt this planning message", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("Only admitted readers can decrypt this planning message"), reader.messages(space))
                        excluded.refresh(space)
                        assertEquals(listOf(general), excluded.channels(space).map { it.id })
                        assertEquals("Public writing survives a private channel", creator.draft(space, general))
                        assertEquals(general, creator.community(space).channel)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun uncertainChannelCreationRecoversTheSameOriginalAndOnlyShowsAcceptedChannels() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-creation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-creation-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-creation-reader.sqlite").absolutePath).use { readerStore ->
                    var loseReply = true
                    val delivered = mutableListOf<String>()
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") {
                            delivered.add(original.envelope())
                            if (loseReply) { loseReply = false; throw java.io.IOException("Owned accepted channel creation reply lost") }
                        }
                        response
                    }
                    val creator = ownerClient()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Recoverable new channels", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space); reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "General draft survives creating a channel", general)
                    assertNull(creator.channelCreation(space))
                    val profile = SpaceChannelProfile("reading room", SpaceChannelKind.ARTICLE, "Knowledge")
                    val channel = creator.createChannel(space, profile)
                    val queued = requireNotNull(creator.channelCreation(space))
                    assertEquals(channel, queued.channelId); assertEquals(profile, queued.profile)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, queued.status)
                    assertEquals(listOf(general), creator.channels(space).map { it.id })
                    try { creator.flush(space); fail("The accepted creation reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelCreation(space)?.status)
                    assertEquals(listOf(general), creator.channels(space).map { it.id })
                    reader.refresh(space)
                    assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.channelCreation(space)?.status)
                    restored.flush(space)
                    assertEquals(queued.id, restored.channelCreation(space)?.id)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelCreation(space)?.status)
                    assertEquals(2, delivered.size); assertEquals(delivered[0], delivered[1])
                    assertEquals(profile, restored.channels(space).single { it.id == channel }.profile)
                    assertEquals(general, restored.community(space).channel)
                    assertEquals("General draft survives creating a channel", restored.draft(space, general))
                    try { reader.createChannel(space, SpaceChannelProfile("Unapproved structure")); fail("Members cannot create channels") }
                    catch (_: IllegalArgumentException) { }
                    assertNull(reader.channelCreation(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anUncertainChannelEditRetainsItsOriginalAndOnlyProjectsConfirmedMetadata() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-edit-intent-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-edit-intent-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-edit-intent-reader.sqlite").absolutePath).use { readerStore ->
                    var loseReply = true
                    val delivered = mutableListOf<String>()
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-channel") {
                            delivered.add(original.envelope())
                            if (loseReply) { loseReply = false; throw java.io.IOException("Owned accepted channel edit reply lost") }
                        }
                        response
                    }
                    val creator = ownerClient()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val oldProfile = SpaceChannelProfile("workshop", category = "Conversations")
                    val space = creator.create(SpaceProfile("Honest channel edit receipts", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("general"), oldProfile)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.channels(space).single { it.profile == oldProfile }.id
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "A workshop draft independent of its label", channel)
                    creator.saveDraft(space, "A separate general draft", general)
                    val renamed = SpaceChannelProfile("builders", category = "Projects")
                    val original = creator.editChannel(space, channel, renamed)
                    assertEquals(original, creator.channelEdit(space, channel)?.id)
                    assertEquals(renamed, creator.channelEdit(space, channel)?.profile)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.channelEdit(space, channel)?.status)
                    assertEquals(oldProfile, creator.channels(space).single { it.id == channel }.profile)
                    assertFalse(creator.community(space).canManage)
                    try { creator.flush(space); fail("The accepted edit reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelEdit(space, channel)?.status)
                    assertEquals(oldProfile, creator.channels(space).single { it.id == channel }.profile)
                    reader.refresh(space, channel)
                    assertEquals(renamed, reader.channels(space).single { it.id == channel }.profile)
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.channelEdit(space, channel)?.status)
                    restored.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(original, restored.channelEdit(space, channel)?.id)
                    assertEquals(2, delivered.size); assertEquals(delivered[0], delivered[1])
                    assertEquals(renamed, restored.channels(space).single { it.id == channel }.profile)
                    assertNull(restored.channelEdit(space, general))
                    val another = renamed.copy(name = "build-together")
                    val rejectedEdit = restored.editChannel(space, channel, another)
                    val competing = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "set-listed"); addProperty("space_id", space); addProperty("control_id", original)
                        addProperty("listed", false); addProperty("operation_id", "e".repeat(64))
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(competing.envelope())).body.get("stream").asString)
                    restored.flush(space)
                    assertEquals(rejectedEdit, restored.channelEdit(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(renamed, restored.channels(space).single { it.id == channel }.profile)
                    restored.refresh(space)
                    val deliberate = restored.editChannel(space, channel, another)
                    assertNotEquals(rejectedEdit, deliberate); restored.flush(space); reader.refresh(space, channel)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(another, reader.channels(space).single { it.id == channel }.profile)
                    assertEquals("A workshop draft independent of its label", restored.draft(space, channel))
                    assertEquals("A separate general draft", restored.draft(space, general))
                    try { reader.editChannel(space, channel, oldProfile); fail("Members cannot prepare channel edits") }
                    catch (_: IllegalArgumentException) { }
                    assertNull(reader.channelEdit(space, channel))
                }
            }
        }
    }

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

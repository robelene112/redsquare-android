package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceRole
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceRolesTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val administrator = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun roleIntentRetainsItsOriginalAndDoesNotMistakeAnUncertainOrRejectedChangeForSuccess() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("role-intent-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("role-intent-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("role-intent-admin.sqlite").absolutePath).use { adminStore ->
                    var loseRoleReply = true
                    val originals = mutableListOf<String>()
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "set-role") {
                            originals.add(original.envelope())
                            if (loseRoleReply) { loseRoleReply = false; throw java.io.IOException("Owned accepted role reply lost") }
                        }
                        response
                    }
                    val creator = ownerClient()
                    val admin = SpaceClient(administrator, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Deliberate delegated roles", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    admin.openPublic(space); admin.joinPublic(space); admin.flush(space); creator.refresh(space)
                    val promotion = creator.setAdministrator(space, administrator.publicKey, true)
                    assertEquals(promotion, creator.roleChange(space)?.id)
                    assertEquals(administrator.publicKey, creator.roleChange(space)?.memberKey)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.roleChange(space)?.status)
                    assertFalse(creator.community(space).canManage)
                    assertEquals(SpaceRole.MEMBER, creator.members(space).single { it.publicKey == administrator.publicKey }.role)
                    try { creator.flush(space); fail("The accepted reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.roleChange(space)?.status)
                    creator.saveDraft(space, "A separate new conversation draft")
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.roleChange(space)?.status)
                    restored.flush(space); admin.refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.roleChange(space)?.status)
                    assertEquals(2, originals.size); assertEquals(originals[0], originals[1])
                    assertEquals(promotion, SaitoEnvelope.decode(originals[1]).id)
                    assertEquals(SpaceRole.ADMIN, admin.members(space).single { it.publicKey == administrator.publicKey }.role)
                    assertTrue(admin.community(space).canManage)
                    try { admin.setAdministrator(space, owner.publicKey, false); fail("An administrator cannot change owner roles") }
                    catch (_: IllegalArgumentException) { }
                    assertNull(admin.roleChange(space))
                    val demotion = restored.setAdministrator(space, administrator.publicKey, false)
                    val concurrent = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "set-listed"); addProperty("space_id", space); addProperty("control_id", promotion)
                        addProperty("listed", false); addProperty("operation_id", "e".repeat(64))
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(concurrent.envelope())).body.get("stream").asString)
                    restored.flush(space); admin.refresh(space)
                    assertEquals(demotion, restored.roleChange(space)?.id)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, restored.roleChange(space)?.status)
                    assertEquals(SpaceRole.ADMIN, admin.members(space).single { it.publicKey == administrator.publicKey }.role)
                    assertEquals("A separate new conversation draft", restored.draft(space))
                    restored.refresh(space)
                    val deliberate = restored.setAdministrator(space, administrator.publicKey, false)
                    assertNotEquals(demotion, deliberate)
                    restored.flush(space); admin.refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.roleChange(space)?.status)
                    assertEquals(SpaceRole.MEMBER, admin.members(space).single { it.publicKey == administrator.publicKey }.role)
                    assertEquals("A separate new conversation draft", restored.draft(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun restoredAdministratorAuthorityDoesNotReviveOldPrivilegedWriting() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("role-cycle-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("role-cycle-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("role-cycle-admin.sqlite").absolutePath).use { adminStore ->
                    var head: String? = null
                    fun deliver(original: SaitoTransaction): String {
                        val response = remote.exchange(original.envelope())
                        val body = SaitoEnvelope.decode(response).body
                        if (body.get("stream")?.asString == "control") head = SaitoEnvelope.decode(body.get("event").asString).id
                        return response
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    fun administratorClient() = SpaceClient(administrator, "fixture-node", service.publicKey, adminStore, clock) { deliver(it) }
                    val first = administratorClient()
                    val space = creator.create(SpaceProfile("Privilege windows", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("general"), SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT)))
                    creator.flush(space); first.openPublic(space); first.joinPublic(space); first.flush(space)
                    fun role(value: String, operation: String) {
                        val original = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "set-role"); addProperty("space_id", space); addProperty("control_id", requireNotNull(head))
                            addProperty("member", administrator.publicKey); addProperty("role", value); addProperty("operation_id", operation)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("control", SaitoEnvelope.decode(deliver(original)).body.get("stream").asString)
                    }
                    role("admin", "a".repeat(64)); first.refresh(space)
                    val general = first.community(space).channel
                    val announcements = first.channels(space).single { it.profile.kind == SpaceChannelKind.ANNOUNCEMENT }.id
                    val ordinary = first.send(space, "Ordinary writing stays authorized", general)
                    val privileged = first.send(space, "Old privileged writing cannot return", announcements)
                    first.saveDraft(space, "Independent general draft", general)
                    first.saveDraft(space, "Independent announcement draft", announcements)
                    role("member", "b".repeat(64)); role("admin", "c".repeat(64))
                    val restored = administratorClient()
                    restored.flush(space); restored.refresh(space, announcements)
                    assertTrue("A new administrator term cannot publish an original from its previous term",
                        restored.timeline(space, channel = announcements).single { it.id == privileged }.rejected)
                    creator.refresh(space, general)
                    assertEquals(listOf(ordinary), creator.timeline(space, channel = general).map { it.id })
                    creator.refresh(space, announcements)
                    assertTrue(creator.timeline(space, channel = announcements).isEmpty())
                    assertEquals("Independent general draft", restored.draft(space, general))
                    assertEquals("Independent announcement draft", restored.draft(space, announcements))
                    val fresh = restored.send(space, "Deliberate writing under the new term", announcements)
                    role("admin", "d".repeat(64))
                    restored.flush(space); creator.refresh(space, announcements)
                    assertEquals("A same-role update must not discard authorized writing", listOf(fresh), creator.timeline(space, channel = announcements).map { it.id })
                }
            }
        }
    }

    @Test(timeout = 45_000) fun onlyTheOwnerAppointsAdminsAndDemotionStopsQueuedAnnouncementWriting() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("role-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("role-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("role-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("role-member.sqlite").absolutePath).use { memberStore ->
                        var head: String? = null
                        val delivered = mutableMapOf<String, String>()
                        fun deliver(original: SaitoTransaction): String {
                            delivered[original.id] = original.envelope()
                            val response = remote.exchange(original.envelope())
                            val body = SaitoEnvelope.decode(response).body
                            if (body.get("stream")?.asString == "control") head = SaitoEnvelope.decode(body.get("event").asString).id
                            return response
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val admin = SpaceClient(administrator, "fixture-node", service.publicKey, adminStore, clock) { deliver(it) }
                        val ordinary = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                        val space = creator.create(SpaceProfile("One deliberate owner", visibility = SpaceVisibility.PUBLIC),
                            listOf(SpaceChannelProfile("general"), SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT)))
                        creator.flush(space)
                        for (reader in listOf(admin, ordinary)) {
                            reader.openPublic(space); reader.joinPublic(space); reader.flush(space); reader.refresh(space)
                        }
                        creator.refresh(space); admin.refresh(space)
                        val announcements = creator.channels(space).single { it.profile.kind == SpaceChannelKind.ANNOUNCEMENT }.id
                        try { admin.send(space, "Not an administrator yet", announcements); fail("Members cannot publish announcements") }
                        catch (_: IllegalArgumentException) { }
                        try { ordinary.createChannel(space, SpaceChannelProfile("Unapproved structure")); fail("Members cannot create channels") }
                        catch (_: IllegalArgumentException) { }
                        fun role(actor: SaitoWallet, target: SaitoWallet, value: String, operation: String): String {
                            val original = SaitoTransaction.create(actor, JsonObject().apply {
                                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                                addProperty("kind", "set-role"); addProperty("space_id", space); addProperty("control_id", requireNotNull(head))
                                addProperty("member", target.publicKey); addProperty("role", value); addProperty("operation_id", operation)
                            }, clock.millis(), listOf(service.publicKey))
                            return SaitoEnvelope.decode(deliver(original)).body.get("stream").asString
                        }
                        assertEquals("The owner can delegate administration to an existing member", "control", role(owner, administrator, "admin", "a".repeat(64)))
                        for (client in listOf(creator, admin, ordinary)) client.refresh(space)
                        val expected = mapOf(owner.publicKey to SpaceRole.OWNER, administrator.publicKey to SpaceRole.ADMIN, member.publicKey to SpaceRole.MEMBER)
                        for (client in listOf(creator, admin, ordinary)) assertEquals(expected, client.members(space).associate { it.publicKey to it.role })
                        val announcement = admin.send(space, "An accepted administrator announcement", announcements); admin.flush(space)
                        creator.refresh(space, announcements)
                        assertEquals(announcement, creator.timeline(space, channel = announcements).single().id)
                        val channel = admin.createChannel(space, SpaceChannelProfile("Administrator-created channel")); admin.flush(space)
                        creator.refresh(space)
                        assertEquals("Administrator-created channel", creator.channels(space).single { it.id == channel }.profile.name)
                        assertEquals("rejection", role(administrator, owner, "member", "b".repeat(64)))
                        assertEquals("rejection", role(administrator, member, "admin", "c".repeat(64)))
                        assertEquals("rejection", role(owner, administrator, "owner", "d".repeat(64)))
                        val queued = admin.send(space, "Queued under the old administrator role", announcements)
                        admin.saveDraft(space, "Independent announcement draft", announcements)
                        assertEquals("control", role(owner, administrator, "member", "e".repeat(64)))
                        admin.flush(space); admin.refresh(space, announcements)
                        assertTrue(admin.timeline(space, channel = announcements).single { it.id == queued }.rejected)
                        assertFalse(admin.community(space, announcements).canSend)
                        assertEquals("Independent announcement draft", admin.draft(space, announcements))
                        creator.refresh(space, announcements); ordinary.refresh(space)
                        assertEquals(listOf(announcement), creator.timeline(space, channel = announcements).map { it.id })
                        for (client in listOf(creator, admin, ordinary)) assertEquals(SpaceRole.MEMBER,
                            client.members(space).single { it.publicKey == administrator.publicKey }.role)
                        assertEquals("control", role(owner, administrator, "admin", "f".repeat(64)))
                        assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(requireNotNull(delivered[queued]))).body.get("stream").asString)
                        assertEquals(SpaceRole.OWNER, creator.members(space).single { it.publicKey == owner.publicKey }.role)
                    }
                }
            }
        }
    }
}

package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceDissolutionTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val administrator = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val visitor = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun theOwnersSavedDissolutionRecoversItsOriginalAfterAnAcceptedReplyIsLost() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("dissolve-recovery-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("dissolve-recovery-reader.sqlite").absolutePath).use { readerStore ->
                val originals = mutableListOf<String>()
                var loseReply = false
                fun deliver(original: SaitoTransaction): String {
                    val closing = SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "dissolve"
                    if (closing) originals += original.envelope()
                    val receipt = remote.exchange(original.envelope())
                    if (closing && loseReply) { loseReply = false; throw java.io.IOException("Owned accepted dissolution reply loss") }
                    return receipt
                }
                val path = files.newFile("dissolve-recovery-owner.sqlite").absolutePath
                val reader = SpaceClient(administrator, "fixture-node", service.publicKey, readerStore, clock) { deliver(it) }
                lateinit var space: String
                lateinit var original: String
                SqliteClientFixture(path).use { ownerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    space = creator.create("An owner confirms closure deliberately"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    creator.send(space, "The last received conversation"); creator.flush(space); reader.refresh(space)
                    creator.saveDraft(space, "Keep the owner's unfinished draft")
                    try { reader.dissolve(space); fail("A member cannot request dissolution") }
                    catch (_: IllegalArgumentException) { }
                    loseReply = true
                    original = creator.dissolve(space)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.dissolutionChange(space)?.status)
                    assertFalse(creator.community(space).dissolved)
                    try { creator.flush(space); fail("The reply must be lost after remote acceptance") }
                    catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.dissolutionChange(space)?.status)
                    assertFalse(creator.community(space).dissolved)
                    reader.refresh(space)
                    assertTrue(reader.community(space).dissolved)
                    assertFalse(reader.community(space).canSend)
                }
                SqliteClientFixture(path).use { ownerStore ->
                    val recovered = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    assertEquals(original, recovered.dissolutionChange(space)?.id)
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, recovered.dissolutionChange(space)?.status)
                    assertEquals(original, recovered.management(space).dissolution?.id)
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, recovered.management(space).dissolution?.status)
                    assertFalse(recovered.community(space).dissolved)
                    recovered.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, recovered.dissolutionChange(space)?.status)
                    assertTrue(recovered.community(space).dissolved)
                    assertFalse(recovered.community(space).canManage)
                    assertEquals(2, originals.size); assertEquals(originals[0], originals[1])
                    assertEquals("Keep the owner's unfinished draft", recovered.draft(space))
                    assertEquals(listOf("The last received conversation"), recovered.messages(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aRemoteDissolutionRetiresUnfinishedStarterChannelsWithoutGeneratingRepeatedOriginals() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("dissolve-starters-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("dissolve-starters-owner.sqlite").absolutePath).use { ownerStore ->
                val channelAttempts = mutableListOf<String>()
                var interrupted = false
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    val body = SaitoEnvelope.decode(original.envelope()).body
                    if (body.get("kind")?.asString == "create-channel") channelAttempts += original.id
                    val receipt = remote.exchange(original.envelope())
                    if (body.get("kind")?.asString == "create" && !interrupted) {
                        interrupted = true
                        val dissolve = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", body.get("space_id").asString); addProperty("kind", "dissolve")
                            addProperty("control_id", original.id); addProperty("operation_id", "e".repeat(64))
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("control", SaitoEnvelope.decode(remote.exchange(dissolve.envelope())).body.get("stream").asString)
                    }
                    receipt
                }
                val space = creator.create(SpaceProfile("An interrupted starter plan"), listOf(
                    SpaceChannelProfile("general"), SpaceChannelProfile("planning"), SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT)))
                creator.flush(space)
                creator.refresh(space)
                assertTrue(creator.community(space).dissolved)
                creator.flush(space)
                assertTrue("At most the original prepared before learning of closure may be submitted", channelAttempts.size <= 1)
                assertFalse(creator.community(space).pending)
                val count = channelAttempts.size
                repeat(3) { creator.flush(space) }
                assertEquals(count, channelAttempts.size)
                assertFalse(creator.community(space).canManage)
            }
        }
    }

    @Test(timeout = 45_000) fun selectingAClosedPrivateChannelDoesNotAutomaticallyRequestFreshKeys() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("dissolve-key-preparation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("dissolve-key-preparation-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("dissolve-key-preparation-reader.sqlite").absolutePath).use { readerStore ->
                    var head = ""
                    var position = -1L
                    val requests = mutableListOf<String>()
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                        val received = SaitoEnvelope.decode(receipt).body
                        if (received.get("stream")?.asString == "control" && received.get("position").asLong > position) {
                            position = received.get("position").asLong; head = original.id
                        }
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val reader = SpaceClient(administrator, "fixture-node", service.publicKey, readerStore, clock) {
                        requests += SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString.orEmpty(); deliver(it)
                    }
                    val space = creator.create("No new preparation after closure"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    val channel = creator.createChannel(space, SpaceChannelProfile("private-notes")); creator.flush(space); reader.refresh(space)
                    assertFalse(reader.channels(space).single { it.id == channel }.ready)
                    val dissolve = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", "dissolve"); addProperty("control_id", head)
                        addProperty("operation_id", "d".repeat(64))
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(deliver(dissolve)).body.get("stream").asString)
                    reader.refresh(space); requests.clear()
                    reader.selectChannel(space, channel); reader.flush(space)
                    assertFalse(requests.contains("join-channel"))
                    assertFalse(reader.community(space).pending)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anAlreadyAdmittedMemberStillReceivesItsCommittedHistoryWhenDissolutionCrossesALostReply() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("dissolve-history-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("dissolve-history-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("dissolve-history-member.sqlite").absolutePath).use { memberStore ->
                    val originals = mutableListOf<String>()
                    val historyResults = mutableListOf<Pair<String, String>>()
                    var head = ""
                    var position = -1L
                    var loseAdmission = false
                    fun deliver(original: SaitoTransaction): String {
                        val body = SaitoEnvelope.decode(original.envelope()).body
                        originals += original.envelope()
                        val receipt = remote.exchange(original.envelope())
                        val received = SaitoEnvelope.decode(receipt).body
                        if (received.get("stream")?.asString == "control" && received.get("position").asLong > position) {
                            position = received.get("position").asLong; head = original.id
                        }
                        if (body.get("content_kind")?.asString == "history-keys") historyResults += original.id to received.get("stream").asString
                        if (body.get("kind")?.asString == "admit" && loseAdmission) {
                            loseAdmission = false; throw java.io.IOException("Owned accepted admission reply loss")
                        }
                        return receipt
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val reader = SpaceClient(administrator, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create("A closing community keeps earlier admission promises"); creator.flush(space)
                    creator.send(space, "Earlier history committed before admission"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space); creator.admitPending(space)
                    loseAdmission = true
                    try { creator.flush(space); fail("The admission reply must be lost after remote acceptance") }
                    catch (_: java.io.IOException) { }
                    val admitted = SaitoEnvelope.decode(originals.single { SaitoEnvelope.decode(it).body.get("kind")?.asString == "admit" })
                    val committed = admitted.body.getAsJsonArray("history_deliveries").map { it.asString }
                    assertTrue(committed.isNotEmpty()); assertTrue(historyResults.isEmpty())
                    val dissolve = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", "dissolve"); addProperty("control_id", head)
                        addProperty("operation_id", "f".repeat(64))
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(deliver(dissolve)).body.get("stream").asString)
                    creator.flush(space)
                    assertEquals(committed, historyResults.map { it.first })
                    assertEquals(List(committed.size) { "content" }, historyResults.map { it.second })
                    val retries = originals.filter { SaitoEnvelope.decode(it).body.get("kind")?.asString == "admit" }
                    assertEquals(2, retries.size); assertEquals(retries[0], retries[1])
                    reader.refresh(space)
                    assertEquals(listOf("Earlier history committed before admission"), reader.messages(space))
                    assertFalse(reader.community(space).canSend)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun onlyTheOwnerCanDissolveAndQueuedContributionsStopWithoutErasingReceivedHistory() = runBlocking<Unit> {
        for (visibility in SpaceVisibility.entries) {
            SpacesServiceStore(files.newFolder("dissolve-service-$visibility").toPath(), service, clock).use { remote ->
                SqliteClientFixture(files.newFile("dissolve-owner-$visibility.sqlite").absolutePath).use { ownerStore ->
                    SqliteClientFixture(files.newFile("dissolve-admin-$visibility.sqlite").absolutePath).use { adminStore ->
                        SqliteClientFixture(files.newFile("dissolve-visitor-$visibility.sqlite").absolutePath).use { visitorStore ->
                            var head = ""
                            var position = -1L
                            fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                                val body = SaitoEnvelope.decode(receipt).body
                                if (body.get("stream")?.asString == "control" && body.get("position").asLong > position) {
                                    position = body.get("position").asLong; head = original.id
                                }
                            }
                            val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                            val reader = SpaceClient(administrator, "fixture-node", service.publicKey, adminStore, clock) { deliver(it) }
                            val outsider = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { deliver(it) }
                            val space = creator.create(SpaceProfile("A community closing deliberately", visibility = visibility)); creator.flush(space)
                            val channel = creator.community(space).channel
                            if (visibility == SpaceVisibility.PUBLIC) { reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space) }
                            else {
                                val invitation = creator.invite(space); creator.flush(space)
                                reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                            }
                            creator.setAdministrator(space, administrator.publicKey, true); creator.flush(space); reader.refresh(space)
                            creator.send(space, "Keep this already received conversation", channel); creator.flush(space); reader.refresh(space)
                            reader.saveDraft(space, "Keep my unfinished work", channel)
                            val queued = reader.send(space, "Queued before the Space was dissolved", channel)
                            fun dissolve(actor: SaitoWallet, operation: String) = SaitoTransaction.create(actor, JsonObject().apply {
                                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                                addProperty("space_id", space); addProperty("kind", "dissolve"); addProperty("control_id", head)
                                addProperty("operation_id", operation)
                            }, clock.millis(), listOf(service.publicKey))
                            assertEquals("rejection", SaitoEnvelope.decode(deliver(dissolve(administrator, "a".repeat(64)))).body.get("stream").asString)
                            assertEquals("rejection", SaitoEnvelope.decode(deliver(dissolve(visitor, "b".repeat(64)))).body.get("stream").asString)
                            val original = dissolve(owner, "c".repeat(64))
                            val accepted = deliver(original)
                            assertEquals("control", SaitoEnvelope.decode(accepted).body.get("stream").asString)
                            assertEquals(accepted, deliver(original))
                            creator.refresh(space); reader.flush(space); reader.refresh(space)
                            assertFalse(creator.community(space).canSend)
                            assertFalse(creator.community(space).canManage)
                            assertFalse(creator.community(space).canInvite)
                            assertTrue(creator.channels(space).all { it.archived })
                            assertTrue(reader.timeline(space).single { it.id == queued }.rejected)
                            assertEquals("Keep my unfinished work", reader.draft(space, channel))
                            assertEquals(listOf("Keep this already received conversation"), creator.messages(space, channel))
                            assertTrue(outsider.discover().items.isEmpty())
                            val reopen = SaitoTransaction.create(owner, JsonObject().apply {
                                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                                addProperty("space_id", space); addProperty("kind", "archive-channel"); addProperty("control_id", head)
                                addProperty("operation_id", "d".repeat(64)); addProperty("channel_id", channel); addProperty("archived", false)
                            }, clock.millis(), listOf(service.publicKey))
                            assertEquals("rejection", SaitoEnvelope.decode(deliver(reopen)).body.get("stream").asString)
                        }
                    }
                }
            }
        }
    }
}

package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
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

class SpaceGovernanceTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test fun anAuthorizedNewMemberReadsEarlierPrivateHistoryWhileTheChannelIsArchived() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("archived-history-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("archived-history-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("archived-history-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create("Private archived history"); creator.flush(space)
                    val original = creator.send(space, "Available earlier private text"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    val channel = creator.community(space).channel
                    creator.archiveChannel(space, channel, true); creator.flush(space)
                    reader.join(invitation); reader.flush(space)
                    creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    assertTrue(reader.community(space).joined)
                    assertTrue(reader.channels(space).single().archived)
                    assertFalse(reader.community(space).canSend)
                    assertEquals(original, reader.timeline(space).single { it.text == "Available earlier private text" }.id)
                    assertNull("Another member has no local administration attempt", reader.archiveChange(space, channel))
                    reader.saveDraft(space, "Saved while reading an archive")
                    creator.archiveChannel(space, channel, false); creator.flush(space); reader.refresh(space)
                    assertTrue(reader.community(space).canSend)
                    assertEquals("Saved while reading an archive", reader.draft(space))
                    val reply = reader.send(space, "A new private reply after reopening"); reader.flush(space); creator.refresh(space)
                    assertEquals(listOf(original, reply), creator.timeline(space).map { it.id })
                    assertEquals(listOf("Available earlier private text", "A new private reply after reopening"), creator.messages(space))
                }
            }
        }
    }

    @Test fun anArchiveChangePreservesItsOriginalAndHonestStatusAfterALostAcknowledgment() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("archive-intent-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("archive-intent-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("archive-intent-reader.sqlite").absolutePath).use { readerStore ->
                    var loseArchiveReply = true
                    val delivered = mutableListOf<String>()
                    fun creator() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val received = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "archive-channel") {
                            delivered.add(original.envelope())
                            if (loseArchiveReply) { loseArchiveReply = false; throw java.io.IOException("Owned accepted archive reply lost") }
                        }
                        received
                    }
                    val first = creator()
                    val space = first.create(SpaceProfile("Archive receipt truth", visibility = SpaceVisibility.PUBLIC)); first.flush(space)
                    val channel = first.community(space).channel
                    val id = first.archiveChannel(space, channel, true)
                    assertEquals(id, requireNotNull(first.archiveChange(space, channel)).id)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, first.archiveChange(space, channel)?.status)
                    assertFalse(first.channels(space).single().archived)
                    assertFalse(first.community(space).canSend)
                    assertFalse(first.community(space).canManage)
                    try { first.flush(space); fail("The accepted archive acknowledgment is deliberately lost") }
                    catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, first.archiveChange(space, channel)?.status)
                    assertFalse("An uncertain send is not a local accepted archive", first.channels(space).single().archived)
                    first.saveDraft(space, "New writing survives uncertain administration")
                    val restored = creator()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.archiveChange(space, channel)?.status)
                    restored.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.archiveChange(space, channel)?.status)
                    assertTrue(restored.channels(space).single().archived)
                    assertTrue("The owner can reopen an accepted archive", restored.community(space).canManage)
                    assertEquals(2, delivered.size); assertEquals(delivered[0], delivered[1])
                    assertEquals(id, SaitoEnvelope.decode(delivered[1]).id)
                    assertEquals("New writing survives uncertain administration", restored.draft(space))
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    assertTrue(reader.channels(space).single().archived)
                    val reopening = restored.archiveChannel(space, channel, false)
                    val concurrentListing = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "set-listed"); addProperty("space_id", space)
                        addProperty("control_id", id); addProperty("operation_id", "f".repeat(64)); addProperty("listed", false)
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(concurrentListing.envelope())).body.get("stream").asString)
                    restored.flush(space)
                    assertEquals(reopening, restored.archiveChange(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, restored.archiveChange(space, channel)?.status)
                    assertTrue(restored.channels(space).single().archived)
                    assertEquals("New writing survives uncertain administration", restored.draft(space))
                    restored.refresh(space)
                    val deliberate = restored.archiveChannel(space, channel, false)
                    assertNotEquals(reopening, deliberate)
                    restored.flush(space); reader.refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.archiveChange(space, channel)?.status)
                    assertFalse(reader.channels(space).single().archived)
                    assertEquals("New writing survives uncertain administration", restored.draft(space))
                }
            }
        }
    }

    @Test fun anIncompleteArchiveChangeHasADurableRejectionAndDoesNotInterruptWriting() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("invalid-archive-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("invalid-archive-owner.sqlite").absolutePath).use { ownerStore ->
                var head: String? = null
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { reply ->
                        val body = SaitoEnvelope.decode(reply).body
                        if (body.get("stream")?.asString == "control") head = SaitoEnvelope.decode(body.get("event").asString).id
                    }
                }
                val space = creator.create(SpaceProfile("Invalid changes leave the channel open", visibility = SpaceVisibility.PUBLIC))
                creator.flush(space)
                val queued = creator.send(space, "This valid original must stay publishable")
                val missingFlag = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("kind", "archive-channel"); addProperty("space_id", space)
                    addProperty("channel_id", creator.community(space).channel); addProperty("control_id", requireNotNull(head))
                    addProperty("operation_id", "a".repeat(64))
                }, clock.millis(), listOf(service.publicKey))
                val rejection = remote.exchange(missingFlag.envelope())
                assertEquals("rejection", SaitoEnvelope.decode(rejection).body.get("stream").asString)
                assertEquals("The original disposition is stable on redelivery", rejection, remote.exchange(missingFlag.envelope()))
                creator.flush(space); creator.refresh(space)
                assertTrue(creator.community(space).canSend)
                assertEquals(listOf(queued), creator.timeline(space).map { it.id })
            }
        }
    }

    @Test fun reopeningDoesNotPublishWritingPreparedBeforeTheArchive() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("stale-archive-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("stale-archive-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("stale-archive-reader.sqlite").absolutePath).use { readerStore ->
                    var head: String? = null
                    fun deliver(original: SaitoTransaction): String {
                        val reply = remote.exchange(original.envelope())
                        val body = SaitoEnvelope.decode(reply).body
                        if (body.get("stream")?.asString == "control") head = SaitoEnvelope.decode(body.get("event").asString).id
                        return reply
                    }
                    fun creator() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val first = creator()
                    val space = first.create(SpaceProfile("Reopening needs deliberate new writing", visibility = SpaceVisibility.PUBLIC))
                    first.flush(space)
                    val accepted = first.send(space, "Already readable before archiving"); first.flush(space)
                    val queued = first.send(space, "Stale writing must not silently appear after reopening")
                    first.saveDraft(space, "New draft is independent of the queued original")
                    for ((operation, archived) in listOf("a".repeat(64) to true, "b".repeat(64) to false)) {
                        val action = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "archive-channel"); addProperty("space_id", space)
                            addProperty("channel_id", first.community(space).channel); addProperty("control_id", requireNotNull(head))
                            addProperty("operation_id", operation); addProperty("archived", archived)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("control", SaitoEnvelope.decode(deliver(action)).body.get("stream").asString)
                    }
                    val restored = creator()
                    restored.flush(space); restored.refresh(space)
                    assertTrue("A pending original from before the archive needs a deliberate retry", restored.timeline(space).single { it.id == queued }.rejected)
                    assertTrue(restored.community(space).canSend)
                    assertEquals("New draft is independent of the queued original", restored.draft(space))
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    assertEquals(listOf(accepted), reader.timeline(space).map { it.id })
                    val next = restored.send(space, "A deliberate new post after reopening"); restored.flush(space)
                    reader.refresh(space)
                    assertEquals(listOf(accepted, next), reader.timeline(space).map { it.id })
                }
            }
        }
    }

    @Test fun archivingRejectsQueuedAndNewContributionsButKeepsReadableHistory() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("archive-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("archive-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("archive-member.sqlite").absolutePath).use { memberStore ->
                    var head: String? = null
                    fun deliver(original: SaitoTransaction): String {
                        val reply = remote.exchange(original.envelope())
                        val body = SaitoEnvelope.decode(reply).body
                        if (body.get("stream")?.asString == "control") head = SaitoEnvelope.decode(body.get("event").asString).id
                        return reply
                    }
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) =
                        SpaceClient(wallet, "fixture-node", service.publicKey, store, clock) { deliver(it) }
                    val creator = client(owner, ownerStore)
                    val reader = client(member, memberStore)
                    val space = creator.create(SpaceProfile("A real archived conversation", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space)
                    val accepted = creator.send(space, "The earlier accepted message"); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); reader.refresh(space)
                    val queued = reader.send(space, "Written before the archive, delivered afterward")
                    reader.saveDraft(space, "Separate new writing must survive")
                    val channel = creator.community(space).channel
                    fun change(actor: SaitoWallet, archived: Boolean, operation: String): SaitoTransaction =
                        SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "archive-channel"); addProperty("space_id", space)
                            addProperty("channel_id", channel); addProperty("control_id", requireNotNull(head))
                            addProperty("operation_id", operation); addProperty("archived", archived)
                        }, clock.millis(), listOf(service.publicKey))
                    val archive = change(owner, true, "a".repeat(64))
                    assertEquals("An owner can archive the actual channel", "control", SaitoEnvelope.decode(deliver(archive)).body.get("stream").asString)
                    reader.flush(space); reader.refresh(space)
                    assertEquals(accepted, reader.timeline(space).single { it.text == "The earlier accepted message" }.id)
                    assertTrue(reader.timeline(space).single { it.id == queued }.rejected)
                    assertFalse(reader.community(space).canSend)
                    assertEquals("Separate new writing must survive", reader.draft(space))
                    try { reader.send(space, "A new contribution to the archived channel"); fail("An archived channel cannot accept new writing") }
                    catch (_: IllegalArgumentException) { }
                    assertFalse("Cold restoration preserves the accepted archive", client(member, memberStore).community(space).canSend)
                    val unauthorized = change(member, false, "b".repeat(64))
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(unauthorized)).body.get("stream").asString)
                    val reopen = change(owner, false, "c".repeat(64))
                    assertEquals("control", SaitoEnvelope.decode(deliver(reopen)).body.get("stream").asString)
                    reader.refresh(space)
                    assertTrue(reader.community(space).canSend)
                    assertEquals("Separate new writing must survive", reader.draft(space))
                    val next = reader.send(space, "Only new writing after reopening"); reader.flush(space)
                    creator.refresh(space)
                    assertEquals(listOf(accepted, next), creator.timeline(space).filter { !it.rejected }.map { it.id })
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(unauthorized)).body.get("stream").asString)
                    assertEquals("control", SaitoEnvelope.decode(deliver(archive)).body.get("stream").asString)
                    creator.refresh(space)
                    assertTrue("Duplicate old archive delivery does not rearchive a reopened channel", creator.community(space).canSend)
                }
            }
        }
    }
}

package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
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
import java.io.IOException

class SpaceMessageDeletionTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private fun wallet(value: Int) = SaitoWallet.fromPrivateKey(value.toString().padStart(64, '0'))

    @Test fun restoredAdministrationRequiresANewModerationDecisionAndRemovedTextCannotReturn() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3); val staff = wallet(4)
        SpacesServiceStore(files.newFolder("moderation-term-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("moderation-term-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("moderation-term-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("moderation-term-admin.sqlite").absolutePath).use { adminStore ->
                        val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val ordinary = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        var hold = true
                        val attempts = mutableListOf<String>(); val results = mutableListOf<String>()
                        fun administrator() = SpaceClient(staff, "fixture-node", service.publicKey, adminStore, clock) { request ->
                            val deletion = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-delete"
                            if (deletion) { attempts.add(request.envelope()); if (hold) throw IOException("The queued moderation has not reached the service") }
                            remote.exchange(request.envelope()).also { if (deletion) results.add(it) }
                        }
                        var admin = administrator()
                        val space = manager.create(SpaceProfile("Reviewed moderation", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                        for (client in listOf(ordinary, admin)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        manager.refresh(space)
                        val original = manager.send(space, "A message remains until a current decision"); manager.flush(space)
                        ordinary.refresh(space)
                        assertThrows(IllegalArgumentException::class.java) { ordinary.deleteMessage(space, ordinary.timeline(space).single()) }
                        manager.setAdministrator(space, staff.publicKey, true); manager.flush(space); admin.refresh(space)
                        val root = admin.timeline(space).single()
                        admin.saveDraft(space, "Independent writing survives changed authority")
                        admin.deleteMessage(space, root)
                        try { admin.flush(space); fail("The first moderation is still undelivered") } catch (_: IOException) { }
                        val body = SaitoEnvelope.decode(attempts.single()).body
                        val unauthorized = SaitoTransaction.create(member, body.deepCopy().apply {
                            addProperty("operation_id", "a".repeat(64))
                        }, clock.millis(), listOf(service.publicKey))
                        val refusal = remote.exchange(unauthorized.envelope())
                        assertEquals("rejection", SaitoEnvelope.decode(refusal).body.get("stream").asString)
                        assertEquals(refusal, remote.exchange(unauthorized.envelope()))
                        manager.setAdministrator(space, staff.publicKey, false); manager.flush(space)
                        manager.setAdministrator(space, staff.publicKey, true); manager.flush(space)
                        hold = false; admin = administrator(); admin.flush(space)
                        assertEquals(2, attempts.size); assertEquals(attempts[0], attempts[1])
                        assertEquals("Restoring a role cannot revive its old signed moderation", "rejection", SaitoEnvelope.decode(results.single()).body.get("stream").asString)
                        assertEquals(results.single(), remote.exchange(attempts[0]))
                        manager.refresh(space)
                        assertFalse(manager.timeline(space).single().deleted)
                        admin.refresh(space)
                        admin.deleteMessage(space, admin.timeline(space).single()); admin.flush(space); manager.refresh(space)
                        val removed = manager.timeline(space).single()
                        assertEquals(original, removed.id); assertEquals(root.position, removed.position)
                        assertTrue(removed.deleted); assertNull(removed.text)
                        val resurrection = SaitoTransaction.create(owner, SaitoEnvelope.decode(attempts.last()).body.deepCopy().apply {
                            addProperty("operation_id", "b".repeat(64)); addProperty("content_kind", "message-edit"); addProperty("text", "The removed text returns")
                        }, clock.millis(), listOf(service.publicKey))
                        val rejectedEdit = remote.exchange(resurrection.envelope())
                        assertEquals("rejection", SaitoEnvelope.decode(rejectedEdit).body.get("stream").asString)
                        assertEquals(rejectedEdit, remote.exchange(resurrection.envelope()))
                        assertEquals("Independent writing survives changed authority", admin.draft(space))
                        assertEquals("A message remains until a current decision", root.text)
                    }
                }
            }
        }
    }

    @Test fun aPrivateDeletionRecoversTheExactCiphertextAfterALostResponseAndServiceReopen() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        val directory = files.newFolder("private-deletion-service").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        try {
            SqliteClientFixture(files.newFile("private-deletion-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-deletion-writer.sqlite").absolutePath).use { authorStore ->
                    val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var loseReply = true
                    val deletions = mutableListOf<String>()
                    fun author() = SpaceClient(member, "fixture-node", service.publicKey, authorStore, clock) { request ->
                        val isDeletion = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-delete"
                        if (isDeletion) deletions.add(request.envelope())
                        val reply = remote.exchange(request.envelope())
                        if (isDeletion && loseReply) { loseReply = false; throw IOException("The accepted deletion reply was lost") }
                        reply
                    }
                    var writer = author()
                    val space = manager.create(SpaceProfile("Private deletion", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); manager.refresh(space)
                    val channel = manager.createPrivateChannel(space, SpaceChannelProfile("Confidential writing"), setOf(member.publicKey)); manager.flush(space)
                    writer.refresh(space); writer.flush(space); manager.admitPending(space); manager.flush(space); writer.refresh(space)
                    val originalId = writer.send(space, "Confidential text later removed", channel); writer.flush(space); manager.refresh(space, channel)
                    val before = manager.timeline(space, channel = channel).single()
                    writer.deleteMessage(space, writer.timeline(space, channel = channel).single(), channel)
                    try { writer.flush(space); fail("The accepted response is deliberately lost") } catch (_: IOException) { }
                    manager.refresh(space, channel)
                    val removed = manager.timeline(space, channel = channel).single()
                    assertEquals(originalId, removed.id); assertEquals(before.position, removed.position)
                    assertTrue(removed.deleted); assertNull(removed.text)
                    assertEquals("Confidential text later removed", before.text); assertFalse(before.deleted)
                    remote.close(); remote = SpacesServiceStore(directory, service, clock)
                    writer = author(); writer.flush(space)
                    assertEquals(2, deletions.size)
                    assertEquals("Recovery uses the original ciphertext, operation and signature", deletions[0], deletions[1])
                    val wire = SaitoEnvelope.decode(deletions[0]).body
                    assertEquals("content", wire.get("kind").asString)
                    assertTrue(wire.has("ciphertext")); assertFalse(wire.has("text"))
                    assertFalse(wire.toString().contains("Confidential text later removed"))
                    writer.refresh(space, channel)
                    assertTrue(writer.timeline(space, channel = channel).single().deleted)
                    assertEquals(originalId, writer.timeline(space, channel = channel).single().id)
                }
            }
        } finally { remote.close() }
    }

    @Test fun aDeletedMessageRetainsItsIdentityAsACompactPlaceholderOnAnotherClient() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("deletion-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("deletion-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("deletion-author.sqlite").absolutePath).use { authorStore ->
                    val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    val space = manager.create(SpaceProfile("Message history", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); manager.refresh(space)
                    val originalId = writer.send(space, "Text the author later removes"); writer.flush(space); manager.refresh(space)
                    val before = manager.timeline(space).single()
                    writer.deleteMessage(space, writer.timeline(space).single()); writer.flush(space); manager.refresh(space)
                    val removed = manager.timeline(space).single()
                    assertEquals(originalId, removed.id); assertEquals(before.position, removed.position)
                    assertEquals(member.publicKey, removed.author)
                    assertTrue(removed.deleted); assertNull(removed.text)
                    assertEquals("Text the author later removes", before.text)
                    assertFalse(before.deleted)
                }
            }
        }
    }
}

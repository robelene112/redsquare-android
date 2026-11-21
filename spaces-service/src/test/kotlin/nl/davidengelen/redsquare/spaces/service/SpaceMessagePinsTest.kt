package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoEnvelope
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

class SpaceMessagePinsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private fun wallet(value: Int) = SaitoWallet.fromPrivateKey(value.toString().padStart(64, '0'))

    @Test fun anUnattemptedPinCanBeCancelledWithoutSendingOrDiscardingTheCurrentDraft() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("cancel-pin-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("cancel-pin-owner.sqlite").absolutePath).use { local ->
                var pinRequests = 0
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, local, clock) { request ->
                    if (SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-pin") pinRequests++
                    remote.exchange(request.envelope())
                }
                val space = creator.create(SpaceProfile("Cancelled reference", visibility = SpaceVisibility.PUBLIC)); creator.flush(space); creator.refresh(space)
                creator.send(space, "Accepted original"); creator.flush(space)
                creator.saveDraft(space, "Keep this unrelated draft")
                val operation = creator.pinMessage(space, creator.timeline(space).single(), true)
                assertTrue(creator.timeline(space).single().pinPending)
                assertThrows(IllegalArgumentException::class.java) { creator.pinMessage(space, creator.timeline(space).single(), false) }
                assertTrue(creator.timeline(space).single().pinPending)
                assertTrue(creator.cancelQueuedChange(space, operation))
                assertFalse(creator.timeline(space).single().pinned); assertFalse(creator.timeline(space).single().pinPending)
                assertTrue(creator.pinnedMessages(space).items.isEmpty())
                val cancelled = requireNotNull(creator.timeline(space).single().pinChange)
                assertEquals(operation, cancelled.operationId); assertTrue(cancelled.cancelled); assertFalse(cancelled.rejected)
                creator.flush(space); assertEquals(0, pinRequests)
                assertEquals("Keep this unrelated draft", creator.draft(space))
                assertFalse(creator.cancelQueuedChange(space, operation))
            }
        }
    }

    @Test(timeout = 180_000) fun pinListsPageAllActiveReferencesAndDoNotMakeReadersPageThroughRemovedPins() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("active-pin-pages-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("active-pin-pages-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Useful active references", visibility = SpaceVisibility.PUBLIC)); creator.flush(space); creator.refresh(space)
                val targets = mutableListOf<String>()
                repeat(51) { index ->
                    val id = creator.send(space, "Active reference $index"); creator.flush(space)
                    creator.pinMessage(space, requireNotNull(creator.message(space, id)), true); creator.flush(space); targets.add(id)
                }
                SqliteClientFixture(files.newFile("active-pin-pages-reader.sqlite").absolutePath).use { readerStore ->
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    val first = reader.pinnedMessages(space); assertEquals(50, first.items.size); assertTrue(first.complete)
                    val second = reader.pinnedMessages(space, requireNotNull(first.nextMessage)); assertEquals(1, second.items.size); assertNull(second.nextMessage)
                    assertEquals(targets.toSet(), (first.items + second.items).map { it.message.id }.toSet())
                    val last = targets.max()
                    targets.filter { it != last }.forEach { id -> creator.pinMessage(space, requireNotNull(creator.message(space, id)), false) }
                    creator.flush(space); reader.refresh(space)
                    val active = reader.pinnedMessages(space)
                    assertEquals("Removed references must not leave an empty first page before the remaining active pin", listOf(last), active.items.map { it.message.id })
                    assertNull(active.nextMessage); assertTrue(active.complete)
                    assertFalse(reader.community(space).joined)
                }
            }
        }
    }

    @Test fun anOldAdministratorCannotReviveAPinDecisionAfterLosingAndRegainingItsRole() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("pin-authority-terms-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("pin-authority-terms-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("pin-authority-terms-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val originals = mutableListOf<String>(); val replies = mutableListOf<String>()
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        val pin = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-pin"
                        if (pin) originals.add(request.envelope())
                        remote.exchange(request.envelope()).also { if (pin) replies.add(it) }
                    }
                    val space = creator.create(SpaceProfile("Reviewed pin authority", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    creator.send(space, "The retained accepted reference"); creator.flush(space)
                    creator.pinMessage(space, creator.timeline(space).single(), true); creator.flush(space)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space); writer.refresh(space)
                    val target = writer.timeline(space).single(); assertTrue(target.pinned); assertTrue(writer.community(space).canPin)
                    writer.saveDraft(space, "Independent writing survives a refused moderation decision")
                    val obsolete = writer.pinMessage(space, target, false)
                    creator.setAdministrator(space, member.publicKey, false); creator.flush(space); writer.flush(space)
                    val refused = replies.single(); val refusedOriginal = originals.single()
                    assertEquals("rejection", SaitoEnvelope.decode(refused).body.get("stream").asString)
                    assertEquals(obsolete, SaitoEnvelope.decode(refusedOriginal).id)
                    assertTrue(writer.timeline(space).single().pinned); assertFalse(writer.timeline(space).single().pinPending)
                    assertFalse(writer.community(space).canPin)
                    val feedback = requireNotNull(writer.timeline(space).single().pinChange)
                    assertEquals(obsolete, feedback.operationId); assertTrue(feedback.rejected); assertTrue(feedback.attempted)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space); writer.refresh(space)
                    assertTrue(writer.community(space).canPin)
                    assertEquals("The original refusal remains immutable after role restoration", refused, remote.exchange(refusedOriginal))
                    val reviewed = writer.pinMessage(space, writer.timeline(space).single(), false); writer.flush(space); creator.refresh(space)
                    assertNotEquals(obsolete, reviewed); assertEquals(2, originals.size)
                    assertEquals("content", SaitoEnvelope.decode(replies.last()).body.get("stream").asString)
                    assertFalse(creator.timeline(space).single().pinned)
                    assertNull(writer.timeline(space).single().pinChange)
                    assertEquals("Independent writing survives a refused moderation decision", writer.draft(space))
                }
            }
        }
    }

    @Test fun aPrivateReplyPinRetainsItsExactCiphertextAndActualParentAcrossALostReplyAndServiceRestart() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        val directory = files.newFolder("private-reply-pins-service").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        try {
            SqliteClientFixture(files.newFile("private-reply-pins-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-reply-pins-reader.sqlite").absolutePath).use { readerStore ->
                    var lose = true; val originals = mutableListOf<String>()
                    fun author() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val pin = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-pin"
                        if (pin) originals.add(request.envelope())
                        remote.exchange(request.envelope()).also { if (pin && lose) { lose = false; throw java.io.IOException("Accepted private pin response is lost") } }
                    }
                    var creator = author()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Encrypted reference context", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, nl.davidengelen.redsquare.spaces.SpaceChannelProfile("Confidential references"), setOf(member.publicKey)); creator.flush(space)
                    reader.refresh(space); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, channel)
                    reader.send(space, "The actual confidential parent", channel); reader.flush(space); reader.refresh(space, channel)
                    val parent = reader.timeline(space, channel = channel).single()
                    reader.reply(space, parent, "The actual confidential pinned reply", channel); reader.flush(space)
                    creator.refresh(space, channel); creator.refreshThread(space, parent, channel)
                    val reply = creator.thread(space, parent, channel = channel).single()
                    creator.pinMessage(space, reply, true, channel)
                    try { creator.flush(space); fail("The actual accepted response is lost") } catch (_: java.io.IOException) { }
                    remote.close(); remote = SpacesServiceStore(directory, service, clock)
                    reader.refresh(space, channel)
                    val received = reader.pinnedMessages(space, channel = channel).items.single()
                    assertEquals(reply.id, received.message.id); assertEquals("The actual confidential pinned reply", received.message.text)
                    assertEquals(parent.id, received.parent?.id); assertEquals("The actual confidential parent", received.parent?.text)
                    assertTrue(received.message.pinned); assertFalse(received.message.pinPending)
                    creator = author(); creator.flush(space)
                    assertEquals(2, originals.size); assertEquals(originals[0], originals[1])
                    val wire = SaitoEnvelope.decode(originals[0]).body
                    assertEquals("content", wire.get("kind").asString); assertTrue(wire.has("ciphertext")); assertFalse(wire.has("text"))
                    assertEquals(reply.id, wire.get("target_message_id").asString); assertEquals(parent.id, wire.get("thread_root_id").asString)
                    assertFalse(creator.pinnedMessages(space, channel = channel).items.single().message.pinPending)
                }
            }
        } finally { remote.close() }
    }

    @Test fun aColdPublicReplyPinRestoresItsExactParentAndCurrentReplyCountOutsideTheLiveWindow() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("cold-thread-pins-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("cold-thread-pins-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Persistent thread reference", visibility = SpaceVisibility.PUBLIC)); creator.flush(space); creator.refresh(space)
                creator.send(space, "The exact parent of the pinned answer"); creator.flush(space)
                val parent = creator.timeline(space).single()
                val answer = creator.reply(space, parent, "The actual pinned answer"); creator.flush(space); creator.refreshThread(space, parent)
                creator.pinMessage(space, creator.thread(space, parent).single(), true); creator.flush(space)
                repeat(60) { creator.send(space, "Newer public writing $it") }; creator.flush(space)
                SqliteClientFixture(files.newFile("cold-thread-pins-reader.sqlite").absolutePath).use { readerStore ->
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    val window = reader.historyWindow(space)
                    assertFalse(reader.timeline(space, from = window.from).any { it.id == parent.id })
                    val reference = reader.pinnedMessages(space).items.single()
                    assertEquals(answer, reference.message.id); assertEquals(parent.id, reference.threadId)
                    assertEquals(parent.id, reference.parent?.id); assertEquals("The exact parent of the pinned answer", reference.parent?.text)
                    assertEquals("The restored parent retains the actual thread-entry count", 1L, reference.parent?.replyCount)
                    reader.selectThread(space, requireNotNull(reference.parent)); reader.refreshThread(space, requireNotNull(reference.parent))
                    assertEquals(answer, reader.thread(space, requireNotNull(reference.parent)).single().id)
                    assertFalse(reader.community(space).joined)
                }
            }
        }
    }

    @Test fun aNewPublicReaderFindsAPinnedReferenceOutsideItsLatestMessageWindow() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("cold-message-pins-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("cold-message-pins-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Long-lived reference", visibility = SpaceVisibility.PUBLIC)); creator.flush(space); creator.refresh(space)
                val root = creator.send(space, "The reference remains discoverable after the conversation moves"); creator.flush(space)
                creator.pinMessage(space, creator.timeline(space).single(), true); creator.flush(space)
                repeat(60) { creator.send(space, "More recent writing $it") }; creator.flush(space)
                SqliteClientFixture(files.newFile("cold-message-pins-reader.sqlite").absolutePath).use { readerStore ->
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    assertFalse("Public reading must not join", reader.community(space).joined)
                    val history = reader.historyWindow(space)
                    assertFalse(reader.timeline(space, from = history.from).any { it.id == root })
                    val pins = reader.pinnedMessages(space)
                    assertTrue(pins.complete)
                    assertEquals(listOf(root), pins.items.map { it.message.id })
                    assertEquals("The reference remains discoverable after the conversation moves", pins.items.single().message.text)
                    assertFalse(pins.items.single().message.pinPending)
                }
            }
        }
    }

    @Test fun staffPinsHaveImmediateLocalFeedbackAndReadersReceiveTheExactPinnedMessage() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("message-pins-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("message-pins-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("message-pins-reader.sqlite").absolutePath).use { readerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Useful pinned messages", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val id = creator.send(space, "The actual community reference"); creator.flush(space); reader.refresh(space)
                    val message = creator.timeline(space).single()
                    assertThrows(IllegalArgumentException::class.java) { reader.pinMessage(space, reader.timeline(space).single(), true) }
                    creator.pinMessage(space, message, true)
                    val pending = creator.timeline(space).single()
                    assertTrue(pending.pinned); assertTrue(pending.pinPending)
                    assertFalse(reader.timeline(space).single().pinned)
                    creator.flush(space); reader.refresh(space)
                    val received = reader.timeline(space).single()
                    assertEquals(id, received.id); assertTrue(received.pinned); assertFalse(received.pinPending)
                    assertEquals(listOf(id), reader.pinnedMessages(space).items.map { it.message.id })
                    creator.pinMessage(space, creator.timeline(space).single(), false)
                    assertFalse(creator.timeline(space).single().pinned)
                    creator.flush(space); reader.refresh(space)
                    assertFalse(reader.timeline(space).single().pinned)
                    assertTrue(reader.pinnedMessages(space).items.isEmpty())
                    assertEquals("The actual community reference", reader.timeline(space).single().text)
                }
            }
        }
    }
}

package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceMessageThreadsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private fun wallet(value: Int) = SaitoWallet.fromPrivateKey(value.toString().padStart(64, '0'))

    @Test fun cancellingAnUntransmittedRootOrReplyRetainsRecoverableTextAndCannotWithdrawAnUncertainSend() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("cancel-message-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("cancel-message-owner.sqlite").absolutePath).use { ownerStore ->
                var loseSendReply = false
                val attempts = mutableListOf<String>()
                fun author() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                    val root = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message"
                    if (root) attempts.add(request.envelope())
                    remote.exchange(request.envelope()).also { if (root && loseSendReply) {
                        loseSendReply = false; throw java.io.IOException("The send was accepted, but its reply was lost")
                    } }
                }
                var writer = author()
                val space = writer.create(SpaceProfile("Recoverable cancellation", visibility = SpaceVisibility.PUBLIC)); writer.flush(space)
                val cancelledRoot = writer.send(space, "A root cancelled before transmission")
                writer.saveDraft(space, "Newer channel writing")
                assertTrue(writer.cancelQueuedMessage(space, cancelledRoot))
                assertFalse(writer.cancelQueuedMessage(space, cancelledRoot))
                writer = author()
                val cancelled = writer.timeline(space).single()
                assertTrue(cancelled.cancelled); assertFalse(cancelled.pending); assertFalse(cancelled.rejected)
                assertEquals("A root cancelled before transmission", cancelled.text)
                assertEquals("Newer channel writing", writer.draft(space))
                writer.flush(space); assertTrue(attempts.isEmpty()); assertTrue(writer.messages(space).isEmpty())
                writer.send(space, "An accepted thread parent"); writer.flush(space)
                val parent = writer.timeline(space).single { !it.cancelled }
                writer.selectThread(space, parent)
                val cancelledReply = writer.reply(space, parent, "A reply cancelled before transmission")
                writer.saveThreadDraft(space, parent, "Newer thread writing")
                assertTrue(writer.cancelQueuedMessage(space, cancelledReply))
                writer.flush(space); writer.refreshThread(space, parent)
                val child = writer.thread(space, parent).single()
                assertTrue(child.cancelled); assertEquals("A reply cancelled before transmission", child.text)
                assertEquals("Newer thread writing", writer.threadDraft(space, parent))
                assertEquals(0L, writer.timeline(space).single { it.id == parent.id }.replyCount)
                val uncertain = writer.send(space, "An uncertain accepted send")
                loseSendReply = true
                try { writer.flush(space); fail("The accepted response is lost") } catch (_: java.io.IOException) { }
                writer = author()
                assertFalse(writer.cancelQueuedMessage(space, uncertain))
                assertTrue(writer.timeline(space).single { it.id == uncertain }.attempted)
                writer.flush(space)
                val originals = attempts.filter { SaitoEnvelope.decode(it).id == uncertain }
                assertEquals(2, originals.size); assertEquals(originals[0], originals[1])
                assertEquals(1, writer.messages(space).count { it == "An uncertain accepted send" })
            }
        }
    }

    @Test fun pendingMessageChangesKeepTheirVisibleIntentAcrossReopenLostAcknowledgmentAndRejection() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("message-change-status-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("message-change-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("message-change-member.sqlite").absolutePath).use { memberStore ->
                    val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var loseEditReply = false
                    val attempts = mutableListOf<String>()
                    fun author() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        val edit = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-edit"
                        if (edit) attempts.add(request.envelope())
                        remote.exchange(request.envelope()).also { if (edit && loseEditReply) {
                            loseEditReply = false; throw java.io.IOException("The accepted edit acknowledgment was lost")
                        } }
                    }
                    var writer = author()
                    val space = manager.create(SpaceProfile("Visible queued intent", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); manager.refresh(space)
                    writer.send(space, "The original received message"); writer.flush(space)
                    val original = writer.timeline(space).single()
                    val edit = writer.editMessage(space, original, "The desired replacement")
                    writer = author()
                    val queued = requireNotNull(writer.timeline(space).single().change)
                    assertEquals(edit, queued.operationId); assertEquals(SpaceClient.MessageChangeKind.EDIT, queued.kind)
                    assertFalse(queued.attempted); assertFalse(queued.rejected); assertEquals("The desired replacement", queued.text)
                    assertEquals("The original received message", writer.timeline(space).single().text)
                    loseEditReply = true
                    try { writer.flush(space); fail("The response is deliberately lost") } catch (_: java.io.IOException) { }
                    writer = author()
                    assertTrue(requireNotNull(writer.timeline(space).single().change).attempted)
                    writer.flush(space)
                    assertEquals(attempts[0], attempts[1]); assertNull(writer.timeline(space).single().change)
                    assertEquals("The desired replacement", writer.timeline(space).single().text)
                    writer.deleteMessage(space, writer.timeline(space).single())
                    assertEquals(SpaceClient.MessageChangeKind.DELETE, writer.timeline(space).single().change?.kind)
                    val channel = writer.community(space).channel
                    manager.archiveChannel(space, channel, true); manager.flush(space)
                    writer.flush(space)
                    val rejected = requireNotNull(writer.timeline(space).single().change)
                    assertEquals(SpaceClient.MessageChangeKind.DELETE, rejected.kind); assertTrue(rejected.rejected)
                    assertFalse(writer.timeline(space).single().deleted)
                    manager.archiveChannel(space, channel, false); manager.flush(space); writer.refresh(space)
                    writer.editMessage(space, writer.timeline(space).single(), "The newer requested text")
                    val newer = requireNotNull(writer.timeline(space).single().change)
                    assertFalse(newer.rejected); assertEquals("The newer requested text", newer.text)
                    writer.flush(space)
                    assertNull(writer.timeline(space).single().change)
                    assertEquals("The newer requested text", writer.timeline(space).single().text)
                }
            }
        }
    }

    @Test fun revokedPrivateAccessPreservesCachedThreadFocusAndUnsavedWritingWithoutGrantingNewContributions() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("revoked-thread-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("revoked-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("revoked-thread-member.sqlite").absolutePath).use { memberStore ->
                    val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var exchanges = 0
                    fun reader() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { exchanges++; remote.exchange(it.envelope()) }
                    var writer = reader()
                    val space = manager.create(SpaceProfile("Retained local work", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); manager.refresh(space)
                    val channel = manager.createPrivateChannel(space, SpaceChannelProfile("Private discussions"), setOf(member.publicKey)); manager.flush(space)
                    writer.refresh(space); writer.flush(space); manager.admitPending(space); manager.flush(space); writer.refresh(space)
                    manager.send(space, "A previously received private parent", channel); manager.flush(space); writer.refresh(space, channel)
                    val parent = writer.timeline(space, channel = channel).single()
                    writer.selectThread(space, parent, channel)
                    val replyId = writer.reply(space, parent, "A previously received reply", channel); writer.flush(space); writer.refreshThread(space, parent, channel)
                    val reply = writer.thread(space, parent, channel = channel).single { it.id == replyId }
                    val position = requireNotNull(reply.position)
                    val anchor = SpaceClient.ReadingAnchor(reply.id, position, 19, position)
                    manager.revokeChannelAccess(space, channel, member.publicKey); manager.flush(space)
                    try { writer.refresh(space, channel) } catch (_: SpaceClient.ReadDenied) { }
                    assertEquals("Cached identity survives a permission change", parent.id, writer.message(space, parent.id, channel)?.id)
                    writer.saveThreadDraft(space, parent, "Work typed before the access change")
                    writer.saveThreadReadingAnchor(space, parent, anchor, channel)
                    writer = reader()
                    assertEquals(parent.id, writer.selectedThread(space, channel)?.id)
                    val view = writer.presentation(space, channel, thread = parent)
                    assertEquals("A previously received private parent", view.threadParent?.text)
                    assertEquals(listOf(replyId), view.messages.map { it.id })
                    assertEquals("Work typed before the access change", view.draft); assertEquals(anchor, view.anchor)
                    assertFalse(requireNotNull(view.current).canReply)
                    assertThrows(IllegalArgumentException::class.java) { writer.reply(space, parent, "A new forbidden contribution", channel) }
                    val requestsBeforeLocalNavigation = exchanges
                    writer.selectChannel(space, channel)
                    assertNull(writer.selectedThread(space, channel))
                    assertEquals("A previously received private parent", writer.presentation(space, channel).messages.single().text)
                    writer.selectThread(space, parent, channel)
                    val reopened = writer.presentation(space, channel, thread = requireNotNull(writer.selectedThread(space, channel)))
                    assertEquals("Work typed before the access change", reopened.draft); assertEquals(anchor, reopened.anchor)
                    assertFalse(requireNotNull(reopened.current).canReply)
                    writer.flush(space)
                    assertEquals("Cached navigation must not send a new join or contribution", requestsBeforeLocalNavigation, exchanges)
                    assertFalse(requireNotNull(writer.presentation(space, channel).current).canReply)
                }
            }
        }
    }

    @Test fun aRestoredNativeConversationSelectionProjectsTheRightDraftHistoryAndCurrentParent() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("thread-selection-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("thread-selection-owner.sqlite").absolutePath).use { ownerStore ->
                fun author() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                var creator = author()
                val space = creator.create(SpaceProfile("Native conversation focus", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                val parentId = creator.send(space, "The original parent")
                val otherId = creator.send(space, "Another top-level message"); creator.flush(space)
                val parent = creator.timeline(space).single { it.id == parentId }
                val replyId = creator.reply(space, parent, "A focused reply"); creator.flush(space); creator.refresh(space); creator.refreshThread(space, parent)
                val channel = creator.community(space).channel
                val reply = creator.thread(space, parent).single()
                val mainAnchor = SpaceClient.ReadingAnchor(parentId, requireNotNull(parent.position), 11, 0)
                val replyAnchor = SpaceClient.ReadingAnchor(replyId, requireNotNull(reply.position), 17, requireNotNull(reply.position))
                creator.saveReadingAnchor(space, channel, mainAnchor)
                creator.saveThreadReadingAnchor(space, parent, replyAnchor)
                creator.saveDraft(space, "Channel writing"); creator.saveThreadDraft(space, parent, "Thread writing")
                creator.selectThread(space, parent)
                creator = author()
                val restored = requireNotNull(creator.selectedThread(space))
                assertEquals(parentId, restored.id)
                val focused = creator.presentation(space, thread = restored)
                assertEquals(parentId, focused.threadParent?.id)
                assertEquals(listOf(replyId), focused.messages.map { it.id })
                assertEquals("Thread writing", focused.draft); assertEquals(replyAnchor, focused.anchor)
                assertEquals(listOf(parentId, otherId), creator.presentation(space).messages.map { it.id })
                creator.editMessage(space, parent, "The updated parent"); creator.flush(space)
                assertEquals("The updated parent", creator.selectedThread(space)?.text)
                assertEquals("The updated parent", creator.presentation(space, thread = restored).threadParent?.text)
                creator.selectChannel(space, channel)
                assertNull(creator.selectedThread(space))
                val main = creator.presentation(space)
                assertNull(main.threadParent); assertEquals("Channel writing", main.draft); assertEquals(mainAnchor, main.anchor)
                assertEquals("Thread writing", creator.threadDraft(space, parent))
                assertEquals(replyAnchor, creator.threadReadingAnchor(space, parent))
                assertThrows(IllegalArgumentException::class.java) { creator.selectThread(space, reply) }
                assertNull(creator.selectedThread(space))
            }
        }
    }

    @Test(timeout = 45_000) fun refreshingAnOlderThreadWindowUpdatesEveryVisibleLargeEditAndRetainsBothAnchors() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("anchored-thread-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("anchored-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("anchored-thread-writer.sqlite").absolutePath).use { writerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, writerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Stable older threads", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    creator.send(space, "The original conversation"); creator.flush(space); writer.refresh(space)
                    val parent = writer.timeline(space).single()
                    repeat(50) { writer.reply(space, parent, "Original $it") }; writer.flush(space); creator.refreshThread(space, parent)
                    val originals = writer.thread(space, parent)
                    val first = creator.thread(space, parent).first()
                    val channel = creator.community(space).channel
                    val channelAnchor = SpaceClient.ReadingAnchor(parent.id, requireNotNull(parent.position), 11, 0)
                    creator.saveReadingAnchor(space, channel, channelAnchor)
                    val anchored = creator.thread(space, parent)[10]
                    val threadAnchor = SpaceClient.ReadingAnchor(anchored.id, requireNotNull(anchored.position), 27, requireNotNull(first.position))
                    creator.saveThreadReadingAnchor(space, parent, threadAnchor)
                    creator.saveThreadDraft(space, parent, "Newer local thread writing")
                    repeat(200) { writer.reply(space, parent, "New reply $it") }; repeat(4) { writer.flush(space) }
                    val payload = "x".repeat(60 * 1024)
                    originals.forEachIndexed { index, message -> writer.editMessage(space, message, "Corrected $index:$payload") }; writer.flush(space)
                    creator.refreshThread(space, parent)
                    val visible = creator.thread(space, parent, from = threadAnchor.windowFrom)
                    assertEquals(originals.map { it.id }, visible.map { it.id })
                    assertEquals((0..49).map { "Corrected $it:$payload" }, visible.map { it.text })
                    assertTrue(visible.all { it.edited })
                    assertEquals(threadAnchor, creator.threadReadingAnchor(space, parent))
                    assertEquals(channelAnchor, creator.readingAnchor(space))
                    assertEquals("Newer local thread writing", creator.threadDraft(space, parent))
                }
            }
        }
    }

    @Test fun membersEditAndRemoveTheirThreadRepliesWhileAnnouncementsRemainStaffOnly() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("thread-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("thread-edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("thread-edit-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val edits = mutableListOf<String>()
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        if (SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-edit") edits.add(request.envelope())
                        remote.exchange(request.envelope())
                    }
                    val space = creator.create(SpaceProfile("Thread authorship", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    val channel = creator.createChannel(space, SpaceChannelProfile("announcements", kind = SpaceChannelKind.ANNOUNCEMENT)); creator.flush(space)
                    val parentId = creator.send(space, "An unchanged staff announcement", channel); creator.flush(space); writer.refresh(space, channel)
                    val parent = writer.timeline(space, channel = channel).single()
                    val replyId = writer.reply(space, parent, "The member's first reply", channel); writer.flush(space)
                    val original = writer.thread(space, parent, channel = channel).single()
                    writer.editMessage(space, original, "The member's corrected reply", channel); writer.flush(space)
                    creator.refreshThread(space, parent, channel)
                    val corrected = creator.thread(space, parent, channel = channel).single()
                    assertEquals(replyId, corrected.id); assertEquals(original.position, corrected.position)
                    assertEquals("The member's corrected reply", corrected.text); assertTrue(corrected.edited)
                    val wrongConversation = SaitoTransaction.create(owner, SaitoEnvelope.decode(edits.single()).body.deepCopy().apply {
                        addProperty("operation_id", "c".repeat(64)); addProperty("target_message_id", parentId)
                        addProperty("target_position", parent.position); addProperty("text", "A root mislabeled as a thread reply")
                    }, clock.millis(), listOf(service.publicKey))
                    val refused = remote.exchange(wrongConversation.envelope())
                    assertEquals("Even the author must identify the correct conversation", "rejection", SaitoEnvelope.decode(refused).body.get("stream").asString)
                    assertEquals(refused, remote.exchange(wrongConversation.envelope()))
                    assertFalse(writer.community(space, channel).canSend)
                    assertThrows(IllegalArgumentException::class.java) { writer.editMessage(space, parent, "Changed announcement", channel) }
                    writer.deleteMessage(space, original, channel); writer.flush(space); creator.refreshThread(space, parent, channel)
                    val removed = creator.thread(space, parent, channel = channel).single()
                    assertEquals(replyId, removed.id); assertEquals(original.position, removed.position)
                    assertTrue(removed.deleted); assertNull(removed.text)
                    creator.refresh(space, channel)
                    val top = creator.timeline(space, channel = channel).single()
                    assertEquals(parentId, top.id); assertEquals("An unchanged staff announcement", top.text)
                    assertEquals(1L, top.replyCount)
                    assertEquals("The member's first reply", original.text)
                    assertFalse(original.edited); assertFalse(original.deleted)
                }
            }
        }
    }

    @Test fun threadHistoryAndPixelAnchorsSurviveReopenWithoutMovingTheChannel() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val visitor = wallet(3)
        SpacesServiceStore(files.newFolder("thread-history-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("thread-history-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("thread-history-reader.sqlite").absolutePath).use { readerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    fun returningReader() = SpaceClient(visitor, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Long focused conversations", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    val rootId = creator.send(space, "The original conversation")
                    val otherId = creator.send(space, "Another conversation"); creator.flush(space)
                    val root = creator.timeline(space).single { it.id == rootId }
                    repeat(70) { creator.reply(space, root, "Reply $it") }; repeat(2) { creator.flush(space) }
                    var reader = returningReader(); reader.openPublic(space); reader.refresh(space)
                    val parent = reader.timeline(space).single { it.id == rootId }
                    val channel = reader.community(space).channel
                    assertFalse(reader.community(space).joined)
                    val channelAnchor = SpaceClient.ReadingAnchor(parent.id, requireNotNull(parent.position), 11, 0)
                    reader.saveReadingAnchor(space, channel, channelAnchor)
                    reader.refreshThread(space, parent)
                    val latest = reader.thread(space, parent)
                    assertEquals((20..69).map { "Reply $it" }, latest.map { it.text })
                    val window = reader.threadHistoryWindow(space, parent)
                    assertEquals(latest.first().position, window.from)
                    assertTrue(window.loaded); assertTrue(window.hasEarlier); assertFalse(window.hasLater)
                    val reading = latest[8]
                    val threadAnchor = SpaceClient.ReadingAnchor(reading.id, requireNotNull(reading.position), 17, window.from)
                    reader.saveThreadReadingAnchor(space, parent, threadAnchor)
                    reader.saveThreadDraft(space, parent, "Independent thread writing")
                    reader = returningReader()
                    assertEquals(threadAnchor, reader.threadReadingAnchor(space, parent))
                    assertEquals(channelAnchor, reader.readingAnchor(space))
                    assertEquals("Independent thread writing", reader.threadDraft(space, parent))
                    val earlier = reader.olderThread(space, parent, before = window.from)
                    assertEquals((0..49).map { "Reply $it" }, reader.thread(space, parent, from = earlier).map { it.text })
                    val olderWindow = reader.threadHistoryWindow(space, parent, from = earlier)
                    assertFalse("Message positions include roots, but no earlier replies exist", olderWindow.hasEarlier)
                    assertTrue(olderWindow.hasLater)
                    assertEquals((20..69).map { "Reply $it" }, reader.thread(space, parent).map { it.text })
                    assertEquals(listOf(rootId, otherId), reader.timeline(space).map { it.id })
                    assertEquals(channelAnchor, reader.readingAnchor(space))
                    val wrongScope = SpaceClient.ReadingAnchor(otherId, 1, 0, 0)
                    assertThrows(IllegalArgumentException::class.java) { reader.saveThreadReadingAnchor(space, parent, wrongScope) }
                    assertEquals(threadAnchor, reader.threadReadingAnchor(space, parent))
                    assertEquals("Independent thread writing", reader.threadDraft(space, parent))
                }
            }
        }
    }

    @Test fun threadDraftsRemainIndependentAcrossReopenAndSendingOlderWork() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("thread-draft-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("thread-draft-owner.sqlite").absolutePath).use { ownerStore ->
                fun author() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                var creator = author()
                val space = creator.create(SpaceProfile("Independent writing", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                val firstId = creator.send(space, "The first conversation")
                val secondId = creator.send(space, "The second conversation"); creator.flush(space)
                val first = creator.timeline(space).single { it.id == firstId }
                val second = creator.timeline(space).single { it.id == secondId }
                val sharedText = "The same words independently saved in two places"
                creator.saveDraft(space, sharedText)
                creator.saveThreadDraft(space, first, sharedText)
                creator.saveThreadDraft(space, second, "The separate second-thread draft")
                creator = author()
                assertEquals(sharedText, creator.draft(space))
                assertEquals(sharedText, creator.threadDraft(space, first))
                assertEquals("The separate second-thread draft", creator.threadDraft(space, second))
                val replyId = creator.reply(space, first, sharedText)
                assertEquals("Queuing consumes only the matching thread draft", "", creator.threadDraft(space, first))
                assertEquals(sharedText, creator.draft(space))
                assertEquals("The separate second-thread draft", creator.threadDraft(space, second))
                val waiting = creator.thread(space, first).single()
                assertEquals(replyId, waiting.id); assertEquals(sharedText, waiting.text)
                assertTrue(waiting.pending); assertFalse(waiting.attempted)
                creator.saveThreadDraft(space, first, "New writing while older work is sent")
                creator.flush(space); creator.refreshThread(space, first)
                creator = author()
                assertEquals("New writing while older work is sent", creator.threadDraft(space, first))
                assertEquals("The separate second-thread draft", creator.threadDraft(space, second))
                assertEquals(sharedText, creator.draft(space))
                val received = creator.thread(space, first).single()
                assertEquals(replyId, received.id); assertEquals(sharedText, received.text)
                assertFalse(received.pending)
            }
        }
    }

    @Test fun privateRepliesKeepTheirCiphertextIdentityThroughALostResponseAndServiceReopen() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        val directory = files.newFolder("private-thread-service").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        try {
            SqliteClientFixture(files.newFile("private-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-thread-writer.sqlite").absolutePath).use { writerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var lose = true
                    val attempts = mutableListOf<String>(); val changes = mutableListOf<String>()
                    fun author() = SpaceClient(member, "fixture-node", service.publicKey, writerStore, clock) { request ->
                        val kind = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString
                        val reply = kind == "thread-reply"
                        if (reply) attempts.add(request.envelope())
                        if (kind in setOf("message-edit", "message-delete")) changes.add(request.envelope())
                        remote.exchange(request.envelope()).also {
                            if (reply && lose) { lose = false; throw java.io.IOException("The actual accepted reply acknowledgment was lost") }
                        }
                    }
                    var writer = author()
                    val space = creator.create(SpaceProfile("Private threaded writing", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private conversations"), setOf(member.publicKey)); creator.flush(space)
                    writer.refresh(space); writer.flush(space); creator.admitPending(space); creator.flush(space); writer.refresh(space)
                    val parentId = creator.send(space, "A confidential parent question", channel)
                    val otherId = creator.send(space, "An independent confidential question", channel); creator.flush(space); writer.refresh(space, channel)
                    val parent = writer.timeline(space, channel = channel).single { it.id == parentId }
                    val replyId = writer.reply(space, parent, "A confidential threaded answer", channel)
                    try { writer.flush(space); fail("The actual accepted response is deliberately lost") } catch (_: java.io.IOException) { }
                    creator.refresh(space, channel); creator.refreshThread(space, parent, channel)
                    assertEquals(listOf(parentId, otherId), creator.timeline(space, channel = channel).map { it.id })
                    assertEquals(1L, creator.timeline(space, channel = channel).single { it.id == parentId }.replyCount)
                    assertEquals(0L, creator.timeline(space, channel = channel).single { it.id == otherId }.replyCount)
                    assertEquals(listOf("A confidential threaded answer"), creator.thread(space, parent, channel = channel).map { it.text })
                    remote.close(); remote = SpacesServiceStore(directory, service, clock)
                    writer = author(); writer.flush(space)
                    assertEquals(2, attempts.size); assertEquals(attempts[0], attempts[1])
                    val wire = SaitoEnvelope.decode(attempts[0]).body
                    assertEquals("content", wire.get("kind").asString); assertFalse(wire.has("text")); assertTrue(wire.has("ciphertext"))
                    assertFalse(wire.toString().contains("A confidential threaded answer"))
                    writer.refreshThread(space, parent, channel)
                    val received = writer.thread(space, parent, channel = channel)
                    assertEquals(listOf(replyId), received.map { it.id })
                    assertEquals("A confidential threaded answer", received.single().text)
                    assertFalse(received.single().pending)
                    writer.editMessage(space, received.single(), "The corrected confidential answer", channel)
                    val queuedEdit = requireNotNull(writer.thread(space, parent, channel = channel).single().change)
                    assertEquals(SpaceClient.MessageChangeKind.EDIT, queuedEdit.kind)
                    assertEquals("The corrected confidential answer", queuedEdit.text)
                    assertFalse(queuedEdit.attempted)
                    writer = author()
                    assertEquals("The corrected confidential answer", writer.thread(space, parent, channel = channel).single().change?.text)
                    writer.flush(space)
                    assertNull(writer.thread(space, parent, channel = channel).single().change)
                    creator.refreshThread(space, parent, channel)
                    val corrected = creator.thread(space, parent, channel = channel).single()
                    assertEquals(replyId, corrected.id); assertTrue(corrected.edited)
                    assertEquals("The corrected confidential answer", corrected.text)
                    writer.deleteMessage(space, received.single(), channel)
                    val queuedDeletion = requireNotNull(writer.thread(space, parent, channel = channel).single().change)
                    assertEquals(SpaceClient.MessageChangeKind.DELETE, queuedDeletion.kind); assertNull(queuedDeletion.text)
                    writer.flush(space); creator.refreshThread(space, parent, channel)
                    val removed = creator.thread(space, parent, channel = channel).single()
                    assertEquals(replyId, removed.id); assertTrue(removed.deleted); assertNull(removed.text)
                    assertEquals(2, changes.size)
                    for (change in changes) {
                        val body = SaitoEnvelope.decode(change).body
                        assertEquals(parentId, body.get("thread_root_id").asString)
                        assertTrue(body.has("ciphertext")); assertFalse(body.has("text"))
                        assertFalse(body.toString().contains("The corrected confidential answer"))
                    }
                    assertEquals("A confidential threaded answer", received.single().text)
                    assertFalse(received.single().edited); assertFalse(received.single().deleted)
                    val cancelledId = writer.reply(space, parent, "An unsent confidential reply", channel)
                    assertTrue(writer.cancelQueuedMessage(space, cancelledId))
                    writer = author(); writer.flush(space)
                    val retained = writer.thread(space, parent, channel = channel).single { it.id == cancelledId }
                    assertTrue(retained.cancelled); assertEquals("An unsent confidential reply", retained.text)
                    creator.refreshThread(space, parent, channel)
                    assertEquals(listOf(replyId), creator.thread(space, parent, channel = channel).map { it.id })
                    creator.refresh(space, channel)
                    assertEquals(1L, creator.timeline(space, channel = channel).single { it.id == parentId }.replyCount)
                }
            }
        } finally { remote.close() }
    }

    @Test fun membersReplyToAnnouncementsWithoutReceivingPermissionToPublishAnnouncements() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("announcement-thread-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("announcement-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("announcement-thread-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Staff announcements", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createChannel(space, SpaceChannelProfile("announcements", kind = SpaceChannelKind.ANNOUNCEMENT)); creator.flush(space)
                    val parentId = creator.send(space, "The staff announcement", channel); creator.flush(space); reader.refresh(space, channel)
                    assertFalse(reader.community(space, channel).canSend)
                    assertThrows(IllegalArgumentException::class.java) { reader.send(space, "A member cannot publish an announcement", channel) }
                    val parent = reader.timeline(space, channel = channel).single()
                    val replyId = reader.reply(space, parent, "A member asks a question in its thread", channel); reader.flush(space)
                    creator.refresh(space, channel); creator.refreshThread(space, parent, channel)
                    assertEquals(listOf(parentId), creator.timeline(space, channel = channel).map { it.id })
                    assertEquals(1L, creator.timeline(space, channel = channel).single().replyCount)
                    val replies = creator.thread(space, parent, channel = channel)
                    assertEquals(listOf(replyId), replies.map { it.id })
                    assertEquals("A member asks a question in its thread", replies.single().text)
                    assertEquals(member.publicKey, replies.single().author)
                    assertFalse(replies.single().rejected)
                    assertFalse(reader.community(space, channel).canSend)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anOlderHeldRootPageCannotReduceAnIndependentlyObservedReplyCount() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("held-thread-summary-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("held-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("held-thread-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var hold = false
                    val captured = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        val reply = remote.exchange(request.envelope())
                        val body = SaitoEnvelope.decode(reply).body
                        val scope = body.getAsJsonObject("scope")
                        if (hold && body.get("request")?.asString == "page" && scope?.get("stream")?.asString == "messages" && !scope.has("thread_id")) {
                            hold = false; captured.complete(Unit); release.await()
                        }
                        reply
                    }
                    val space = creator.create(SpaceProfile("Fresh reply totals", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val parentId = creator.send(space, "The original stays in place"); creator.flush(space); reader.refresh(space)
                    val parent = reader.timeline(space).single()
                    assertEquals(0L, parent.replyCount)
                    hold = true
                    val oldRead = async { runCatching { reader.refresh(space) } }
                    captured.await()
                    try {
                        creator.reply(space, creator.timeline(space).single(), "A genuinely newer reply"); creator.flush(space)
                        reader.refresh(space)
                        assertEquals(1L, reader.timeline(space).single().replyCount)
                    } finally { release.complete(Unit) }
                    oldRead.await()
                    val after = reader.timeline(space).single()
                    assertEquals("A stale callback cannot undo a newer reply summary", 1L, after.replyCount)
                    assertEquals(parentId, after.id); assertEquals(parent.position, after.position)
                    assertEquals("The original stays in place", after.text)
                    reader.refreshThread(space, parent)
                    assertEquals(listOf("A genuinely newer reply"), reader.thread(space, parent).map { it.text })
                }
            }
        }
    }

    @Test fun repliesStayAttachedToTheirOriginalMessageAndOutsideTheTopLevelConversation() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("thread-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("thread-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Focused conversations", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val parentId = creator.send(space, "The original question")
                    val otherId = creator.send(space, "A separate conversation"); creator.flush(space); reader.refresh(space)
                    val parent = reader.timeline(space).single { it.id == parentId }
                    val replyId = reader.reply(space, parent, "A reply to the original question"); reader.flush(space)
                    creator.refresh(space); creator.refreshThread(space, parent)
                    val channel = creator.timeline(space)
                    assertEquals(listOf(parentId, otherId), channel.map { it.id })
                    assertEquals(1L, channel.single { it.id == parentId }.replyCount)
                    assertEquals(0L, channel.single { it.id == otherId }.replyCount)
                    val thread = creator.thread(space, parent)
                    assertEquals(listOf(replyId), thread.map { it.id })
                    assertEquals(listOf("A reply to the original question"), thread.map { it.text })
                    assertEquals(member.publicKey, thread.single().author)
                    assertFalse(thread.single().pending)
                    assertEquals("The original question", parent.text)
                }
            }
        }
    }
}

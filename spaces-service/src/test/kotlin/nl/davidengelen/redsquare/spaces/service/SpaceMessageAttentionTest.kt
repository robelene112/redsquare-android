package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.spaces.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceMessageAttentionTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    @Test fun aNegativeRootHeadCannotClaimThereIsNoUnreadActivityWhileAThreadPageIsMissing() = runBlocking<Unit> {
        val owner = SaitoWallet.fromPrivateKey("1".padStart(64, '0'))
        val service = SaitoWallet.fromPrivateKey("2".padStart(64, '0'))
        val member = SaitoWallet.fromPrivateKey("3".padStart(64, '0'))
        SpacesServiceStore(files.newFolder("unknown-thread-head-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("unknown-thread-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("unknown-thread-reader.sqlite").absolutePath).use { readerStore ->
                    val writer = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = writer.create(SpaceProfile("Unread thread freshness", visibility = SpaceVisibility.PUBLIC)); writer.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); writer.refresh(space)
                    val parentId = reader.send(space, "My thread parent"); reader.flush(space); writer.refresh(space)
                    reader.refreshAttention(space)
                    val parent = requireNotNull(reader.message(space, parentId))
                    reader.markRead(space, reader.community(space).channel, requireNotNull(parent.position))
                    reader.refreshUnread(space); reader.refreshAttention(space)
                    assertEquals(false, reader.community(space).hasUnread)
                    writer.reply(space, requireNotNull(writer.message(space, parentId)), "The still-unseen reply"); writer.flush(space)
                    reader.refreshUnread(space)
                    assertNull("A root-only zero is not a confirmed zero for unseen thread activity", reader.channels(space).single().hasUnread)
                    assertNull(reader.community(space).hasUnread)
                    reader.refreshAttention(space)
                    assertEquals(true, reader.channels(space).single().hasUnread)
                    assertEquals(1L, reader.channels(space).single().personalUnread)
                    assertEquals(1L, reader.community(space).personalUnread)
                }
            }
        }
    }

    @Test fun retainedPrivateMentionsRepairAfterTheExactHistoricalKeyGrantWithoutAnotherMessageRead() = runBlocking<Unit> {
        val owner = SaitoWallet.fromPrivateKey("1".padStart(64, '0'))
        val service = SaitoWallet.fromPrivateKey("2".padStart(64, '0'))
        val member = SaitoWallet.fromPrivateKey("3".padStart(64, '0'))
        SpacesServiceStore(files.newFolder("private-attention-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-attention-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-attention-reader.sqlite").absolutePath).use { readerStore ->
                    var holdHistory = false; var blockContent = false
                    val grants = mutableListOf<String>(); var messages = 0
                    val writer = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (body.get("content_kind")?.asString == "message") messages++
                        if (body.get("content_kind")?.asString == "history-keys") {
                            grants.add(request.envelope())
                            if (holdHistory) { holdHistory = false; throw java.io.IOException("Hold the exact history grant") }
                        }
                        remote.exchange(request.envelope())
                    }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (blockContent && body.get("request")?.asString == "read" && body.getAsJsonObject("scope")?.get("stream")?.asString == "content")
                            throw java.io.IOException("Content read is offline after the historical grant arrives")
                        remote.exchange(request.envelope())
                    }
                    val space = writer.create(SpaceProfile("Private personal activity", visibility = SpaceVisibility.PUBLIC)); writer.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); writer.refresh(space)
                    writer.setAdministrator(space, member.publicKey, true); writer.flush(space)
                    val channel = writer.createPrivateChannel(space, SpaceChannelProfile("Private mentions"), emptySet()); writer.flush(space)
                    val message = writer.send(space, "An encrypted earlier mention for @${member.publicKey}", channel); writer.flush(space)
                    reader.refresh(space); reader.selectChannel(space, channel); reader.flush(space)
                    writer.admitPending(space); holdHistory = true
                    try { writer.flush(space); fail("The historical key original must still be held") } catch (_: java.io.IOException) { }
                    reader.refreshAttention(space, channel)
                    assertNull(reader.attention(space, channel).personal)
                    writer.flush(space); assertEquals(2, grants.size); assertEquals(grants[0], grants[1])
                    blockContent = true
                    try { reader.refreshAttention(space, channel); fail("The content read must fail") } catch (_: java.io.IOException) { }
                    assertEquals(1L, reader.attention(space, channel).personal)
                    assertTrue(requireNotNull(reader.messageAttention(space, message, channel)).mentioned)
                    assertEquals("No replacement message is published", 1, messages)
                }
            }
        }
    }

    @Test fun realMentionsAndRepliesHaveSeparateUnreadCountsAndAuthenticatedEveryoneAuthority() = runBlocking<Unit> {
        val owner = SaitoWallet.fromPrivateKey("1".padStart(64, '0'))
        val service = SaitoWallet.fromPrivateKey("2".padStart(64, '0'))
        val member = SaitoWallet.fromPrivateKey("3".padStart(64, '0'))
        SpacesServiceStore(files.newFolder("attention-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("attention-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("attention-reader.sqlite").absolutePath).use { memberStore ->
                    val writer = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = writer.create(SpaceProfile("Personal activity is precise", visibility = SpaceVisibility.PUBLIC)); writer.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); writer.refresh(space)
                    writer.send(space, "An ordinary channel message"); writer.flush(space)
                    val mentioned = writer.send(space, "A specific question for @${member.publicKey}"); writer.flush(space)
                    val parentId = reader.send(space, "My question has its own discussion"); reader.flush(space); writer.refresh(space)
                    val parent = requireNotNull(writer.message(space, parentId))
                    val replyId = writer.reply(space, parent, "An actual answer in your thread"); writer.flush(space)
                    reader.refreshAttention(space)
                    assertEquals(2L, reader.attention(space).personal)
                    assertEquals(true, reader.attention(space).hasUnread)
                    val root = requireNotNull(reader.message(space, parentId))
                    reader.saveDraft(space, "Keep my current writing")
                    val rootPosition = requireNotNull(root.position)
                    val anchor = SpaceClient.ReadingAnchor(root.id, rootPosition, 9, rootPosition)
                    reader.saveReadingAnchor(space, reader.community(space).channel, anchor)
                    writer.editMessage(space, requireNotNull(writer.message(space, mentioned)), "Question withdrawn, no personal mention"); writer.flush(space)
                    reader.refreshAttention(space)
                    assertEquals(1L, reader.attention(space).personal)
                    writer.editMessage(space, requireNotNull(writer.message(space, mentioned)), "Question restored for @${member.publicKey}"); writer.flush(space)
                    reader.refreshAttention(space)
                    assertEquals(2L, reader.attention(space).personal)
                    reader.setNotificationPolicy(space, SpaceNotificationPolicy(SpaceNotificationMode.SILENT))
                    assertEquals("Silent changes delivery preference, not the received unread count", 2L, reader.attention(space).personal)
                    writer.deleteMessage(space, requireNotNull(writer.message(space, mentioned))); writer.flush(space)
                    reader.refreshAttention(space)
                    assertEquals(1L, reader.attention(space).personal)
                    assertTrue(requireNotNull(reader.message(space, mentioned)).deleted)
                    assertNull(reader.messageAttention(space, mentioned))
                    reader.markRead(space, reader.community(space).channel, requireNotNull(root.position))
                    assertEquals("Reading channel messages must not acknowledge an unseen thread reply", 1L, reader.attention(space).personal)
                    val reply = requireNotNull(reader.message(space, replyId))
                    reader.markThreadRead(space, root, requireNotNull(reply.position))
                    assertEquals(0L, reader.attention(space).personal); assertEquals(false, reader.attention(space).hasUnread)
                    val staff = writer.send(space, "Important @everyone"); writer.flush(space)
                    val literal = reader.send(space, "A member can write the literal @everyone without sending a group alert"); reader.flush(space)
                    reader.refreshAttention(space)
                    assertTrue(requireNotNull(reader.messageAttention(space, staff)).everyone)
                    assertFalse(requireNotNull(reader.messageAttention(space, literal)).everyone)
                    assertEquals("Group alerts are not counted as direct personal mentions", 0L, reader.attention(space).personal)
                    assertEquals(anchor, reader.readingAnchor(space))
                    assertEquals("Keep my current writing", reader.draft(space))
                }
            }
        }
    }
}

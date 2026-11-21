package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
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

class SpaceConversationsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private fun wallet(value: Int) = SaitoWallet.fromPrivateKey(value.toString().padStart(64, '0'))

    @Test(timeout = 45_000) fun anAnchoredLargeWindowRefreshesEveryVisibleEditAcrossByteLimitedPages() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("large-anchor-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("large-anchor-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("large-anchor-reader.sqlite").absolutePath).use { readerStore ->
                    val author = SpaceClient(owner, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = author.create(SpaceProfile("Older writing stays current", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                    repeat(50) { author.send(space, "Original $it") }; author.flush(space)
                    val originals = author.timeline(space)
                    reader.openPublic(space); reader.refresh(space)
                    val anchor = SpaceClient.ReadingAnchor(originals[10].id, 10, 27, 0)
                    reader.saveReadingAnchor(space, reader.community(space).channel, anchor)
                    repeat(200) { author.send(space, "Later $it") }; repeat(4) { author.flush(space) }
                    val payload = "x".repeat(60 * 1024)
                    originals.forEachIndexed { index, message -> author.editMessage(space, message, "Changed $index:$payload") }
                    author.flush(space)
                    reader.refresh(space)
                    val visible = reader.timeline(space, from = 0)
                    assertEquals(originals.map { it.id }, visible.map { it.id })
                    assertEquals((0..49).map { "Changed $it:$payload" }, visible.map { it.text })
                    assertTrue(visible.all { it.edited })
                    assertEquals(anchor, reader.readingAnchor(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aByteLimitedLatestPageDoesNotFillItsRemainingSlotsFromADisconnectedCache() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("byte-window-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("byte-window-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("byte-window-reader.sqlite").absolutePath).use { readerStore ->
                    val author = SpaceClient(owner, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = author.create(SpaceProfile("Bounded history", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                    repeat(50) { author.send(space, "Small $it") }; author.flush(space)
                    reader.openPublic(space); reader.refresh(space)
                    val payload = "x".repeat(60 * 1024)
                    repeat(120) { author.send(space, "$it:$payload") }; repeat(3) { author.flush(space) }
                    reader.refresh(space)
                    val page = reader.timeline(space)
                    assertTrue("Latest never includes the disconnected small-message cache", page.all { it.text?.contains(':') == true })
                    val numbers = page.map { requireNotNull(it.text).substringBefore(':').toInt() }
                    assertTrue("The real byte bound yields fewer than fifty messages", page.size in 1..49)
                    assertEquals((numbers.first()..119).toList(), numbers)
                    assertEquals(page.first().position, reader.historyWindow(space).from)
                    val from = reader.older(space, before = reader.historyWindow(space).from)
                    val earlier = reader.timeline(space, from = from).map { requireNotNull(it.text).substringBefore(':').toInt() }
                    assertEquals(earlier.sorted().distinct(), earlier)
                    assertTrue("Earlier retrieves the gap immediately before this page", earlier.first() < numbers.first())
                    assertEquals((earlier.first()..earlier.last()).toList(), earlier)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun earlierMessagePagesRemainReachableAcrossEditGapsAndALongAbsence() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("message-windows-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("message-windows-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("message-windows-reader.sqlite").absolutePath).use { readerStore ->
                    val author = SpaceClient(owner, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    fun returningReader() = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = author.create(SpaceProfile("Durable conversation windows", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                    repeat(50) { author.send(space, "Post $it") }; author.flush(space)
                    val original = author.timeline(space).first()
                    val reader = returningReader(); reader.openPublic(space); reader.refresh(space)
                    val anchored = reader.timeline(space)[10]
                    val channel = reader.community(space).channel
                    val anchor = SpaceClient.ReadingAnchor(anchored.id, requireNotNull(anchored.position), 27, 0)
                    reader.saveReadingAnchor(space, channel, anchor)
                    reader.saveDraft(space, "Writing survives the history gap")
                    repeat(60) { author.editMessage(space, original, "Correction $it"); author.flush(space) }
                    for (index in 50..249) author.send(space, "Post $index")
                    repeat(4) { author.flush(space) }
                    reader.refresh(space)
                    assertEquals((200..249).map { "Post $it" }, reader.messages(space))
                    assertEquals("The anchored older window receives the latest accepted correction", "Correction 59", reader.timeline(space, from = 0).first().text)
                    assertEquals(anchor, reader.readingAnchor(space))
                    var from = reader.historyWindow(space).from
                    val observedIds = reader.timeline(space).map { it.id }.toMutableSet()
                    for (range in listOf(150..199, 100..149, 50..99, 0..49)) {
                        from = reader.older(space, before = from)
                        val page = reader.timeline(space, from = from)
                        val expected = range.map { if (it == 0) "Correction 59" else "Post $it" }
                        assertEquals(expected, page.map { it.text })
                        assertTrue(page.all { observedIds.add(it.id) })
                    }
                    assertEquals(250, observedIds.size)
                    assertEquals(0L, from)
                    assertFalse(reader.historyWindow(space, from = from).hasEarlier)
                    val restored = returningReader()
                    assertEquals(anchor, restored.readingAnchor(space))
                    assertEquals("Writing survives the history gap", restored.draft(space))
                    assertEquals(original.id, restored.timeline(space, from = from).first().id)
                    assertEquals(27, restored.readingAnchor(space)?.offset)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aColdReaderStillSeesTheMessageAfterAnEditHeavyTail() = runBlocking<Unit> {
        val actor = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("edit-tail-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("edit-tail-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("edit-tail-cold.sqlite").absolutePath).use { readerStore ->
                    val author = SpaceClient(actor, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    val space = author.create(SpaceProfile("Edit-heavy history", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                    val id = author.send(space, "The original text"); author.flush(space)
                    val original = author.timeline(space).single()
                    repeat(60) { index -> author.editMessage(space, original, "Revision $index"); author.flush(space) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    val page = reader.timeline(space)
                    assertEquals(listOf("Revision 59"), page.map { it.text })
                    assertEquals(id, page.single().id)
                    assertEquals(0L, page.single().position)
                    assertTrue(page.single().edited)
                }
            }
        }
    }

    @Test fun onlyTheOriginalAuthorCanEditAndInvalidTargetsRemainRejected() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val author = wallet(3); val member = wallet(4)
        var authoredEdit: String? = null
        SpacesServiceStore(files.newFolder("edit-authority-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("edit-writer.sqlite").absolutePath).use { writerStore ->
                    SqliteClientFixture(files.newFile("edit-member.sqlite").absolutePath).use { memberStore ->
                        val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val writer = SpaceClient(author, "fixture-node", service.publicKey, writerStore, clock) { original ->
                            if (SaitoEnvelope.decode(original.envelope()).body.get("content_kind")?.asString == "message-edit") authoredEdit = original.envelope()
                            remote.exchange(original.envelope())
                        }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val space = manager.create(SpaceProfile("Author control", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                        writer.openPublic(space); writer.joinPublic(space); writer.flush(space)
                        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); writer.refresh(space)
                        val originalId = writer.send(space, "The author's words"); writer.flush(space)
                        writer.editMessage(space, writer.timeline(space).single(), "The author's correction"); writer.flush(space)
                        val body = SaitoEnvelope.decode(requireNotNull(authoredEdit)).body
                        for ((signer, client) in listOf(owner to manager, member to reader)) {
                            client.refresh(space)
                            assertThrows(IllegalArgumentException::class.java) { client.editMessage(space, client.timeline(space).single(), "Someone else's text") }
                            val candidate = SaitoTransaction.create(signer, body.deepCopy().apply {
                                addProperty("operation_id", if (signer == owner) "a".repeat(64) else "b".repeat(64))
                                addProperty("text", "Someone else's text")
                            }, clock.millis(), listOf(service.publicKey))
                            val rejected = remote.exchange(candidate.envelope())
                            assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                            assertEquals(rejected, remote.exchange(candidate.envelope()))
                        }
                        for ((index, target) in listOf("0".repeat(128), originalId).withIndex()) {
                            val candidate = SaitoTransaction.create(author, body.deepCopy().apply {
                                addProperty("operation_id", if (index == 0) "c".repeat(64) else "d".repeat(64))
                                addProperty("target_message_id", target)
                                if (index == 1) addProperty("target_position", 100)
                            }, clock.millis(), listOf(service.publicKey))
                            val rejected = remote.exchange(candidate.envelope())
                            assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                            assertEquals(rejected, remote.exchange(candidate.envelope()))
                        }
                        for (client in listOf(manager, writer, reader)) {
                            client.refresh(space)
                            assertEquals(originalId, client.timeline(space).single().id)
                            assertEquals("The author's correction", client.timeline(space).single().text)
                        }
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aPrivateEditRecoversItsExactCiphertextAndAnotherMemberReadsTheChangedText() = runBlocking<Unit> {
        val actor = wallet(1); val service = wallet(2); val member = wallet(3)
        val sentEdits = mutableListOf<String>()
        var loseReply = true
        SpacesServiceStore(files.newFolder("private-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-edit-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("private-edit-reader.sqlite").absolutePath).use { readerStore ->
                    fun author() = SpaceClient(actor, "fixture-node", service.publicKey, authorStore, clock) { original ->
                        val edit = SaitoEnvelope.decode(original.envelope()).body.get("content_kind")?.asString == "message-edit"
                        if (edit) sentEdits += original.envelope()
                        val response = remote.exchange(original.envelope())
                        if (edit && loseReply) { loseReply = false; throw IOException("Accepted private edit response lost") }
                        response
                    }
                    var writer = author()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = writer.create(SpaceProfile("Private conversations", visibility = SpaceVisibility.PUBLIC)); writer.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); writer.refresh(space)
                    val channel = writer.createPrivateChannel(space, SpaceChannelProfile("Private writing"), setOf(member.publicKey)); writer.flush(space)
                    reader.refresh(space); reader.flush(space); writer.admitPending(space); writer.flush(space); reader.refresh(space)
                    val originalId = writer.send(space, "A confidential first version", channel); writer.flush(space); reader.refresh(space, channel)
                    val original = writer.timeline(space, channel = channel).single()
                    writer.editMessage(space, original, "The confidential correction", channel)
                    try { writer.flush(space); fail("The actual accepted response is deliberately lost") } catch (_: IOException) { }
                    writer = author(); writer.flush(space); reader.refresh(space, channel)
                    assertEquals(2, sentEdits.size)
                    assertEquals("Recovery must retain the exact original ciphertext and signature", sentEdits[0], sentEdits[1])
                    val wire = SaitoEnvelope.decode(sentEdits[0]).body
                    assertEquals("content", wire.get("kind").asString)
                    assertTrue(wire.has("ciphertext")); assertFalse(wire.has("text"))
                    assertFalse(wire.toString().contains("The confidential correction"))
                    val changed = reader.timeline(space, channel = channel).single()
                    assertEquals(originalId, changed.id); assertEquals(original.position, changed.position)
                    assertEquals("The confidential correction", changed.text); assertTrue(changed.edited)
                }
            }
        }
    }

    @Test fun anAuthorEditsAnAcceptedPublicMessageWithoutReplacingItsIdentityOnAnotherClient() = runBlocking<Unit> {
        val actor = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("conversation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("conversation-author.sqlite").absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile("conversation-reader.sqlite").absolutePath).use { readerStore ->
                    val author = SpaceClient(actor, "fixture-node", service.publicKey, authorStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = author.create(SpaceProfile("Conversations", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); author.refresh(space)
                    val originalId = author.send(space, "A first version"); author.flush(space); reader.refresh(space)
                    val original = author.timeline(space).single()
                    val before = reader.timeline(space).single()
                    assertEquals(originalId, before.id)
                    author.editMessage(space, original, "A corrected version"); author.flush(space); reader.refresh(space)
                    val after = reader.timeline(space).single()
                    assertEquals(originalId, after.id)
                    assertEquals(before.position, after.position)
                    assertEquals(actor.publicKey, after.author)
                    assertEquals("A corrected version", after.text)
                    assertTrue(after.edited)
                    assertFalse("Earlier returned observations stay immutable", before.edited)
                    assertEquals("A first version", before.text)
                }
            }
        }
    }
}

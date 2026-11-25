package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
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

class SpaceMessageReactionsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private fun wallet(value: Int) = SaitoWallet.fromPrivateKey(value.toString().padStart(64, '0'))
    private fun choices(message: SpaceClient.Message) = message.reactions.map { Triple(it.emoji, it.count, it.selected) }

    @Test fun reactionsSurviveRealPrivateKeyChangesAndArchiveRefusalsWithoutRevivingAnOldOperation() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("reaction-epoch-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("reaction-epoch-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("reaction-epoch-author.sqlite").absolutePath).use { authorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val attempts = mutableListOf<String>(); val replies = mutableListOf<String>()
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, authorStore, clock) { request ->
                        val reaction = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-reaction"
                        if (reaction) attempts.add(request.envelope())
                        remote.exchange(request.envelope()).also { if (reaction) replies.add(it) }
                    }
                    val space = creator.create(SpaceProfile("Reviewed reaction epochs", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private reaction epochs"), setOf(member.publicKey)); creator.flush(space)
                    writer.refresh(space); writer.flush(space); creator.admitPending(space); creator.flush(space); writer.refresh(space)
                    creator.send(space, "A message remains through key changes", channel); creator.flush(space); writer.refresh(space, channel)
                    val message = writer.timeline(space, channel = channel).single()
                    val reacted = writer.toggleReaction(space, channel, message.id, "👍")
                    assertTrue(reacted.reactions.single { it.emoji == "👍" }.selected)
                    assertTrue(attempts.isEmpty())
                    writer.flush(space)
                    writer.saveDraft(space, "Independent writing survives key changes", channel)
                    val stale = requireNotNull(writer.setReaction(space, message, "🚀", true, channel))
                    creator.revokeChannelAccess(space, channel, member.publicKey); creator.flush(space)
                    writer.flush(space)
                    assertEquals("rejection", SaitoEnvelope.decode(replies.last()).body.get("stream").asString)
                    val rejectedOriginal = attempts.last(); val rejectedReply = replies.last()
                    val oldEpoch = SaitoEnvelope.decode(rejectedOriginal).body.get("epoch").asLong
                    assertEquals(stale, SaitoEnvelope.decode(rejectedOriginal).id)
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.timeline(space, channel = channel).single()))
                    assertFalse(writer.community(space, channel).canReact)
                    creator.grantChannelAccess(space, channel, member.publicKey); creator.flush(space)
                    writer.refresh(space); writer.flush(space); creator.admitPending(space); creator.flush(space); writer.refresh(space, channel)
                    assertTrue(writer.community(space, channel).canReact)
                    val reviewed = requireNotNull(writer.setReaction(space, message, "🚀", true, channel)); writer.flush(space)
                    assertNotEquals(stale, reviewed)
                    assertTrue(SaitoEnvelope.decode(attempts.last()).body.get("epoch").asLong > oldEpoch)
                    assertEquals("content", SaitoEnvelope.decode(replies.last()).body.get("stream").asString)
                    assertEquals(rejectedReply, remote.exchange(rejectedOriginal))
                    assertEquals(setOf(Triple("👍", 1L, true), Triple("🚀", 1L, true)), choices(writer.timeline(space, channel = channel).single()).toSet())
                    writer.setReaction(space, message, "🔥", true, channel)
                    creator.archiveChannel(space, channel, true); creator.flush(space); writer.flush(space)
                    assertEquals("rejection", SaitoEnvelope.decode(replies.last()).body.get("stream").asString)
                    assertFalse(writer.community(space, channel).canReact)
                    assertFalse(writer.timeline(space, channel = channel).single().reactions.any { it.emoji == "🔥" })
                    creator.archiveChannel(space, channel, false); creator.flush(space); writer.refresh(space, channel)
                    writer.setReaction(space, message, "🔥", true, channel); writer.flush(space); creator.refresh(space, channel)
                    assertEquals(setOf("👍", "🚀", "🔥"), creator.timeline(space, channel = channel).single().reactions.map { it.emoji }.toSet())
                    assertTrue(creator.timeline(space, channel = channel).single().reactions.all { it.count == 1L && !it.selected })
                    assertEquals("Independent writing survives key changes", writer.draft(space, channel))
                }
            }
        }
    }

    @Test fun lateHistoricalKeysRepairAnAlreadyReceivedReactionEvenWhenTheNextMessageReadIsOffline() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("late-reaction-keys-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("late-reaction-keys-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("late-reaction-keys-reader.sqlite").absolutePath).use { readerStore ->
                    var holdHistory = false; var blockMessageRead = false
                    val grants = mutableListOf<String>(); var reactionSubmissions = 0
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (body.get("content_kind")?.asString == "message-reaction") reactionSubmissions++
                        if (body.get("content_kind")?.asString == "history-keys") {
                            grants.add(request.envelope())
                            if (holdHistory) { holdHistory = false; throw java.io.IOException("The signed historical key grant is held before delivery") }
                        }
                        remote.exchange(request.envelope())
                    }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (blockMessageRead && body.get("request")?.asString == "read" && body.getAsJsonObject("scope")?.get("stream")?.asString == "messages")
                            throw java.io.IOException("The historical keys arrived before message reading went offline")
                        remote.exchange(request.envelope())
                    }
                    val space = creator.create(SpaceProfile("Late reaction history", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private historical reactions"), emptySet()); creator.flush(space)
                    creator.send(space, "Writing before the reader joined", channel); creator.flush(space)
                    creator.setReaction(space, creator.timeline(space, channel = channel).single(), "👍", true, channel); creator.flush(space)
                    reader.refresh(space); reader.selectChannel(space, channel); reader.flush(space)
                    creator.admitPending(space); holdHistory = true
                    try { creator.flush(space); fail("Historical keys remain undelivered") } catch (_: java.io.IOException) { }
                    reader.refresh(space, channel)
                    val before = reader.timeline(space, channel = channel).single()
                    assertTrue(before.missingKeys); assertFalse(before.reactionsComplete)
                    creator.flush(space)
                    assertEquals(2, grants.size); assertEquals(grants[0], grants[1])
                    blockMessageRead = true
                    try { reader.refresh(space, channel); fail("The next message read is offline after the key grant arrives") } catch (_: java.io.IOException) { }
                    val repaired = reader.presentation(space, channel).messages.single()
                    assertEquals("Writing before the reader joined", repaired.text)
                    assertTrue("The retained reaction repairs locally after historical keys arrive", repaired.reactionsComplete)
                    assertEquals(listOf(Triple("👍", 1L, false)), choices(repaired))
                    assertEquals("No second reaction was needed", 1, reactionSubmissions)
                    blockMessageRead = false; reader.refresh(space, channel)
                    assertEquals(listOf(Triple("👍", 1L, false)), choices(reader.timeline(space, channel = channel).single()))
                }
            }
        }
    }

    @Test fun anOlderGenuineReactionPageCannotRestoreAnAuthorsRemovedChoice() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("held-reaction-page-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("held-reaction-page-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("held-reaction-page-member.sqlite").absolutePath).use { memberStore ->
                    val held = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                    var holdNext = false
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        val result = remote.exchange(request.envelope())
                        if (holdNext && body.get("request")?.asString == "read" && body.getAsJsonObject("scope")?.get("stream")?.asString == "messages") {
                            holdNext = false; held.complete(Unit); release.await()
                        }
                        result
                    }
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Ordered reaction pages", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space)
                    creator.send(space, "A message with a subsequently removed reaction"); creator.flush(space); writer.refresh(space)
                    val message = writer.timeline(space).single()
                    writer.setReaction(space, message, "👍", true); writer.flush(space)
                    holdNext = true
                    val older = async { creator.refresh(space) }; held.await()
                    try {
                        writer.setReaction(space, message, "👍", false); writer.flush(space)
                        creator.refresh(space)
                        assertTrue(creator.timeline(space).single().reactions.isEmpty())
                    } finally { release.complete(Unit) }
                    older.await()
                    val restored = creator.timeline(space).single()
                    assertTrue(restored.reactionsComplete); assertTrue(restored.reactions.isEmpty())
                    assertEquals(message.id, restored.id)
                }
            }
        }
    }

    @Test fun malformedReactionSelectionsHaveADurableRefusalWithoutBreakingAnAcceptedReaction() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("reaction-selection-validation").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("reaction-selection-owner.sqlite").absolutePath).use { store ->
                val originals = mutableListOf<String>()
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, store, clock) { request ->
                    if (SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-reaction") originals.add(request.envelope())
                    remote.exchange(request.envelope())
                }
                val space = creator.create(SpaceProfile("Validated reaction selections", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                creator.send(space, "A valid reaction remains intact"); creator.flush(space); creator.refresh(space)
                creator.setReaction(space, creator.timeline(space).single(), "👍", true); creator.flush(space)
                val valid = SaitoEnvelope.decode(originals.single())
                val invalid = listOf("not-json", "[1]", "[\"👍\",\"👍\"]", "null", "[\"\"]") + listOf("""["\uD800"]""")
                for ((index, text) in invalid.withIndex()) {
                    val original = SaitoTransaction.create(owner, valid.body.deepCopy().apply {
                        addProperty("operation_id", (index + 8).toString(16).padStart(64, '0')); addProperty("text", text)
                    }, clock.millis(), listOf(service.publicKey))
                    val refusal = remote.exchange(original.envelope())
                    assertEquals("rejection", SaitoEnvelope.decode(refusal).body.get("stream").asString)
                    assertEquals("A malformed selection keeps its exact refusal on retry", refusal, remote.exchange(original.envelope()))
                }
                creator.refresh(space)
                assertEquals(listOf(Triple("👍", 1L, true)), choices(creator.timeline(space).single()))
            }
        }
    }

    @Test fun everyOwnChoiceRemainsVisibleAndRemovableWhenTheMessageHasMoreThanFiftyEmojiKinds() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("varied-reactions-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("varied-reactions-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Many reaction choices", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                creator.send(space, "A message with fifty-one different emoji choices"); creator.flush(space)
                val emojis = (0 until 51).map { String(Character.toChars(0x1f600 + it)) }
                val selections = listOf(emojis.take(12), emojis.drop(12).take(12), emojis.drop(24).take(11), emojis.drop(35))
                val paths = selections.indices.map { files.newFile("varied-reactions-member-$it.sqlite").absolutePath }
                for ((index, selection) in selections.withIndex()) SqliteClientFixture(paths[index]).use { store ->
                    val writer = SpaceClient(wallet(index + 3), "fixture-node", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); writer.refresh(space)
                    val message = writer.timeline(space).single()
                    selection.forEach { writer.setReaction(space, message, it, true) }; writer.flush(space)
                }
                creator.refresh(space)
                val received = creator.timeline(space).single()
                assertTrue(received.reactionsComplete); assertTrue(received.reactionsHaveMore)
                val firstPage = creator.reactionPage(space, received)
                assertEquals(50, firstPage.choices.size); assertTrue(firstPage.complete)
                val next = requireNotNull(firstPage.nextEmoji)
                val secondPage = creator.reactionPage(space, received, next)
                assertEquals(1, secondPage.choices.size); assertNull(secondPage.nextEmoji)
                assertEquals(emojis.toSet(), (firstPage.choices + secondPage.choices).map { it.emoji }.toSet())
                assertTrue((firstPage.choices + secondPage.choices).all { it.count == 1L && !it.selected })
                for ((index, selection) in selections.withIndex()) SqliteClientFixture(paths[index]).use { store ->
                    val writer = SpaceClient(wallet(index + 3), "fixture-node", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                    writer.refresh(space)
                    val own = writer.timeline(space).single().reactions.filter { it.selected }.map { it.emoji }.toSet()
                    assertEquals("Every own choice remains visible independently of the bounded display page", selection.toSet(), own)
                }
                for ((index, selection) in selections.withIndex()) SqliteClientFixture(paths[index]).use { store ->
                    val writer = SpaceClient(wallet(index + 3), "fixture-node", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                    writer.refresh(space)
                    val message = writer.timeline(space).single()
                    for (emoji in selection) {
                        writer.setReaction(space, message, emoji, false)
                        assertFalse(writer.timeline(space).single().reactions.any { it.emoji == emoji && it.selected })
                    }
                    writer.flush(space)
                }
                creator.refresh(space)
                assertTrue(creator.timeline(space).single().reactions.isEmpty())
            }
        }
    }

    @Test(timeout = 180_000) fun aColdReaderPreservesItsOwnChoicesBeforeTheWholeGroupHasLoaded() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val lastAuthor = wallet(154)
        SpacesServiceStore(files.newFolder("cold-own-reactions-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("cold-own-reactions-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Large reaction group", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                creator.send(space, "A message whose last reacting member opens a fresh installation"); creator.flush(space)
                // Prepare real signed service participants without repeatedly
                // rehydrating the entire growing roster in 152 throwaway clients.
                val seedOriginals = mutableListOf<String>()
                SqliteClientFixture(files.newFile("cold-own-reactions-seed.sqlite").absolutePath).use { seed ->
                    val writer = SpaceClient(wallet(3), "fixture-node", service.publicKey, seed, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (body.get("kind")?.asString == "join-public" || body.get("content_kind")?.asString == "message-reaction") seedOriginals.add(request.envelope())
                        remote.exchange(request.envelope())
                    }
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); writer.refresh(space)
                    writer.setReaction(space, writer.timeline(space).single(), "👍", true); writer.flush(space)
                }
                val joinTemplate = SaitoEnvelope.decode(seedOriginals.first())
                val reactionTemplate = SaitoEnvelope.decode(seedOriginals.last())
                var control = joinTemplate.id
                for (index in 1..151) {
                    val author = wallet(index + 3)
                    val join = SaitoTransaction.create(author, joinTemplate.body.deepCopy().apply {
                        addProperty("operation_id", (index * 2 + 1).toString(16).padStart(64, '0'))
                        addProperty("control_id", control)
                    }, clock.millis(), listOf(service.publicKey))
                    val joined = SaitoEnvelope.decode(remote.exchange(join.envelope()))
                    assertEquals("Every participant actually joins through the service", "control", joined.body.get("stream").asString)
                    control = join.id
                    val reaction = SaitoTransaction.create(author, reactionTemplate.body.deepCopy().apply {
                        addProperty("operation_id", (index * 2 + 2).toString(16).padStart(64, '0'))
                        addProperty("control_id", control)
                        addProperty("text", if (index == 151) "[\"👀\"]" else "[\"👍\"]")
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("Every selection is a real accepted signed original", "content", SaitoEnvelope.decode(remote.exchange(reaction.envelope())).body.get("stream").asString)
                }
                SqliteClientFixture(files.newFile("cold-own-reactions-fresh.sqlite").absolutePath).use { fresh ->
                    val reader = SpaceClient(lastAuthor, "fixture-node", service.publicKey, fresh, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space); reader.refresh(space)
                    val partial = reader.timeline(space).single()
                    assertFalse("Group hydration remains bounded", partial.reactionsComplete)
                    assertEquals("Own selection is available independently of group paging", setOf("👀"), partial.reactions.filter { it.selected }.map { it.emoji }.toSet())
                    reader.setReaction(space, partial, "🚀", true); reader.flush(space)
                    repeat(3) { creator.refresh(space) }
                    val received = creator.timeline(space).single()
                    assertTrue(received.reactionsComplete)
                    assertEquals(setOf(Triple("👍", 151L, false), Triple("👀", 1L, false), Triple("🚀", 1L, false)), choices(received).toSet())
                }
            }
        }
    }

    @Test(timeout = 180_000) fun reactionsPageBeyondFiftyAuthorsAndUnchangedRefreshesReuseTheirAcceptedProjection() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2)
        SpacesServiceStore(files.newFolder("paged-reactions-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("paged-reactions-owner.sqlite").absolutePath).use { ownerStore ->
                val actorReceipts = mutableListOf<Int>()
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                    remote.exchange(request.envelope()).also { result ->
                        val body = SaitoEnvelope.decode(result).body
                        if (body.get("request")?.asString == "page") actorReceipts.add(body.getAsJsonArray("items").sumOf { value ->
                            if (value.isJsonObject) value.asJsonObject.getAsJsonArray("reactions")?.size() ?: 0 else 0
                        })
                    }
                }
                val space = creator.create(SpaceProfile("Bounded reaction hydration", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                creator.send(space, "A message with fifty-one independent reactions"); creator.flush(space)
                repeat(51) { index ->
                    SqliteClientFixture(files.newFile("paged-reactions-member-$index.sqlite").absolutePath).use { store ->
                        val writer = SpaceClient(wallet(index + 3), "fixture-node", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                        writer.openPublic(space); writer.joinPublic(space); writer.flush(space); writer.refresh(space)
                        val message = writer.timeline(space).single()
                        writer.setReaction(space, message, "👍", true); writer.flush(space)
                    }
                }
                creator.refresh(space)
                val received = creator.timeline(space).single()
                assertTrue("All fifty-one accepted author states must be available", received.reactionsComplete)
                assertEquals(listOf(Triple("👍", 51L, false)), choices(received))
                val before = actorReceipts.sum()
                creator.refresh(space)
                assertEquals("Unchanged refresh must not download the actor histories again", before, actorReceipts.sum())
                assertEquals(listOf(Triple("👍", 51L, false)), choices(creator.timeline(space).single()))
            }
        }
    }

    @Test fun privateThreadReactionsRetainExactCiphertextAfterALostReplyAndPermitMemberReactionsToAnnouncements() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        val directory = files.newFolder("private-reactions-service").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        try {
            SqliteClientFixture(files.newFile("private-reactions-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-reactions-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var lose = true
                    val attempts = mutableListOf<String>()
                    fun author() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        val reaction = SaitoEnvelope.decode(request.envelope()).body.get("content_kind")?.asString == "message-reaction"
                        if (reaction) attempts.add(request.envelope())
                        remote.exchange(request.envelope()).also { if (reaction && lose) {
                            lose = false; throw java.io.IOException("The private reaction was accepted before its response was lost")
                        } }
                    }
                    var writer = author()
                    val space = creator.create(SpaceProfile("Encrypted announcement reactions", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private announcements", kind = SpaceChannelKind.ANNOUNCEMENT), setOf(member.publicKey)); creator.flush(space)
                    writer.refresh(space); writer.flush(space); creator.admitPending(space); creator.flush(space); writer.refresh(space)
                    creator.send(space, "A confidential staff announcement", channel); creator.flush(space); writer.refresh(space, channel)
                    val parent = writer.timeline(space, channel = channel).single()
                    writer.reply(space, parent, "A confidential member answer", channel); writer.flush(space); writer.refreshThread(space, parent, channel)
                    val child = writer.thread(space, parent, channel = channel).single()
                    writer.setReaction(space, child, "👍", true, channel)
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.thread(space, parent, channel = channel).single()))
                    try { writer.flush(space); fail("The received acknowledgment was lost") } catch (_: java.io.IOException) { }
                    creator.refreshThread(space, parent, channel)
                    assertEquals(listOf(Triple("👍", 1L, false)), choices(creator.thread(space, parent, channel = channel).single()))
                    remote.close(); remote = SpacesServiceStore(directory, service, clock)
                    writer = author()
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.thread(space, parent, channel = channel).single()))
                    writer.flush(space)
                    assertEquals(2, attempts.size); assertEquals(attempts[0], attempts[1])
                    val wire = SaitoEnvelope.decode(attempts[0]).body
                    assertEquals("content", wire.get("kind").asString); assertTrue(wire.has("ciphertext")); assertFalse(wire.has("text"))
                    assertFalse(wire.toString().contains("👍")); assertEquals(child.id, wire.get("target_message_id").asString)
                    assertEquals(parent.id, wire.get("thread_root_id").asString)
                    writer.setReaction(space, parent, "🚀", true, channel); writer.flush(space); creator.refresh(space, channel)
                    assertEquals(listOf(Triple("🚀", 1L, false)), choices(creator.timeline(space, channel = channel).single()))
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.thread(space, parent, channel = channel).single()))
                    assertThrows(IllegalArgumentException::class.java) { writer.send(space, "A member cannot publish an announcement", channel) }
                }
            }
        } finally { remote.close() }
    }

    @Test fun reactionsRespondImmediatelyAndConvergePerAuthorAcrossRapidChoicesAndReopen() = runBlocking<Unit> {
        val owner = wallet(1); val service = wallet(2); val member = wallet(3)
        SpacesServiceStore(files.newFolder("message-reactions-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("message-reactions-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("message-reactions-member.sqlite").absolutePath).use { memberStore ->
                    fun manager() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var creator = manager()
                    val writer = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Immediate message reactions", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    writer.openPublic(space); writer.joinPublic(space); writer.flush(space); creator.refresh(space)
                    creator.send(space, "A message worth reacting to"); creator.flush(space); writer.refresh(space)
                    val root = writer.timeline(space).single()
                    assertTrue(root.reactions.isEmpty())
                    writer.setReaction(space, root, "👍", true)
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.timeline(space).single()))
                    assertTrue(creator.timeline(space).single().reactions.isEmpty())
                    writer.setReaction(space, root, "👍", false)
                    assertTrue(writer.timeline(space).single().reactions.isEmpty())
                    writer.setReaction(space, root, "👍", true)
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(writer.timeline(space).single()))
                    writer.flush(space); creator.refresh(space)
                    assertEquals(listOf(Triple("👍", 1L, false)), choices(creator.timeline(space).single()))
                    creator.setReaction(space, creator.timeline(space).single(), "👍", true)
                    assertEquals(listOf(Triple("👍", 2L, true)), choices(creator.timeline(space).single()))
                    creator.flush(space); writer.refresh(space)
                    assertEquals(listOf(Triple("👍", 2L, true)), choices(writer.timeline(space).single()))
                    writer.setReaction(space, root, "👍", false)
                    assertEquals(listOf(Triple("👍", 1L, false)), choices(writer.timeline(space).single()))
                    writer.flush(space); creator.refresh(space)
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(creator.timeline(space).single()))
                    creator = manager()
                    assertEquals(listOf(Triple("👍", 1L, true)), choices(creator.timeline(space).single()))
                    writer.setReaction(space, root, "🚀", true); writer.flush(space); creator.refresh(space)
                    assertEquals(setOf(Triple("👍", 1L, true), Triple("🚀", 1L, false)), choices(creator.timeline(space).single()).toSet())
                    assertEquals("A message worth reacting to", creator.timeline(space).single().text)
                }
            }
        }
    }
}

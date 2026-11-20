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

    @Test(timeout = 45_000) fun leavingStopsFuturePrivateKeysWithoutRecallingCopiedHistory() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-private-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-private-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-private-member.sqlite").absolutePath).use { memberStore ->
                    var head = ""
                    val originals = mutableListOf<SaitoTransaction>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        originals.add(original)
                        remote.exchange(original.envelope()).also { reply ->
                            if (SaitoEnvelope.decode(reply).body.get("stream")?.asString == "control") head = original.id
                        }
                    }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create("A community members can leave"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space)
                    creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    creator.send(space, "Already received history"); creator.flush(space); reader.refresh(space)
                    val past = originals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                    val copied = memberStore.transaction {
                        val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/state")).toString(Charsets.UTF_8)).asJsonObject
                        nl.davidengelen.redsquare.spaces.SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                    }
                    fun leave(actor: SaitoWallet, operation: String) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "leave"); addProperty("space_id", space); addProperty("control_id", head)
                        addProperty("operation_id", operation)
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("The owner must transfer ownership before leaving", "rejection",
                        SaitoEnvelope.decode(remote.exchange(leave(owner, "b".repeat(64)).envelope())).body.get("stream").asString)
                    val departure = leave(member, "c".repeat(64))
                    val accepted = remote.exchange(departure.envelope())
                    assertEquals("An ordinary member can leave their actual private community", "control", SaitoEnvelope.decode(accepted).body.get("stream").asString)
                    assertEquals(accepted, remote.exchange(departure.envelope()))
                    creator.refresh(space)
                    assertEquals(listOf(owner.publicKey), creator.members(space).map { it.publicKey })
                    assertFalse("Future private writing waits for removal of the departed device", creator.community(space).canSend)
                    creator.flush(space)
                    assertTrue(creator.community(space).canSend)
                    creator.send(space, "Written after the departure key update"); creator.flush(space)
                    val future = originals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                    try { copied.read(future.envelope()); fail("Copied departed keys cannot decrypt future accepted text") }
                    catch (_: nl.davidengelen.redsquare.spaces.SpaceCrypto.MissingKeys) { }
                    assertEquals("Already received history", copied.read(past.envelope()).text)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun leavingRetainsItsExactOriginalAndTruthfulStatusAcrossALostResponseAndColdReturn() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-recovery-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-recovery-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-recovery-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var loseDeparture = true
                    val departures = mutableListOf<String>()
                    fun memberClient() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { original ->
                        val received = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "leave") {
                            departures.add(original.envelope())
                            if (loseDeparture) { loseDeparture = false; throw java.io.IOException("Owned accepted departure reply lost") }
                        }
                        received
                    }
                    var reader = memberClient()
                    val space = creator.create("Durable community departure"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space)
                    creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    creator.send(space, "Past conversation stays readable"); creator.flush(space); reader.refresh(space)
                    reader.saveDraft(space, "Writing kept when I leave")
                    try { creator.leave(space); fail("An owner must transfer ownership first") } catch (_: IllegalArgumentException) { }
                    assertNull(creator.membershipChange(space))
                    val original = reader.leave(space)
                    assertEquals(original, reader.membershipChange(space)?.id)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, reader.membershipChange(space)?.status)
                    assertTrue("Queued departure is not a remotely accepted membership result", reader.community(space).joined)
                    assertFalse(reader.community(space).canSend)
                    try { reader.flush(space); fail("The actual accepted departure response is lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, reader.membershipChange(space)?.status)
                    creator.refresh(space); creator.flush(space)
                    assertEquals(listOf(owner.publicKey), creator.members(space).map { it.publicKey })
                    reader = memberClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, reader.membershipChange(space)?.status)
                    reader.flush(space)
                    assertEquals(2, departures.size); assertEquals(departures[0], departures[1])
                    assertEquals(original, SaitoEnvelope.decode(departures[1]).id)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, reader.membershipChange(space)?.status)
                    assertFalse(reader.community(space).joined); assertFalse(reader.community(space).canSend)
                    assertFalse(reader.community(space).canInvite); assertFalse(reader.community(space).canManage)
                    assertEquals("Writing kept when I leave", reader.draft(space))
                    assertEquals(listOf("Past conversation stays readable"), reader.messages(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aDepartureCannotBeUndoneByAnotherJoinRequestedBeforeTheFirstAdmission() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-old-request-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-old-request-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-old-request-first.sqlite").absolutePath).use { firstStore ->
                    SqliteClientFixture(files.newFile("leave-old-request-second.sqlite").absolutePath).use { secondStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val first = SpaceClient(member, "fixture-node", service.publicKey, firstStore, clock) { remote.exchange(it.envelope()) }
                        val second = SpaceClient(member, "fixture-node", service.publicKey, secondStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create("Explicit membership intent"); creator.flush(space)
                        val invitation = creator.invite(space); creator.flush(space)
                        first.join(invitation); first.flush(space)
                        second.join(invitation); second.flush(space) // Another genuine original from a separately initialized device.
                        creator.admitPending(space); creator.flush(space); first.refresh(space)
                        assertTrue(first.community(space).joined)
                        first.leave(space); first.flush(space)
                        creator.refresh(space); creator.flush(space)
                        assertEquals(listOf(owner.publicKey), creator.members(space).map { it.publicKey })
                        creator.admitPending(space); creator.flush(space)
                        assertEquals("An accepted departure requires fresh intent before any later admission",
                            listOf(owner.publicKey), creator.members(space).map { it.publicKey })
                        assertFalse(first.community(space).joined)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anOldDeferredInvitationCannotBlockFreshAdmissionsAfterItsApplicantLeaves() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-deferred-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-deferred-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-deferred-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("leave-deferred-applicant.sqlite").absolutePath).use { applicantStore ->
                        SqliteClientFixture(files.newFile("leave-deferred-fresh.sqlite").absolutePath).use { freshStore ->
                            val adminWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                            val freshWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "5")
                            val originals = mutableListOf<SaitoTransaction>()
                            var head = ""
                            fun submit(original: SaitoTransaction) = remote.exchange(original.envelope()).also { reply ->
                                if (SaitoEnvelope.decode(reply).body.get("stream")?.asString == "control") head = original.id
                            }
                            val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { originals.add(it); submit(it) }
                            val administrator = SpaceClient(adminWallet, "fixture-node", service.publicKey, adminStore, clock) { submit(it) }
                            val applicant = SpaceClient(member, "fixture-node", service.publicKey, applicantStore, clock) { submit(it) }
                            val fresh = SpaceClient(freshWallet, "fixture-node", service.publicKey, freshStore, clock) { submit(it) }
                            val profile = SpaceProfile("Deferred membership")
                            val space = creator.create(profile); creator.flush(space)
                            val invitation = creator.invite(space); creator.flush(space)
                            administrator.join(invitation); administrator.flush(space)
                            creator.admitPending(space); creator.flush(space); administrator.refresh(space)
                            creator.setAdministrator(space, adminWallet.publicKey, true); creator.flush(space)
                            val channel = creator.createChannel(space, nl.davidengelen.redsquare.spaces.SpaceChannelProfile("Planning")); creator.flush(space)
                            val creation = SaitoEnvelope.decode(originals.last { SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString == "create-channel" }.envelope())
                            val scope = nl.davidengelen.redsquare.spaces.SpaceCrypto.Scope(service.publicKey, space, channel, creation.body.get("key_generation").asString)
                            val bearer = SaitoWallet.fromPrivateKey("0".repeat(63) + "6")
                            val previewKey = ByteArray(32) { 9 }
                            val operation = "e".repeat(64)
                            // Independent version1 peer encoding, as in the existing discovery interoperability fixtures.
                            val nonce = ByteArray(12) { 8 }
                            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                                init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(previewKey, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
                                updateAAD("SaitoSpacesPreview/v1:${scope.groupId}:$operation".toByteArray(Charsets.UTF_8))
                            }
                            val preview = java.util.Base64.getEncoder().encodeToString(nonce + cipher.doFinal(profile.json().toString().toByteArray(Charsets.UTF_8)))
                            // A compatible peer invites a new member directly into this existing private child.
                            val childInvite = SaitoTransaction.create(owner, JsonObject().apply {
                                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                                addProperty("kind", "invite"); addProperty("space_id", space); addProperty("control_id", head)
                                addProperty("operation_id", operation); addProperty("invite_key", bearer.publicKey)
                                addProperty("expires_at", clock.millis() + 60_000); addProperty("preview_format", "profile-v1")
                                addProperty("channel_id", channel)
                                addProperty("preview", preview)
                            }, clock.millis(), listOf(service.publicKey))
                            assertEquals("control", SaitoEnvelope.decode(submit(childInvite)).body.get("stream").asString)
                            creator.refresh(space); administrator.refresh(space)
                            val childReference = JsonObject().apply {
                                addProperty("version", 1); addProperty("node", "fixture-node")
                                add("scope", JsonObject().apply {
                                    addProperty("service", scope.serviceKey); addProperty("space", scope.spaceId)
                                    addProperty("channel", scope.channelId); addProperty("generation", scope.generation)
                                })
                                addProperty("invitation", childInvite.id); addProperty("capability", "0".repeat(63) + "6")
                                addProperty("preview_key", java.util.Base64.getEncoder().encodeToString(previewKey))
                            }
                            val childLink = "saito://spaces/join#" + java.util.Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(childReference.toString().toByteArray(Charsets.UTF_8))
                            applicant.join(childLink); applicant.flush(space)
                            administrator.admitPending(space); administrator.flush(space) // This holder has not received the child keys yet.
                            assertFalse(administrator.channels(space).single { it.id == channel }.ready)
                            creator.admitPending(space); creator.flush(space); applicant.refresh(space)
                            assertTrue(applicant.community(space).joined)
                            applicant.leave(space); applicant.flush(space); creator.refresh(space); creator.flush(space)
                            administrator.refresh(space); administrator.selectChannel(space, channel); administrator.flush(space)
                            creator.admitPending(space); creator.flush(space); administrator.refresh(space, channel)
                            assertTrue(administrator.channels(space).single { it.id == channel }.ready)
                            fresh.join(invitation); fresh.flush(space)
                            administrator.admitPending(space); administrator.flush(space)
                            fresh.refresh(space); creator.refresh(space)
                            assertTrue("Discarding a pre-departure deferred request allows the actual fresh request to complete", fresh.community(space).joined)
                            assertTrue(creator.members(space).any { it.publicKey == freshWallet.publicKey })
                            assertFalse(creator.members(space).any { it.publicKey == member.publicKey })
                        }
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun explicitlyRejoiningCreatesFreshDeviceKeysWithoutDiscardingAlreadyReceivedHistory() = verifyExplicitRejoin(true)
    @Test(timeout = 45_000) fun aFreshRejoinRequestedBeforeOldDeviceRotationKeepsItsOwnWelcomeMaterial() = verifyExplicitRejoin(false)

    private fun verifyExplicitRejoin(rotateBeforeReturn: Boolean) = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-rejoin-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-rejoin-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-rejoin-reader.sqlite").absolutePath).use { readerStore ->
                    var holdReturnedHistory = false
                    val joins = mutableListOf<String>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        if (holdReturnedHistory && SaitoEnvelope.decode(original.envelope()).body.get("content_kind")?.asString == "history-keys") {
                            holdReturnedHistory = false
                            throw java.io.IOException("Owned rejoin history grant held before delivery")
                        }
                        remote.exchange(original.envelope())
                    }
                    fun readerClient() = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { original ->
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "join") joins.add(original.envelope())
                        remote.exchange(original.envelope())
                    }
                    var reader = readerClient()
                    val space = creator.create("An explicit return"); creator.flush(space)
                    val invitation = creator.invite(space); creator.flush(space)
                    reader.join(invitation); reader.flush(space)
                    creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    creator.send(space, "Already received before departure"); creator.flush(space); reader.refresh(space)
                    reader.leave(space); reader.flush(space); creator.refresh(space)
                    if (rotateBeforeReturn) {
                        creator.flush(space)
                        creator.send(space, "Available history from while I was away"); creator.flush(space)
                    }
                    reader.join(invitation); reader.flush(space) // A deliberate new signed intent, using the still-valid invitation.
                    reader = readerClient() // Fresh constructor must recover the new admission material from the actual store.
                    if (!rotateBeforeReturn) {
                        creator.flush(space)
                        creator.send(space, "Available history from while I was away"); creator.flush(space)
                    }
                    creator.admitPending(space); holdReturnedHistory = true
                    try { creator.flush(space); fail("Actual historical key delivery is deliberately held") } catch (_: java.io.IOException) { }
                    reader.refresh(space)
                    assertTrue(reader.community(space).joined)
                    assertTrue("A fresh device admission must not discard already received history while a new grant is unavailable",
                        reader.messages(space).contains("Already received before departure"))
                    assertFalse(reader.messages(space).contains("Available history from while I was away"))
                    assertEquals(2, joins.size)
                    assertNotEquals(SaitoEnvelope.decode(joins[0]).body.getAsJsonObject("key_package").get("device").asString,
                        SaitoEnvelope.decode(joins[1]).body.getAsJsonObject("key_package").get("device").asString)
                    creator.flush(space); reader.refresh(space)
                    assertEquals(listOf("Already received before departure", "Available history from while I was away"), reader.messages(space))
                    creator.send(space, "A new message after my return"); creator.flush(space); reader.refresh(space)
                    assertEquals(listOf("Already received before departure", "Available history from while I was away", "A new message after my return"), reader.messages(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anOlderDepartureRejectionCannotWaitForeverForAnAlreadyKnownControl() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("leave-old-rejection-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("leave-old-rejection-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("leave-old-rejection-reader.sqlite").absolutePath).use { readerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var holdOldDisposition = true
                    lateinit var reader: SpaceClient
                    reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { original ->
                        val received = remote.exchange(original.envelope())
                        if (holdOldDisposition && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "leave") {
                            holdOldDisposition = false
                            assertEquals("rejection", SaitoEnvelope.decode(received).body.get("stream").asString)
                            // Another genuine control arrives before this already-created rejection is delivered.
                            val space = SaitoEnvelope.decode(original.envelope()).body.get("space_id").asString
                            creator.archiveChannel(space, creator.community(space).channel, false); creator.flush(space)
                            reader.refresh(space)
                        }
                        received
                    }
                    val space = creator.create(SpaceProfile("Ordered departure feedback", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); reader.refresh(space); creator.refresh(space)
                    reader.saveDraft(space, "Writing survives the rejected departure")
                    val rejected = reader.leave(space)
                    creator.archiveChannel(space, creator.community(space).channel, true); creator.flush(space)
                    reader.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, reader.membershipChange(space)?.status)
                    assertTrue(reader.community(space).joined)
                    assertTrue("A stale rejection must not require a predecessor which is already authenticated locally", reader.community(space).canSend)
                    reader.refresh(space)
                    assertTrue(reader.community(space).canSend)
                    assertEquals("Writing survives the rejected departure", reader.draft(space))
                    val reviewed = reader.leave(space); assertNotEquals(rejected, reviewed)
                    reader.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, reader.membershipChange(space)?.status)
                    assertFalse(reader.community(space).joined)
                    creator.refresh(space)
                    assertEquals(listOf(owner.publicKey), creator.members(space).map { it.publicKey })
                }
            }
        }
    }

    @Test(timeout = 45_000) fun publicRejoinCannotPublishWritingQueuedBeforeThePreviousDeparture() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("membership-writing-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("membership-writing-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("membership-writing-reader.sqlite").absolutePath).use { readerStore ->
                    var head = ""
                    var holdOldWriting = true
                    fun submit(original: SaitoTransaction) = remote.exchange(original.envelope()).also { reply ->
                        if (SaitoEnvelope.decode(reply).body.get("stream")?.asString == "control") head = original.id
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { submit(it) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { original ->
                        if (holdOldWriting && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "public-content")
                            throw java.io.IOException("Owned old signed writing held before delivery")
                        submit(original)
                    }
                    val space = creator.create(SpaceProfile("A new membership term", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    val channel = creator.community(space).channel
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); reader.refresh(space)
                    val queued = reader.send(space, "Writing signed before departure")
                    try { reader.flush(space); fail("The old original is deliberately held") } catch (_: java.io.IOException) { }
                    // A compatible client using the same member wallet deliberately leaves and rejoins while that original remains queued.
                    fun membership(kind: String, operation: String) = SaitoTransaction.create(member, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("operation_id", operation)
                        addProperty("control_id", head)
                        if (kind == "join-public") { addProperty("channel_id", channel); addProperty("rules_token", "") }
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(submit(membership("leave", "d".repeat(64)))).body.get("stream").asString)
                    assertEquals("control", SaitoEnvelope.decode(submit(membership("join-public", "e".repeat(64)))).body.get("stream").asString)
                    reader.refresh(space)
                    assertTrue(reader.community(space).joined)
                    holdOldWriting = false; reader.flush(space); creator.refresh(space)
                    assertEquals("Ending membership must not silently publish its old queued writing after a new join", emptyList<String>(), creator.messages(space))
                    assertTrue(reader.timeline(space).single { it.id == queued }.rejected)
                    reader.send(space, "Fresh writing after the reviewed return"); reader.flush(space); creator.refresh(space)
                    assertEquals(listOf("Fresh writing after the reviewed return"), creator.messages(space))
                }
            }
        }
    }

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

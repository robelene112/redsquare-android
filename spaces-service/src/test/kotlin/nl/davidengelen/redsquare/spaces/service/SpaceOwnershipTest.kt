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

class SpaceOwnershipTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun onlyTheOwnerCanOfferOwnershipAndOnlyTheChosenMemberCanAcceptIt() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("ownership-other.sqlite").absolutePath).use { otherStore ->
                        var head = ""
                        var highestPosition = -1L
                        var operation = 0
                        fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                            val body = SaitoEnvelope.decode(receipt).body
                            if (body.get("stream")?.asString == "control" && body.get("position").asLong > highestPosition) {
                                highestPosition = body.get("position").asLong; head = original.id
                            }
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val recipient = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                        val outsider = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) { deliver(it) }
                        val space = creator.create(SpaceProfile("Deliberate ownership", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(recipient, outsider)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space)
                        fun original(actor: SaitoWallet, kind: String, fields: JsonObject.() -> Unit = {}) = SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                            addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); fields()
                        }, clock.millis(), listOf(service.publicKey))
                        fun stream(request: SaitoTransaction) = SaitoEnvelope.decode(deliver(request)).body.get("stream").asString
                        assertEquals("rejection", stream(original(member, "propose-owner") { addProperty("member", other.publicKey) }))
                        val proposal = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                        val offered = deliver(proposal)
                        assertEquals("An owner can offer ownership to an existing member", "control", SaitoEnvelope.decode(offered).body.get("stream").asString)
                        creator.refresh(space); recipient.refresh(space)
                        assertEquals("OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                        assertEquals("MEMBER", recipient.members(space).single { it.publicKey == member.publicKey }.role.name)
                        assertEquals("An offer alone does not release the owner's responsibility", "rejection", stream(original(owner, "leave")))
                        assertEquals("rejection", stream(original(other, "accept-owner") { addProperty("proposal_id", proposal.id) }))
                        assertEquals("control", stream(original(member, "accept-owner") { addProperty("proposal_id", proposal.id) }))
                        assertEquals(offered, deliver(proposal))
                        creator.refresh(space); recipient.refresh(space); outsider.refresh(space)
                        val expected = mapOf(owner.publicKey to "MEMBER", member.publicKey to "OWNER", other.publicKey to "MEMBER")
                        for (client in listOf(creator, recipient, outsider)) assertEquals(expected, client.members(space).associate { it.publicKey to it.role.name })
                        assertEquals("A former owner has no residual administrator powers", "rejection", stream(original(owner, "set-role") {
                            addProperty("member", other.publicKey); addProperty("role", "admin")
                        }))
                        assertEquals("control", stream(original(member, "set-role") { addProperty("member", other.publicKey); addProperty("role", "admin") }))
                        creator.refresh(space); creator.leave(space); creator.flush(space); recipient.refresh(space)
                        assertFalse(creator.community(space).joined)
                        assertEquals(mapOf(member.publicKey to "OWNER", other.publicKey to "ADMIN"), recipient.members(space).associate { it.publicKey to it.role.name })
                    }
                }
            }
        }
    }
    @Test(timeout = 45_000) fun aProposalCannotSurviveTheRecipientsDepartureAndBecomeActiveAfterTheyRejoin() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-term-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-term-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-term-member.sqlite").absolutePath).use { memberStore ->
                    var head = ""
                    var highestPosition = -1L
                    var operation = 0
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                        val body = SaitoEnvelope.decode(receipt).body
                        if (body.get("stream")?.asString == "control" && body.get("position").asLong > highestPosition) {
                            highestPosition = body.get("position").asLong; head = original.id
                        }
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val recipient = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create(SpaceProfile("A fresh ownership choice", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    recipient.openPublic(space); recipient.joinPublic(space); recipient.flush(space); creator.refresh(space)
                    fun original(actor: SaitoWallet, kind: String, fields: JsonObject.() -> Unit = {}) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                        addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); fields()
                    }, clock.millis(), listOf(service.publicKey))
                    val proposal = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                    assertEquals("control", SaitoEnvelope.decode(deliver(proposal)).body.get("stream").asString)
                    recipient.refresh(space); recipient.leave(space); recipient.flush(space)
                    recipient.joinPublic(space); recipient.flush(space); creator.refresh(space)
                    val staleAcceptance = original(member, "accept-owner") { addProperty("proposal_id", proposal.id) }
                    val rejected = deliver(staleAcceptance)
                    assertEquals("A new membership cannot revive an old ownership offer", "rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                    val fresh = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                    assertEquals("control", SaitoEnvelope.decode(deliver(fresh)).body.get("stream").asString)
                    assertEquals("control", SaitoEnvelope.decode(deliver(original(member, "accept-owner") { addProperty("proposal_id", fresh.id) })).body.get("stream").asString)
                    assertEquals(rejected, deliver(staleAcceptance))
                    creator.refresh(space)
                    assertEquals(mapOf(owner.publicKey to "MEMBER", member.publicKey to "OWNER"), creator.members(space).associate { it.publicKey to it.role.name })
                }
            }
        }
    }


    @Test(timeout = 45_000) fun aConfirmedHandoverDeliversPrivateKeysAndExcludesTheFormerOwnersFutureAccess() = runBlocking<Unit> {
        privateHandoverWithAvailableHistory(loseAdmissionReply = false)
    }

    @Test(timeout = 45_000) fun aLostAdmissionReplyDuringHandoverStillDeliversAvailableEarlierPrivateHistory() = runBlocking<Unit> {
        privateHandoverWithAvailableHistory(loseAdmissionReply = true)
    }

    @Test(timeout = 45_000) fun cancellingConsentedPrivateHandoverRetiresItsTemporaryAccessBeforeNewWriting() = runBlocking<Unit> {
        privateHandoverWithAvailableHistory(loseAdmissionReply = false, cancelAfterAdmission = true)
    }

    private suspend fun privateHandoverWithAvailableHistory(loseAdmissionReply: Boolean, cancelAfterAdmission: Boolean = false) {

        SpacesServiceStore(files.newFolder("ownership-private-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-private-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-private-member.sqlite").absolutePath).use { memberStore ->
                    var head = ""
                    var highestPosition = -1L
                    var operation = 0
                    val received = mutableListOf<SaitoTransaction>()
                    val allOriginals = mutableListOf<SaitoTransaction>()
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                        allOriginals += original
                        val body = SaitoEnvelope.decode(receipt).body
                        if (body.get("stream")?.asString == "control" && body.get("position").asLong > highestPosition) {
                            highestPosition = body.get("position").asLong; head = original.id
                        }
                    }
                    var loseNextAdmission = loseAdmissionReply
                    val admissionAttempts = mutableListOf<String>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val reply = deliver(original)
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "admit") {
                            admissionAttempts.add(original.envelope())
                            if (loseNextAdmission) { loseNextAdmission = false; throw java.io.IOException("Owned admission was accepted but its reply was lost") }
                        }
                        reply
                    }
                    val recipient = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { original -> received.add(original); deliver(original) }
                    val space = creator.create(SpaceProfile("An encrypted handover", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    recipient.openPublic(space); recipient.joinPublic(space); recipient.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, nl.davidengelen.redsquare.spaces.SpaceChannelProfile("Private handover history"), emptySet()); creator.flush(space)
                    creator.send(space, "Private writing before handover", channel); creator.flush(space)
                    val copied = ownerStore.transaction {
                        val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                        nl.davidengelen.redsquare.spaces.SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                    }
                    fun original(actor: SaitoWallet, kind: String, fields: JsonObject.() -> Unit) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                        addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); fields()
                    }, clock.millis(), listOf(service.publicKey))
                    val proposal = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                    assertEquals("control", SaitoEnvelope.decode(deliver(proposal)).body.get("stream").asString)
                    recipient.refresh(space); recipient.flush(space); creator.admitPending(space); creator.flush(space); recipient.refresh(space)
                    assertFalse("An ownership offer alone must not disclose the private channel", recipient.channels(space).any { it.id == channel })
                    creator.refresh(space)
                    assertEquals("OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                    val beforeConsent = recipient.readScopeRevision
                    assertEquals("control", SaitoEnvelope.decode(deliver(original(member, "accept-owner") { addProperty("proposal_id", proposal.id) })).body.get("stream").asString)
                    // Drive the normal client workers through consent, actual
                    // key admission and the acknowledged completion of handover.
                    recipient.refresh(space)
                    assertEquals("Consent changes the revision before any key admission", beforeConsent + 1, recipient.readScopeRevision)
                    recipient.flush(space)
                    creator.refresh(space); creator.admitPending(space)
                    if (loseAdmissionReply) {
                        try { creator.flush(space); fail("The actual accepted admission reply is lost") } catch (_: java.io.IOException) { }
                    } else creator.flush(space)
                    recipient.refresh(space)
                    if (recipient.members(space).single { it.publicKey == member.publicKey }.role.name == "MEMBER") {
                        assertTrue("Consented key preparation makes the admitted private channel readable", recipient.channels(space).any { it.id == channel && it.ready })
                        assertFalse("Key preparation alone gives no private writing powers", recipient.community(space, channel).canSend)
                        try { recipient.send(space, "Writing before completed ownership", channel); fail("Preparing access is not permission to write") }
                        catch (_: IllegalArgumentException) { }
                        val preparingKeys = memberStore.transaction {
                            val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                            nl.davidengelen.redsquare.spaces.SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                        }
                        val writing = preparingKeys.publish(member, "message", "f".repeat(64), head, "A receiving boundary must reject this writing", clock.millis())
                        assertEquals("rejection", SaitoEnvelope.decode(deliver(writing)).body.get("stream").asString)
                    }
                    if (cancelAfterAdmission) {
                        recipient.refresh(space, channel)
                        val copiedRecipient = memberStore.transaction {
                            val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                            nl.davidengelen.redsquare.spaces.SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                        }
                        val consentId = recipient.ownershipOffer(space)!!.acceptedId!!
                        val beforeCancellation = recipient.readScopeRevision
                        assertEquals("control", SaitoEnvelope.decode(deliver(original(owner, "cancel-owner") { addProperty("proposal_id", proposal.id) })).body.get("stream").asString)
                        recipient.refresh(space)
                        assertEquals("Cancellation invalidates access before key rotation", beforeCancellation + 1, recipient.readScopeRevision)
                        assertFalse(channel in recipient.searchAvailableChannels(space, recipient.channels(space)))
                        creator.refresh(space); creator.flush(space); recipient.refresh(space)
                        assertNull(recipient.ownershipOffer(space))
                        assertEquals("OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                        assertFalse(recipient.channels(space).any { it.id == channel })
                        assertEquals("rejection", SaitoEnvelope.decode(deliver(original(member, "complete-owner") {
                            addProperty("proposal_id", proposal.id); addProperty("acceptance_id", consentId)
                        })).body.get("stream").asString)
                        creator.send(space, "New private writing after cancelled handover", channel); creator.flush(space)
                        val future = allOriginals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                        try { copiedRecipient.read(future.envelope()); fail("Temporary handover keys cannot read new writing after cancellation") }
                        catch (_: nl.davidengelen.redsquare.spaces.SpaceCrypto.MissingKeys) { }
                        assertEquals(listOf("Private writing before handover"), recipient.messages(space, channel))
                        return
                    }
                    recipient.flush(space)
                    if (loseAdmissionReply) {
                        // Reconcile the old holder only after the target has
                        // acknowledged completion and future keys have changed.
                        creator.flush(space)
                        assertEquals(2, admissionAttempts.size)
                        assertEquals(admissionAttempts[0], admissionAttempts[1])
                    }
                    recipient.refresh(space, channel)
                    assertEquals("OWNER", recipient.members(space).single { it.publicKey == member.publicKey }.role.name)
                    assertTrue("The new owner must be able to use the private channel after actual key delivery", recipient.channels(space).any { it.id == channel && it.ready })
                    assertEquals(listOf("Private writing before handover"), recipient.messages(space, channel))
                    recipient.send(space, "Private writing after handover", channel); recipient.flush(space)
                    val future = received.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                    try { copied.read(future.envelope()); fail("Former owner keys must not decrypt future restricted-channel writing") }
                    catch (_: nl.davidengelen.redsquare.spaces.SpaceCrypto.MissingKeys) { }
                    creator.refresh(space)
                    assertEquals("MEMBER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                    assertFalse(creator.community(space).canManage)
                    assertFalse(creator.channels(space).any { it.id == channel })
                    assertEquals(listOf("Private writing before handover"), creator.messages(space, channel))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anAdmittedWalletCannotCompletePrivateHandoverBeforeTheAcceptingDeviceHasKeys() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-ready-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-ready-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-ready-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("ownership-ready-other-device.sqlite").absolutePath).use { otherDeviceStore ->
                        var head = ""
                        var highestPosition = -1L
                        var operation = 0
                        fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                            val body = SaitoEnvelope.decode(receipt).body
                            if (body.get("stream")?.asString == "control" && body.get("position").asLong > highestPosition) {
                                highestPosition = body.get("position").asLong; head = original.id
                            }
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val recipient = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                        val otherDevice = SpaceClient(member, "fixture-node", service.publicKey, otherDeviceStore, clock) { deliver(it) }
                        val space = creator.create(SpaceProfile("Actual receiving-device readiness", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        recipient.openPublic(space); recipient.joinPublic(space); recipient.flush(space); creator.refresh(space)
                        val channel = creator.createPrivateChannel(space, nl.davidengelen.redsquare.spaces.SpaceChannelProfile("Known device only"), setOf(member.publicKey)); creator.flush(space)
                        recipient.refresh(space); recipient.flush(space); creator.admitPending(space); creator.flush(space); recipient.refresh(space, channel)
                        assertTrue(recipient.channels(space).single { it.id == channel }.ready)
                        otherDevice.openPublic(space)
                        assertFalse("Another device for the same wallet has not received this channel's keys", otherDevice.channels(space).any { it.id == channel && it.ready })
                        fun original(actor: SaitoWallet, kind: String, fields: JsonObject.() -> Unit) = SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                            addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); fields()
                        }, clock.millis(), listOf(service.publicKey))
                        val proposal = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                        assertEquals("control", SaitoEnvelope.decode(deliver(proposal)).body.get("stream").asString)
                        val consent = original(member, "accept-owner") { addProperty("proposal_id", proposal.id) }
                        assertEquals("control", SaitoEnvelope.decode(deliver(consent)).body.get("stream").asString)
                        creator.refresh(space)
                        assertEquals("A server-side credential is not confirmation that the accepting device has usable keys", "OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                        otherDevice.refresh(space); otherDevice.flush(space); creator.refresh(space)
                        assertEquals("OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                        recipient.refresh(space); recipient.flush(space); creator.refresh(space)
                        assertEquals("The actual admitted device can acknowledge readiness", "OWNER", creator.members(space).single { it.publicKey == member.publicKey }.role.name)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun eitherPartyCanEndAnUncompletedOfferWithoutRevivingItsOldAcceptance() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-cancel-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-cancel-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-cancel-member.sqlite").absolutePath).use { memberStore ->
                    var head = ""
                    var highestPosition = -1L
                    var operation = 0
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                        val body = SaitoEnvelope.decode(receipt).body
                        if (body.get("stream")?.asString == "control" && body.get("position").asLong > highestPosition) {
                            highestPosition = body.get("position").asLong; head = original.id
                        }
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val recipient = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create(SpaceProfile("A reversible invitation to ownership", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    recipient.openPublic(space); recipient.joinPublic(space); recipient.flush(space); creator.refresh(space)
                    fun original(actor: SaitoWallet, kind: String, fields: JsonObject.() -> Unit) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                        addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); fields()
                    }, clock.millis(), listOf(service.publicKey))
                    val proposal = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                    assertEquals("control", SaitoEnvelope.decode(deliver(proposal)).body.get("stream").asString)
                    val staleAcceptance = original(member, "accept-owner") { addProperty("proposal_id", proposal.id) }
                    val cancelled = original(owner, "cancel-owner") { addProperty("proposal_id", proposal.id) }
                    val cancellationReceipt = deliver(cancelled)
                    assertEquals("The current owner can cancel an outstanding offer", "control", SaitoEnvelope.decode(cancellationReceipt).body.get("stream").asString)
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(staleAcceptance)).body.get("stream").asString)
                    val replacement = original(owner, "propose-owner") { addProperty("member", member.publicKey) }
                    assertEquals("control", SaitoEnvelope.decode(deliver(replacement)).body.get("stream").asString)
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(original(owner, "decline-owner") { addProperty("proposal_id", replacement.id) })).body.get("stream").asString)
                    val decline = original(member, "decline-owner") { addProperty("proposal_id", replacement.id) }
                    val declineReceipt = deliver(decline)
                    assertEquals("control", SaitoEnvelope.decode(declineReceipt).body.get("stream").asString)
                    assertEquals(cancellationReceipt, deliver(cancelled))
                    assertEquals(declineReceipt, deliver(decline))
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(original(member, "accept-owner") { addProperty("proposal_id", replacement.id) })).body.get("stream").asString)
                    creator.refresh(space); recipient.refresh(space)
                    assertEquals("OWNER", creator.members(space).single { it.publicKey == owner.publicKey }.role.name)
                    assertEquals("MEMBER", recipient.members(space).single { it.publicKey == member.publicKey }.role.name)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun ownershipRequestsSurviveLostRepliesAndColdClientsWithoutInventingSuccess() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-api-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-api-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-api-member.sqlite").absolutePath).use { memberStore ->
                    var loseKind: String? = null
                    val attempts = mutableListOf<SaitoTransaction>()
                    suspend fun deliver(original: SaitoTransaction): String {
                        attempts += original
                        val reply = remote.exchange(original.envelope())
                        if (loseKind != null && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == loseKind) {
                            loseKind = null; throw java.io.IOException("Actual accepted ownership reply lost")
                        }
                        return reply
                    }
                    fun creator() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    fun recipient() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator().create(SpaceProfile("Durable ownership choices", visibility = SpaceVisibility.PUBLIC)); creator().flush(space)
                    recipient().openPublic(space); recipient().joinPublic(space); recipient().flush(space); creator().refresh(space)
                    loseKind = "propose-owner"
                    val proposal = creator().offerOwnership(space, member.publicKey)
                    assertNull("A local request is not an accepted offer", creator().ownershipOffer(space))
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator().ownershipChange(space)?.status)
                    try { creator().flush(space); fail("The offer response was lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator().ownershipChange(space)?.status)
                    creator().flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator().ownershipChange(space)?.status)
                    assertEquals(proposal, creator().ownershipOffer(space)?.id)
                    recipient().refresh(space)
                    assertEquals(member.publicKey, recipient().ownershipOffer(space)?.member)
                    loseKind = "accept-owner"
                    val acceptance = recipient().acceptOwnership(space, proposal)
                    assertEquals("MEMBER", recipient().members(space).single { it.publicKey == member.publicKey }.role.name)
                    try { recipient().flush(space); fail("The acceptance response was lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, recipient().ownershipChange(space)?.status)
                    recipient().flush(space); creator().refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, recipient().ownershipChange(space)?.status)
                    assertNull(recipient().ownershipOffer(space))
                    assertEquals("OWNER", recipient().members(space).single { it.publicKey == member.publicKey }.role.name)
                    assertFalse(creator().community(space).canManage)
                    for (id in listOf(proposal, acceptance)) {
                        val exact = attempts.filter { it.id == id }
                        assertEquals(2, exact.size); assertEquals(exact[0].envelope(), exact[1].envelope())
                    }
                    // The new owner's cancel and the chosen member's decline
                    // use the same durable application surface.
                    val cancelled = recipient().offerOwnership(space, owner.publicKey); recipient().flush(space)
                    recipient().cancelOwnership(space, cancelled); recipient().flush(space); creator().refresh(space)
                    assertNull(creator().ownershipOffer(space))
                    val declined = recipient().offerOwnership(space, owner.publicKey); recipient().flush(space); creator().refresh(space)
                    creator().declineOwnership(space, declined); creator().flush(space); recipient().refresh(space)
                    assertNull(recipient().ownershipOffer(space))
                    assertEquals("OWNER", recipient().members(space).single { it.publicKey == member.publicKey }.role.name)
                }
            }
        }
    }

}

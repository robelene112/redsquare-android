package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
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
import java.sql.DriverManager

class SpaceDiscoveryTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val visitor = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test fun aPublicVisitorReadsWithoutJoiningAndContributesOnlyAfterAnExplicitDurableJoin() = runBlocking {
        SpacesServiceStore(files.newFolder("public-read-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("public-read-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("public-read-visitor.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var loseJoinResponse = true
                    var originalJoin: String? = null
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { request ->
                        val original = SaitoEnvelope.decode(request.envelope())
                        val result = remote.exchange(request.envelope())
                        if (original.body.get("kind")?.asString == "join-public") {
                            if (originalJoin == null) originalJoin = request.envelope() else assertEquals(originalJoin, request.envelope())
                            if (loseJoinResponse) { loseJoinResponse = false; throw java.io.IOException("Accepted join response was lost") }
                        }
                        result
                    }
                    val space = creator.create(SpaceProfile("Open reading room", "Read before joining", SpaceVisibility.PUBLIC))
                    creator.flush(space)
                    creator.send(space, "A genuinely public conversation")
                    creator.flush(space)
                    assertFalse(reader.openPublic(space).joined)
                    reader.refresh(space)
                    assertEquals(listOf("A genuinely public conversation"), reader.messages(space))
                    assertTrue("Viewing is not joining", reader.communities().isEmpty())
                    reader.saveDraft(space, "My independent draft")
                    try { reader.send(space, "Not a member yet"); fail("Viewing cannot authorize publication") }
                    catch (_: IllegalArgumentException) { }
                    reader.joinPublic(space)
                    assertFalse(reader.community(space).joined)
                    try { reader.flush(space); fail("The controlled original join callback must be lost") }
                    catch (_: java.io.IOException) { }
                    assertFalse(reader.community(space).joined)
                    assertEquals("My independent draft", reader.draft(space))
                    val restored = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { request ->
                        if (SaitoEnvelope.decode(request.envelope()).body.get("kind")?.asString == "join-public")
                            assertEquals(originalJoin, request.envelope())
                        remote.exchange(request.envelope())
                    }
                    restored.flush(space)
                    assertTrue(restored.community(space).joined)
                    assertTrue(restored.community(space).canSend)
                    assertFalse("Direct public join grants no administrator role", restored.community(space).canInvite)
                    restored.send(space, "An explicitly joined member can answer")
                    restored.flush(space)
                    creator.refresh(space)
                    assertEquals(listOf("A genuinely public conversation", "An explicitly joined member can answer"), creator.messages(space))
                    assertEquals(listOf(space), restored.communities().map { it.id })
                    val privateSpace = creator.create("Private room stays private")
                    creator.flush(privateSpace)
                    try { restored.openPublic(privateSpace); fail("Public browsing cannot open private history") }
                    catch (_: IllegalArgumentException) { }
                    assertEquals(listOf(space), restored.communities().map { it.id })
                    assertEquals("My independent draft", restored.draft(space))
                }
            }
        }
    }

    @Test fun anOlderPublicCommunityNeverTurnsItsUnmarkedPrivateChannelIntoPublicPlaintext() = runBlocking {
        SpacesServiceStore(files.newFolder("legacy-public-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("legacy-public-visitor.sqlite").absolutePath).use { visitorStore ->
                val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
                val crypto = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
                val metadata = crypto.publish(owner, "metadata", "d".repeat(64), "0".repeat(128), "Still private", clock.millis())
                val genesis = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", scope.spaceId); addProperty("kind", "create"); addProperty("operation_id", "e".repeat(64))
                    addProperty("visibility", "public"); add("public_profile", SpaceProfile("Legacy public shell", visibility = SpaceVisibility.PUBLIC).json())
                    // Older roots have no channel_visibility. Public discovery
                    // must not imply disclosure of their encrypted conversation.
                    addProperty("channel_id", scope.channelId); addProperty("epoch", 0)
                    addProperty("metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray()))
                    add("devices", JsonObject().apply { crypto.bindings.forEach { (credential, signer) -> addProperty(credential, signer) } })
                }, clock.millis(), listOf(service.publicKey))
                assertEquals("control", SaitoEnvelope.decode(remote.exchange(genesis.envelope())).body.get("stream").asString)
                val encrypted = crypto.publish(owner, "message", "1".repeat(64), genesis.id, "A private historical message", clock.millis())
                assertEquals("content", SaitoEnvelope.decode(remote.exchange(encrypted.envelope())).body.get("stream").asString)
                val plaintext = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", scope.spaceId); addProperty("channel_id", scope.channelId)
                    addProperty("operation_id", "2".repeat(64)); addProperty("kind", "public-content")
                    addProperty("control_id", genesis.id); addProperty("epoch", 0); addProperty("key_generation", scope.generation)
                    addProperty("content_kind", "message"); addProperty("text", "Do not accept a plaintext fallback")
                }, clock.millis(), listOf(service.publicKey))
                assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(plaintext.envelope())).body.get("stream").asString)
                val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                assertEquals(listOf(scope.spaceId), reader.discover().items.map { it.id })
                try { reader.openPublic(scope.spaceId); fail("A public profile is not public channel authority") }
                catch (_: IllegalArgumentException) { }
                assertTrue(reader.communities().isEmpty())
            }
        }
    }

    @Test fun aPrivateInvitationShowsItsRealProfileAndRequiresDeliberateAcceptanceOfItsHouseRules() = runBlocking {
        SpacesServiceStore(files.newFolder("private-rules-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-rules-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-rules-visitor.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val profile = SpaceProfile("A considerate community", "Private description visible to the bearer", houseRules = "Be kind. Do not share private discussions.")
                    val space = creator.create(profile)
                    creator.flush(space)
                    val link = creator.invite(space)
                    creator.flush(space)
                    val preview = reader.preview(link)
                    assertEquals(profile, preview.profile)
                    assertEquals(clock.millis() + 604_800_000, preview.expiresAt)
                    assertTrue(reader.communities().isEmpty())
                    try { reader.join(link); fail("House rules require deliberate acceptance before preparing membership") }
                    catch (_: IllegalArgumentException) { }
                    assertTrue(reader.communities().isEmpty())
                    assertEquals(space, reader.join(link, acceptedRulesToken = preview.rulesToken))
                    reader.flush(space)
                    creator.admitPending(space)
                    creator.flush(space)
                    reader.refresh(space)
                    assertTrue(reader.community(space).joined)
                    reader.send(space, "I accepted the rules and can contribute")
                    reader.flush(space)
                    creator.refresh(space)
                    assertEquals(listOf("I accepted the rules and can contribute"), creator.messages(space))
                    SqliteClientFixture(files.newFile("private-rules-omitted-token.sqlite").absolutePath).use { otherStore ->
                        val otherWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        val omitting = SpaceClient(otherWallet, "fixture-node", service.publicKey, otherStore, clock) { request ->
                            val original = SaitoEnvelope.decode(request.envelope())
                            if (original.body.get("kind")?.asString == "join") {
                                val body = original.body.deepCopy().apply { remove("rules_token") }
                                val omitted = SaitoTransaction.create(otherWallet, body, clock.millis(), listOf(service.publicKey))
                                assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(omitted.envelope())).body.get("stream").asString)
                                throw java.io.IOException("Controlled sender omitted its required acknowledgment")
                            }
                            remote.exchange(request.envelope())
                        }
                        val review = omitting.preview(link)
                        omitting.join(link, acceptedRulesToken = review.rulesToken)
                        try { omitting.flush(space); fail("The controlled sender must stop after the receiver rejects its omission") }
                        catch (_: java.io.IOException) { }
                        assertFalse(omitting.community(space).joined)
                        assertFalse(omitting.community(space).canSend)
                    }
                }
            }
        }
    }

    @Test fun publicHouseRulesAreRequiredByTheReceiverAndApplyToDirectAndInvitedJoining() = runBlocking {
        SpacesServiceStore(files.newFolder("public-rules-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("public-rules-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("public-rules-visitor.sqlite").absolutePath).use { visitorStore ->
                    SqliteClientFixture(files.newFile("public-rules-invited.sqlite").absolutePath).use { invitedStore ->
                        var head = ""
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                            remote.exchange(request.envelope()).also { if (SaitoEnvelope.decode(request.envelope()).body.get("kind")?.asString == "create") head = request.id }
                        }
                        val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                        val invitedWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        val invited = SpaceClient(invitedWallet, "fixture-node", service.publicKey, invitedStore, clock) { remote.exchange(it.envelope()) }
                        val profile = SpaceProfile("Public rules", "An open conversation with shared expectations", SpaceVisibility.PUBLIC, houseRules = "Keep conversations constructive.")
                        val space = creator.create(profile)
                        creator.flush(space)
                        assertEquals(profile, reader.discover().items.single().profile)
                        val opened = reader.openPublic(space)
                        try { reader.joinPublic(space); fail("Reading does not accept house rules") }
                        catch (_: IllegalArgumentException) { }
                        assertTrue(reader.communities().isEmpty())
                        val missingAcceptance = SaitoTransaction.create(visitor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("channel_id", opened.channel)
                            addProperty("kind", "join-public"); addProperty("operation_id", "f".repeat(64)); addProperty("control_id", head)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(missingAcceptance.envelope())).body.get("stream").asString)
                        try { reader.joinPublic(space, acceptedRulesToken = profile.copy(houseRules = "Earlier expectations").publicRulesToken)
                            fail("A previous review cannot accept different current rules") }
                        catch (_: IllegalArgumentException) { }
                        reader.joinPublic(space, acceptedRulesToken = profile.publicRulesToken)
                        reader.flush(space)
                        assertTrue(reader.community(space).joined)
                        // No owner admission is called for either public path.
                        creator.refresh(space)
                        val link = creator.invite(space)
                        creator.flush(space)
                        val invitedPreview = invited.preview(link)
                        assertEquals(profile, invitedPreview.profile)
                        try { invited.join(link); fail("An invitation does not accept rules for its bearer") }
                        catch (_: IllegalArgumentException) { }
                        assertTrue(invited.communities().isEmpty())
                        invited.join(link, acceptedRulesToken = invitedPreview.rulesToken)
                        invited.flush(space)
                        assertTrue(invited.community(space).joined)
                        invited.send(space, "Public invitation joins immediately after acceptance")
                        invited.flush(space)
                        reader.refresh(space)
                        assertEquals(listOf("Public invitation joins immediately after acceptance"), reader.messages(space))
                        assertFalse(invited.community(space).canInvite)
                    }
                }
            }
        }
    }

    @Test fun privateRulesUseInvitationSpecificConsentWithoutPublishingATextFingerprint() = runBlocking {
        SpacesServiceStore(files.newFolder("private-rules-metadata").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-rules-metadata-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-rules-metadata-reader.sqlite").absolutePath).use { visitorStore ->
                    val controls = mutableListOf<com.google.gson.JsonObject>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val original = SaitoEnvelope.decode(request.envelope())
                        if (original.body.get("request").asString == "event") controls += original.body
                        remote.exchange(request.envelope())
                    }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val profile = SpaceProfile("Private expectations", houseRules = "A private set of house rules")
                    val space = creator.create(profile)
                    creator.flush(space)
                    val first = creator.invite(space)
                    creator.flush(space)
                    val second = creator.invite(space)
                    creator.flush(space)
                    val fingerprint = java.security.MessageDigest.getInstance("SHA-256").digest(profile.houseRules.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    for (control in controls) {
                        assertFalse("Private text stays inside ciphertext", control.toString().contains(profile.houseRules))
                        assertFalse("A plaintext-derived fingerprint also discloses private rules", control.toString().contains(fingerprint))
                    }
                    val firstPreview = reader.preview(first)
                    val secondPreview = reader.preview(second)
                    assertEquals(profile, firstPreview.profile)
                    assertEquals(profile, secondPreview.profile)
                    assertTrue(firstPreview.rulesToken.isNotBlank())
                    assertNotEquals(firstPreview.rulesToken, secondPreview.rulesToken)
                    try { reader.join(second, acceptedRulesToken = firstPreview.rulesToken)
                        fail("Acceptance remains specific to the reviewed invitation") }
                    catch (_: IllegalArgumentException) { }
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun acceptingAnInvitationPreviewCannotAcknowledgeDifferentCanonicalPublicRules() = runBlocking {
        SpacesServiceStore(files.newFolder("public-rules-mismatch").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("public-rules-mismatch-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("public-rules-mismatch-reader.sqlite").absolutePath).use { visitorStore ->
                    var originalInvitation = ""
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        if (SaitoEnvelope.decode(request.envelope()).body.get("kind")?.asString == "invite") originalInvitation = request.envelope()
                        remote.exchange(request.envelope())
                    }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val canonical = SpaceProfile("One community", visibility = SpaceVisibility.PUBLIC, houseRules = "Canonical public rules A")
                    val different = canonical.copy(houseRules = "Different invitation rules B")
                    val space = creator.create(canonical)
                    creator.flush(space)
                    val link = creator.invite(space)
                    creator.flush(space)
                    // An authentic owner command, with a genuinely decryptable
                    // preview B, must never consent to canonical rules A.
                    val fragment = java.util.Base64.getUrlDecoder().decode(java.net.URI(link).rawFragment)
                    val linkData = com.google.gson.JsonParser.parseString(fragment.toString(Charsets.UTF_8)).asJsonObject
                    val scopeData = linkData.getAsJsonObject("scope")
                    val scope = SpaceCrypto.Scope(scopeData.get("service").asString, scopeData.get("space").asString,
                        scopeData.get("channel").asString, scopeData.get("generation").asString)
                    val operation = "a".repeat(64)
                    val nonce = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
                    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(
                            java.util.Base64.getDecoder().decode(linkData.get("preview_key").asString), "AES"),
                            javax.crypto.spec.GCMParameterSpec(128, nonce))
                        updateAAD("SaitoSpacesPreview/v1:${scope.groupId}:$operation".toByteArray(Charsets.UTF_8))
                    }
                    val preview = java.util.Base64.getEncoder().encodeToString(nonce + cipher.doFinal(different.json().toString().toByteArray(Charsets.UTF_8)))
                    val actual = SaitoEnvelope.decode(originalInvitation)
                    val body = actual.body.deepCopy().apply {
                        addProperty("operation_id", operation); addProperty("control_id", actual.id)
                        addProperty("rules_token", different.publicRulesToken); addProperty("preview", preview)
                    }
                    val mismatched = SaitoTransaction.create(owner, body, clock.millis(), listOf(service.publicKey))
                    val response = SaitoEnvelope.decode(remote.exchange(mismatched.envelope()))
                    if (response.body.get("stream").asString == "control") {
                        linkData.addProperty("invitation", mismatched.id)
                        val mismatchedLink = "saito://spaces/join#" + java.util.Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(linkData.toString().toByteArray(Charsets.UTF_8))
                        val shown = reader.preview(mismatchedLink)
                        assertEquals(different, shown.profile)
                        try { reader.join(mismatchedLink, acceptedRulesToken = shown.rulesToken); fail("Accepting displayed B cannot acknowledge canonical A") }
                        catch (_: IllegalArgumentException) { }
                    } else assertEquals("rejection", response.body.get("stream").asString)
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun revokingAnInvitationStopsPendingAndFutureAdmissionsWithoutRemovingExistingMembersOrDrafts() = runBlocking {
        SpacesServiceStore(files.newFolder("revoke-invitation").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("revoke-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("revoke-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("revoke-pending.sqlite").absolutePath).use { pendingStore ->
                        var currentControl = ""
                        var invitationId = ""
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                            val result = remote.exchange(request.envelope())
                            if (SaitoEnvelope.decode(result).body.get("stream")?.asString == "control") {
                                val original = SaitoEnvelope.decode(request.envelope())
                                currentControl = original.id
                                if (original.body.get("kind").asString == "invite") invitationId = original.id
                            }
                            result
                        }
                        val member = SpaceClient(visitor, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val pendingWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        val applicant = SpaceClient(pendingWallet, "fixture-node", service.publicKey, pendingStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create("Revocable invitation")
                        creator.flush(space)
                        val oldLink = creator.invite(space)
                        creator.flush(space)
                        member.join(oldLink)
                        member.flush(space)
                        creator.admitPending(space)
                        creator.flush(space)
                        member.refresh(space)
                        assertTrue(member.community(space).joined)
                        applicant.join(oldLink)
                        applicant.saveDraft(space, "Preserve my writing while I wait")
                        applicant.flush(space)
                        assertFalse(applicant.community(space).joined)
                        val memberAttempt = SaitoTransaction.create(visitor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("operation_id", "e".repeat(64))
                            addProperty("kind", "revoke-invite"); addProperty("control_id", currentControl)
                            addProperty("invite_id", invitationId)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(memberAttempt.envelope())).body.get("stream").asString)
                        creator.revokeInvitation(space)
                        creator.flush(space)
                        assertTrue(requireNotNull(creator.invitation(space)).revoked)
                        try { applicant.preview(oldLink); fail("A revoked bearer link cannot be previewed") }
                        catch (_: IllegalArgumentException) { }
                        creator.admitPending(space)
                        creator.flush(space)
                        try { applicant.refresh(space); fail("A revoked pending request cannot become a member") }
                        catch (_: IllegalArgumentException) { }
                        assertFalse(applicant.community(space).canSend)
                        assertEquals("Preserve my writing while I wait", applicant.draft(space))
                        member.send(space, "An existing member remains admitted")
                        member.flush(space)
                        creator.refresh(space)
                        assertEquals(listOf("An existing member remains admitted"), creator.messages(space))
                        val replacement = creator.invite(space)
                        creator.flush(space)
                        assertFalse(requireNotNull(creator.invitation(space)).revoked)
                        assertNotEquals(oldLink, replacement)
                        assertEquals(space, applicant.preview(replacement).spaceId)
                        applicant.join(replacement)
                        applicant.flush(space)
                        creator.admitPending(space)
                        creator.flush(space)
                        applicant.refresh(space)
                        assertTrue(applicant.community(space).joined)
                        assertEquals("Preserve my writing while I wait", applicant.draft(space))
                        applicant.send(space, "A deliberate new invitation authorizes me")
                        applicant.flush(space)
                        member.refresh(space)
                        assertEquals(listOf("An existing member remains admitted", "A deliberate new invitation authorizes me"), member.messages(space))
                    }
                }
            }
        }
    }

    @Test fun authenticatedReadResultsDistinguishExpiredInvitationsFromUnavailablePrivateAccess() = runBlocking {
        var now = clock.millis()
        val advancing = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(now)
        }
        SpacesServiceStore(files.newFolder("read-dispositions").toPath(), service, advancing).use { remote ->
            SqliteClientFixture(files.newFile("read-disposition-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("read-disposition-reader.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, advancing) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, advancing) { remote.exchange(it.envelope()) }
                    val space = creator.create("Unavailable is not offline")
                    creator.flush(space)
                    val invitation = creator.invite(space, expiresAfterMillis = 60_000)
                    creator.flush(space)
                    now += 60_001
                    try { reader.preview(invitation); fail("A genuinely expired invitation must be reported") }
                    catch (denied: SpaceClient.ReadDenied) {
                        assertEquals(space, denied.spaceId)
                        assertEquals(SpaceClient.ReadProblem.EXPIRED, denied.problem)
                    }
                    try { reader.openPublic(space); fail("Private access remains unavailable to visitors") }
                    catch (denied: SpaceClient.ReadDenied) {
                        assertEquals(space, denied.spaceId)
                        assertEquals(SpaceClient.ReadProblem.UNAVAILABLE, denied.problem)
                    }
                    creator.revokeInvitation(space)
                    creator.flush(space)
                    try { reader.preview(invitation); fail("A revoked invitation is unavailable") }
                    catch (denied: SpaceClient.ReadDenied) { assertEquals(SpaceClient.ReadProblem.UNAVAILABLE, denied.problem) }
                    assertTrue(reader.communities().isEmpty())
                    val replacement = creator.invite(space)
                    creator.flush(space)
                    assertEquals(space, reader.preview(replacement).spaceId)
                }
            }
        }
    }

    @Test fun anAdmittedGuestRecoversItsWelcomeAndExactPendingOriginalAfterItsInvitationWasRevoked() = runBlocking {
        SpacesServiceStore(files.newFolder("revoked-admission-recovery").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("revoked-admission-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("revoked-admission-guest.sqlite").absolutePath).use { guestStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var originalJoin: String? = null
                    var loseAcknowledgment = true
                    val guest = SpaceClient(visitor, "fixture-node", service.publicKey, guestStore, clock) { request ->
                        val result = remote.exchange(request.envelope())
                        if (SaitoEnvelope.decode(request.envelope()).body.get("kind")?.asString == "join") {
                            if (originalJoin == null) originalJoin = request.envelope() else assertEquals(originalJoin, request.envelope())
                            if (loseAcknowledgment) { loseAcknowledgment = false; throw java.io.IOException("Actual join acknowledgment was lost") }
                        }
                        result
                    }
                    val space = creator.create("Membership survives invitation revocation")
                    creator.flush(space)
                    val invitation = creator.invite(space)
                    creator.flush(space)
                    guest.join(invitation)
                    guest.saveDraft(space, "My writing survives membership recovery")
                    try { guest.flush(space); fail("The controlled accepted join response must be lost") }
                    catch (_: java.io.IOException) { }
                    creator.admitPending(space)
                    creator.flush(space)
                    assertFalse("The guest has not received its Welcome", guest.community(space).joined)
                    creator.revokeInvitation(space)
                    creator.flush(space)
                    creator.send(space, "Only actual admitted members can read this")
                    creator.flush(space)
                    guest.refresh(space)
                    assertTrue(guest.community(space).joined)
                    assertEquals(listOf("Only actual admitted members can read this"), guest.messages(space))
                    assertEquals("My writing survives membership recovery", guest.draft(space))
                    guest.flush(space)
                    assertFalse("The exact original is reconciled after membership recovery", guest.community(space).pending)
                    guest.send(space, "My admitted identity can still answer")
                    guest.flush(space)
                    creator.refresh(space)
                    assertEquals(listOf("Only actual admitted members can read this", "My admitted identity can still answer"), creator.messages(space))
                }
            }
        }
    }

    @Test fun realCommunityImagesSurviveBoundedPublicDiscoveryAndEncryptedPrivateInvitationPreviews() = runBlocking {
        val raster = java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB)
        for (x in 0..3) for (y in 0..3) raster.setRGB(x, y, if ((x + y) % 2 == 0) 0xa35b43 else 0x272b34)
        val encoded = java.io.ByteArrayOutputStream().also { check(javax.imageio.ImageIO.write(raster, "jpeg", it)) }.toByteArray()
        // A real decodable JPEG at the declared avatar byte boundary. The
        // standards-defined comment segment changes no image pixels.
        val padding = 16384 - encoded.size - 4
        assertTrue(padding > 0)
        val jpeg = java.io.ByteArrayOutputStream().apply {
            write(encoded, 0, 2); write(0xff); write(0xfe)
            write((padding + 2) ushr 8); write((padding + 2) and 255)
            write(ByteArray(padding)); write(encoded, 2, encoded.size - 2)
        }.toByteArray()
        assertEquals(16384, jpeg.size)
        assertNotNull(javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(jpeg)))
        val image = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg)
        val oversized = java.io.ByteArrayOutputStream().apply {
            write(jpeg, 0, 2); write(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, 3, 0))
            write(jpeg, 2, jpeg.size - 2)
        }.toByteArray()
        assertNotNull(javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(oversized)))
        assertThrows(IllegalArgumentException::class.java) {
            SpaceProfile("An oversized real image", image = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(oversized))
        }
        assertThrows(IllegalArgumentException::class.java) { SpaceProfile("No implicit remote image request", image = "https://example.invalid/avatar.jpg") }
        SpacesServiceStore(files.newFolder("profile-images").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("profile-image-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("profile-image-reader.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val privateProfile = SpaceProfile("Actual community image", "d".repeat(4096), houseRules = "r".repeat(4096), image = image)
                    val publicProfile = privateProfile.copy(visibility = SpaceVisibility.PUBLIC, listed = true)
                    val expected = (0..16).map { index ->
                        creator.create(publicProfile.copy(name = "Actual public image $index")).also { creator.flush(it) }
                    }
                    val discovered = mutableListOf<String>()
                    var cursor = 0L
                    do {
                        val result = reader.discover(cursor = cursor)
                        result.items.forEach { assertTrue("Actual community image survives discovery", it.profile.image == image) }
                        if (cursor == 0L) assertTrue("Large real profiles still respect the response byte bound", result.hasMore)
                        discovered += result.items.map { it.id }
                        cursor = result.nextCursor
                    } while (result.hasMore)
                    assertEquals(expected, discovered)
                    assertEquals(17, reader.cachedDirectory().size)
                    assertTrue(reader.cachedDirectory().all { it.profile.image == image })
                    val privateSpace = creator.create(privateProfile)
                    creator.flush(privateSpace)
                    val link = creator.invite(privateSpace)
                    creator.flush(privateSpace)
                    assertTrue("The private profile image is available to its bearer", reader.preview(link).profile == privateProfile)
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun publicChannelsKeepDraftsContentAndLateAcknowledgmentsIsolatedAcrossSelectionAndReload() = runBlocking {
        SpacesServiceStore(files.newFolder("public-channel-isolation").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("public-channel-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("public-channel-visitor.sqlite").absolutePath).use { visitorStore ->
                    var secondary = ""
                    var loseSecondaryAcknowledgment = true
                    var heldOriginal: String? = null
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val result = remote.exchange(request.envelope())
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (body.get("kind")?.asString == "public-content" && body.get("channel_id")?.asString == secondary) {
                            if (heldOriginal == null) heldOriginal = request.envelope() else assertEquals(heldOriginal, request.envelope())
                            if (loseSecondaryAcknowledgment) { loseSecondaryAcknowledgment = false; throw java.io.IOException("Secondary channel acknowledgment was lost") }
                        }
                        result
                    }
                    val privateSpace = creator.create("Keep independent private work")
                    creator.flush(privateSpace)
                    creator.send(privateSpace, "A private message stays private")
                    creator.flush(privateSpace)
                    creator.saveDraft(privateSpace, "A private draft remains independent")
                    val space = creator.create(SpaceProfile("Two public conversations", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space)
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "General writing")
                    creator.send(space, "General conversation")
                    creator.flush(space)
                    secondary = creator.createChannel(space, SpaceChannelProfile("workshop"))
                    creator.flush(space)
                    assertEquals(listOf("general", "workshop"), creator.channels(space).map { it.profile.name })
                    creator.selectChannel(space, secondary)
                    assertEquals(secondary, creator.community(space).channel)
                    assertEquals("", creator.draft(space))
                    creator.saveDraft(space, "Workshop writing")
                    creator.send(space, "Workshop conversation")
                    try { creator.flush(space); fail("The controlled second-channel acknowledgment must be lost") }
                    catch (_: java.io.IOException) { }
                    creator.selectChannel(space, general)
                    assertEquals("General writing", creator.draft(space))
                    assertEquals(listOf("General conversation"), creator.messages(space))
                    assertFalse(creator.timeline(space).any { it.text == "Workshop conversation" })
                    creator.flush(space)
                    assertEquals(general, creator.community(space).channel)
                    assertEquals(listOf("General conversation"), creator.messages(space))
                    creator.selectChannel(space, secondary)
                    assertEquals(listOf("Workshop conversation"), creator.messages(space))
                    val restored = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    assertEquals(secondary, restored.community(space).channel)
                    assertEquals("Workshop writing", restored.draft(space))
                    assertEquals(listOf("Workshop conversation"), restored.messages(space))
                    assertEquals(listOf("A private message stays private"), restored.messages(privateSpace))
                    assertEquals("A private draft remains independent", restored.draft(privateSpace))
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    reader.openPublic(space)
                    assertEquals(listOf("general", "workshop"), reader.channels(space).map { it.profile.name })
                    reader.refresh(space)
                    assertEquals(listOf("General conversation"), reader.messages(space))
                    reader.selectChannel(space, secondary)
                    reader.refresh(space)
                    assertEquals(listOf("Workshop conversation"), reader.messages(space))
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun anotherChannelsLargePendingQueueCannotHideTheSelectedChannelsOwnQueuedMessage() = runBlocking {
        SpacesServiceStore(files.newFolder("channel-outbox-isolation").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-outbox-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Independent queued conversations", visibility = SpaceVisibility.PUBLIC))
                creator.flush(space)
                val general = creator.community(space).channel
                val workshop = creator.createChannel(space, SpaceChannelProfile("workshop"))
                creator.flush(space)
                repeat(51) { creator.send(space, "General queued message $it") }
                creator.selectChannel(space, workshop)
                val queued = creator.send(space, "The selected channel must show its own work immediately")
                val visible = creator.timeline(space)
                assertEquals(listOf(queued), visible.map { it.id })
                assertTrue(visible.single().pending)
                assertFalse(visible.single().attempted)
                creator.selectChannel(space, general)
                assertEquals(50, creator.timeline(space).size)
                assertTrue(creator.timeline(space).all { it.pending && it.text?.startsWith("General queued message") == true })
                creator.selectChannel(space, workshop)
                val restored = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                assertEquals(listOf(queued), restored.timeline(space).map { it.id })
            }
        }
    }

    @Test fun announcementChannelsArePubliclyReadableButOnlyOwnersAndAdministratorsCanPublish() = runBlocking {
        SpacesServiceStore(files.newFolder("announcement-permissions").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("announcement-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("announcement-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    var memberHead = ""
                    val member = SpaceClient(visitor, "fixture-node", service.publicKey, memberStore, clock) { request ->
                        remote.exchange(request.envelope()).also { result ->
                            if (SaitoEnvelope.decode(result).body.get("stream")?.asString == "control") memberHead = request.id
                        }
                    }
                    val space = creator.create(SpaceProfile("Announcements", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space)
                    val announcements = creator.createChannel(space, SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT))
                    creator.flush(space)
                    member.openPublic(space)
                    member.joinPublic(space)
                    member.flush(space)
                    member.selectChannel(space, announcements)
                    val invalid = SaitoTransaction.create(visitor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("channel_id", announcements)
                        addProperty("operation_id", "f".repeat(64)); addProperty("control_id", memberHead)
                        addProperty("epoch", 0); addProperty("key_generation", "0".repeat(64))
                        addProperty("kind", "public-content"); addProperty("content_kind", "message")
                        addProperty("text", "A member must not publish this announcement")
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(invalid.envelope())).body.get("stream").asString)
                    assertFalse(member.community(space).canSend)
                    try { member.send(space, "Do not queue an unauthorized announcement"); fail("The local workflow also respects announcement authority") }
                    catch (_: IllegalArgumentException) { }
                    creator.refresh(space)
                    creator.selectChannel(space, announcements)
                    assertTrue(creator.community(space).canSend)
                    creator.send(space, "A real owner announcement")
                    creator.flush(space)
                    member.refresh(space)
                    assertEquals(listOf("A real owner announcement"), member.messages(space))
                }
            }
        }
    }

    @Test fun rebuildingMessagePointersFromTheEarlierClientLayoutPreservesDraftsAndEverySignedOriginal() = runBlocking {
        SpacesServiceStore(files.newFolder("legacy-message-pointers").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("legacy-message-pointer-owner.sqlite").absolutePath).use { ownerStore ->
                val received = mutableListOf<String>()
                fun client() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                    val result = remote.exchange(request.envelope())
                    if (SaitoEnvelope.decode(request.envelope()).body.get("kind")?.asString == "public-content") received += request.id
                    result
                }
                val creator = client()
                val space = creator.create(SpaceProfile("Keep older pending work", visibility = SpaceVisibility.PUBLIC))
                creator.flush(space)
                creator.saveDraft(space, "Independent text must survive index rebuilding")
                val originals = (0..51).map { creator.send(space, "Existing queued message $it") }
                val channel = creator.community(space).channel
                // Independently establish the earlier record layout around
                // genuine signed outbox rows. This is not an old-binary run.
                ownerStore.transaction {
                    val prefix = "$space/channels/$channel/pending/"
                    while (true) {
                        val rows = page(prefix)
                        rows.forEach { remove(it.key) }
                        if (rows.isEmpty()) break
                    }
                    val key = "$space/state"
                    val state = com.google.gson.JsonParser.parseString(requireNotNull(get(key)).toString(Charsets.UTF_8)).asJsonObject
                    state.remove("message_index_version")
                    put(key, state.toString().toByteArray(Charsets.UTF_8))
                }
                val restored = client()
                assertEquals(originals.take(50), restored.timeline(space).map { it.id })
                assertTrue(restored.timeline(space).all { it.pending && !it.attempted })
                assertEquals("Independent text must survive index rebuilding", restored.draft(space))
                restored.flush(space)
                restored.flush(space)
                assertEquals(originals, received)
                assertFalse(restored.community(space).pending)
                assertTrue(restored.timeline(space).none { it.pending })
                assertEquals((2..51).map { "Existing queued message $it" }, restored.messages(space))
                assertEquals("Independent text must survive index rebuilding", restored.draft(space))
            }
        }
    }

    @Test fun aFreshChannelPageCannotAdvanceItsCursorWithAnotherChannelsAuthenticReceipt() = verifyChannelPageBinding(relabel = false)

    @Test fun aFreshChannelPageCannotRelabelAnAuthenticOriginalIntoAnotherChannel() = verifyChannelPageBinding(relabel = true)

    private fun verifyChannelPageBinding(relabel: Boolean) = runBlocking {
        val suffix = if (relabel) "original" else "request"
        SpacesServiceStore(files.newFolder("channel-page-$suffix").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-page-owner-$suffix.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-page-reader-$suffix.sqlite").absolutePath).use { visitorStore ->
                    var general = ""
                    var generalReceipt = ""
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        remote.exchange(request.envelope()).also { result ->
                            val body = SaitoEnvelope.decode(request.envelope()).body
                            if (body.get("kind")?.asString == "public-content" && body.get("channel_id")?.asString == general) generalReceipt = result
                        }
                    }
                    val space = creator.create(SpaceProfile("Bound channel pages", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space)
                    general = creator.community(space).channel
                    creator.send(space, "General only")
                    creator.flush(space)
                    val workshop = creator.createChannel(space, SpaceChannelProfile("workshop"))
                    creator.flush(space)
                    creator.selectChannel(space, workshop)
                    creator.send(space, "Workshop only")
                    creator.flush(space)
                    var altered = true
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { request ->
                        val result = remote.exchange(request.envelope())
                        val body = SaitoEnvelope.decode(result).body
                        if (altered && body.get("request").asString == "page" &&
                            body.getAsJsonObject("scope").get("stream").asString == "content" &&
                            body.getAsJsonObject("scope").get("channel_id").asString == workshop) {
                            val item = if (!relabel) generalReceipt else {
                                val originalReceipt = SaitoEnvelope.decode(generalReceipt).body.deepCopy().apply { addProperty("channel_id", workshop) }
                                SaitoTransaction.createSpaceReply(service, originalReceipt, clock.millis(), owner.publicKey).envelope()
                            }
                            val changed = body.deepCopy().apply { add("items", JsonArray().apply { add(item) }) }
                            SaitoTransaction.createSpaceReply(service, changed, clock.millis(), visitor.publicKey).envelope()
                        } else result
                    }
                    reader.openPublic(space)
                    reader.selectChannel(space, workshop)
                    reader.saveDraft(space, "Keep this channel's independent writing")
                    try { reader.refresh(space); fail("Requested channel, receipt channel and signed original must agree") }
                    catch (_: IllegalArgumentException) { }
                    assertTrue(reader.messages(space).isEmpty())
                    assertEquals("Keep this channel's independent writing", reader.draft(space))
                    reader.selectChannel(space, general)
                    assertTrue("A rejected page cannot populate another channel", reader.messages(space).isEmpty())
                    reader.selectChannel(space, workshop)
                    altered = false
                    reader.refresh(space)
                    assertEquals(listOf("Workshop only"), reader.messages(space))
                    assertEquals("Keep this channel's independent writing", reader.draft(space))
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun privateChannelsUseIndependentMembershipKeysHistoryAndDraftsWithinTheSameSpace() = runBlocking {
        val directory = files.newFolder("private-channel-groups").toPath()
        SpacesServiceStore(directory, service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-channel-groups-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-channel-groups-member.sqlite").absolutePath).use { memberStore ->
                    val controlBodies = mutableListOf<JsonObject>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
                        val body = SaitoEnvelope.decode(request.envelope()).body
                        if (body.get("kind")?.asString == "create-channel") controlBodies += body.deepCopy()
                        remote.exchange(request.envelope())
                    }
                    val member = SpaceClient(visitor, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create("Independent private channel groups")
                    creator.flush(space)
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "General private writing")
                    creator.send(space, "General sealed message")
                    creator.flush(space)
                    val workshop = creator.createChannel(space, SpaceChannelProfile("a private workshop for members"))
                    creator.flush(space)
                    assertFalse(controlBodies.single().has("channel_profile"))
                    assertFalse(controlBodies.single().toString().contains("a private workshop for members"))
                    creator.selectChannel(space, workshop)
                    creator.send(space, "Workshop sealed message")
                    creator.flush(space)
                    creator.selectChannel(space, general)
                    val link = creator.invite(space)
                    creator.flush(space)
                    member.join(link)
                    member.flush(space)
                    creator.admitPending(space)
                    creator.flush(space)
                    member.refresh(space)
                    assertEquals(listOf("General sealed message"), member.messages(space))
                    assertEquals(listOf("general", "a private workshop for members"), member.channels(space).map { it.profile.name })
                    member.selectChannel(space, workshop)
                    assertFalse("Space membership alone is not a channel key", member.community(space).canSend)
                    assertTrue(member.messages(space).isEmpty())
                    member.saveDraft(space, "Workshop private writing")
                    member.flush(space)
                    creator.admitPending(space)
                    creator.flush(space)
                    member.refresh(space)
                    assertTrue(member.community(space).canSend)
                    assertEquals(listOf("Workshop sealed message"), member.messages(space))
                    assertEquals("Workshop private writing", member.draft(space))
                    member.send(space, "A member answers inside the workshop")
                    member.flush(space)
                    creator.refresh(space)
                    assertEquals(listOf("General sealed message"), creator.messages(space))
                    assertEquals("General private writing", creator.draft(space))
                    creator.selectChannel(space, workshop)
                    creator.refresh(space)
                    assertEquals(listOf("Workshop sealed message", "A member answers inside the workshop"), creator.messages(space))
                    creator.selectChannel(space, general)
                    creator.send(space, "General keys still work independently")
                    creator.flush(space)
                    member.selectChannel(space, general)
                    member.refresh(space)
                    assertEquals(listOf("General sealed message", "General keys still work independently"), member.messages(space))
                }
            }
        }
        java.nio.file.Files.list(directory).use { paths -> paths.filter(java.nio.file.Files::isRegularFile).forEach { path ->
            val bytes = java.nio.file.Files.readAllBytes(path).toString(Charsets.ISO_8859_1)
            for (privateText in listOf("a private workshop for members", "Workshop sealed message", "A member answers inside the workshop"))
                assertFalse("The routing service never stores private channel names or content in plaintext", bytes.contains(privateText))
        } }
    }

    @Test fun aColdMemberCanApplyOtherChannelAdmissionsBeforeHistoricalMetadataKeysArrive() = runBlocking {
        SpacesServiceStore(files.newFolder("private-cold-controls").toPath(), service, clock).use { remote ->
            val stores = (1..3).map { SqliteClientFixture(files.newFile("cold-controls-$it.sqlite").absolutePath) }
            try {
                fun client(wallet: SaitoWallet, index: Int) = SpaceClient(wallet, "fixture-node", service.publicKey, stores[index], clock) {
                    remote.exchange(it.envelope())
                }
                val creator = client(owner, 0)
                val first = client(visitor, 1)
                val cold = client(SaitoWallet.fromPrivateKey("0".repeat(63) + "4"), 2)
                val space = creator.create("Cold channel control recovery")
                creator.flush(space)
                val workshop = creator.createChannel(space, SpaceChannelProfile("Workshop metadata from the first epoch"))
                creator.flush(space)
                val link = creator.invite(space)
                creator.flush(space)
                first.join(link); first.flush(space)
                creator.admitPending(space); creator.flush(space); first.refresh(space)
                cold.join(link); cold.flush(space)
                creator.admitPending(space); creator.flush(space)
                first.refresh(space); first.selectChannel(space, workshop); first.flush(space)
                creator.admitPending(space); creator.flush(space)
                // Cold has neither its Welcome nor General's earlier archive
                // key yet. Another channel's control must not block either.
                cold.refresh(space)
                assertTrue(cold.community(space).joined)
                assertEquals(listOf("general", "Workshop metadata from the first epoch"), cold.channels(space).map { it.profile.name })
                cold.selectChannel(space, workshop); cold.flush(space)
                creator.admitPending(space); creator.flush(space); cold.refresh(space)
                assertTrue(cold.community(space).canSend)
                cold.send(space, "Independent cold channel access recovered")
                cold.flush(space)
                first.refresh(space)
                assertEquals(listOf("Independent cold channel access recovered"), first.messages(space))
            } finally { stores.forEach { it.close() } }
        }
    }

    @Test fun aPrivateChannelAdmissionSurvivesRejectionRestartAndLostAcknowledgmentWithoutReplacingGeneralKeys() = runBlocking {
        val directory = files.newFolder("private-channel-recovery").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        val ownerStore = SqliteClientFixture(files.newFile("private-channel-recovery-owner.sqlite").absolutePath)
        val memberStore = SqliteClientFixture(files.newFile("private-channel-recovery-member.sqlite").absolutePath)
        var lastControl: SaitoTransaction? = null
        var invitation: SaitoTransaction? = null
        var loseAdmission = false
        val acceptedAttempts = mutableListOf<String>()
        fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { request ->
            val body = SaitoEnvelope.decode(request.envelope()).body
            val result = remote.exchange(request.envelope())
            val reply = SaitoEnvelope.decode(result).body
            if (body.get("kind")?.asString == "invite") invitation = request
            if (reply.get("stream")?.asString == "control") {
                lastControl = request
                if (body.get("kind")?.asString == "admit" && loseAdmission) {
                    acceptedAttempts += request.envelope()
                    if (acceptedAttempts.size == 1) throw java.io.IOException("Accepted channel admission response lost")
                }
            }
            result
        }
        try {
            var creator = ownerClient()
            val member = SpaceClient(visitor, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
            val space = creator.create("Independent recovery branches")
            creator.flush(space)
            val general = creator.community(space).channel
            creator.send(space, "General before rejected child admission"); creator.flush(space)
            val workshop = creator.createChannel(space, SpaceChannelProfile("Recovery workshop"))
            creator.flush(space)
            creator.selectChannel(space, workshop)
            creator.send(space, "Workshop before rejected child admission"); creator.flush(space)
            creator.selectChannel(space, general)
            val link = creator.invite(space); creator.flush(space)
            member.join(link); member.flush(space)
            creator.admitPending(space); creator.flush(space); member.refresh(space)
            member.selectChannel(space, workshop); member.saveDraft(space, "Private recovery writing"); member.flush(space)
            creator.admitPending(space)
            // A genuine concurrently accepted owner control rejects the first
            // candidate. The child's cancellation branch must stay in the child.
            val concurrentBody = SaitoEnvelope.decode(requireNotNull(invitation).envelope()).body.deepCopy().apply {
                addProperty("operation_id", "9".repeat(64)); addProperty("control_id", requireNotNull(lastControl).id)
            }
            remote.submit(SaitoTransaction.create(owner, concurrentBody, clock.millis() + 1, listOf(service.publicKey)).envelope())
            creator.flush(space)
            remote.close(); remote = SpacesServiceStore(directory, service, clock)
            creator = ownerClient()
            creator.refresh(space)
            assertEquals(listOf("General before rejected child admission"), creator.messages(space))
            creator.admitPending(space)
            loseAdmission = true
            try { creator.flush(space); fail("Hold the actual accepted child acknowledgment") } catch (_: java.io.IOException) { }
            creator = ownerClient()
            creator.flush(space)
            assertEquals(2, acceptedAttempts.size)
            assertEquals("Restart resends the exact accepted original", acceptedAttempts[0], acceptedAttempts[1])
            member.refresh(space)
            assertEquals(listOf("Workshop before rejected child admission"), member.messages(space))
            assertEquals("Private recovery writing", member.draft(space))
            assertTrue(member.community(space).canSend)
            member.send(space, "Workshop after lost accepted acknowledgment"); member.flush(space)
            creator.selectChannel(space, workshop); creator.refresh(space)
            assertEquals(listOf("Workshop before rejected child admission", "Workshop after lost accepted acknowledgment"), creator.messages(space))
            creator.selectChannel(space, general)
            creator.send(space, "General after child recovery"); creator.flush(space)
            member.selectChannel(space, general); member.refresh(space)
            assertEquals(listOf("General before rejected child admission", "General after child recovery"), member.messages(space))
        } finally { remote.close(); ownerStore.close(); memberStore.close() }
    }

    @Test fun privateAnnouncementPostingPermissionDoesNotRequireHydratingItsDisplayProfile() = runBlocking {
        SpacesServiceStore(files.newFolder("private-announcement-permission").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-announcement-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-announcement-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val member = SpaceClient(visitor, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create("Announcements without display hydration")
                    creator.flush(space)
                    val announcement = creator.createChannel(space, SpaceChannelProfile("Notices", SpaceChannelKind.ANNOUNCEMENT))
                    creator.flush(space)
                    val link = creator.invite(space); creator.flush(space)
                    member.join(link); member.flush(space)
                    creator.admitPending(space); creator.flush(space); member.refresh(space)
                    // A restored/directly selected channel need not have had
                    // its optional presentation profile decoded via channels().
                    member.selectChannel(space, announcement); member.flush(space)
                    creator.admitPending(space); creator.flush(space); member.refresh(space)
                    assertFalse("Canonical posting policy must drive the send control", member.community(space).canSend)
                    assertThrows(IllegalArgumentException::class.java) { member.send(space, "A member cannot publish an announcement") }
                    creator.selectChannel(space, announcement)
                    creator.send(space, "An authorized announcement"); creator.flush(space); member.refresh(space)
                    assertEquals(listOf("An authorized announcement"), member.messages(space))
                    member.channels(space)
                    assertFalse("Decoding display metadata does not change posting authority", member.community(space).canSend)
                }
            }
        }
    }

    @Test fun anOwnerCanListUnlistAndRelistAPublicCommunityWithSignedDurableChanges() = runBlocking {
        SpacesServiceStore(files.newFolder("owner-listing-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("listing-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("listing-visitor.sqlite").absolutePath).use { visitorStore ->
                    var control = ""
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        remote.exchange(original.envelope()).also { control = original.id }
                    }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Listed deliberately", "Public even when unlisted", SpaceVisibility.PUBLIC, listed = false))
                    creator.flush(space)
                    assertTrue(reader.discover().items.isEmpty())
                    creator.setListed(space, true)
                    creator.flush(space)
                    assertEquals(listOf(space), reader.discover().items.map { it.id })
                    creator.setListed(space, false)
                    creator.flush(space)
                    assertTrue(reader.discover().items.isEmpty())
                    val unlisted = reader.openPublic(space)
                    assertEquals("Listed deliberately", unlisted.name)
                    assertFalse(unlisted.joined)
                    reader.refresh(space)
                    assertTrue("Unlisting does not make public access private", reader.communities().isEmpty())
                    creator.setListed(space, true)
                    creator.flush(space)
                    assertEquals(listOf(space), reader.discover().items.map { it.id })
                    assertFalse(creator.community(space).pending)
                    fun attempt(actor: SaitoWallet, target: String, head: String) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", target); addProperty("operation_id", "f".repeat(64))
                        addProperty("kind", "set-listed"); addProperty("control_id", head); addProperty("listed", true)
                    }, clock.millis(), listOf(service.publicKey)).envelope()
                    assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(attempt(visitor, space, control))).body.get("stream").asString)
                    val privateSpace = creator.create("Always private")
                    creator.flush(privateSpace)
                    try { creator.setListed(privateSpace, true); fail("Listing does not make a private community public") }
                    catch (_: IllegalArgumentException) { }
                    assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(attempt(owner, privateSpace, control))).body.get("stream").asString)
                    assertEquals(listOf(space), reader.discover().items.map { it.id })
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun schemaThreeDirectoryBackfillAndReentryPreserveActualAcceptedPrivateAndPublicRecords() = runBlocking {
        val directory = files.newFolder("schema-three-directory").toPath()
        SqliteClientFixture(files.newFile("migration-owner.sqlite").absolutePath).use { ownerStore ->
            SqliteClientFixture(files.newFile("migration-reader.sqlite").absolutePath).use { visitorStore ->
                val originals = linkedMapOf<String, String>()
                var publicSpace = ""
                var privateSpace = ""
                SpacesServiceStore(directory, service, clock).use { initial ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        initial.exchange(original.envelope()).also { receipt ->
                            if (SaitoEnvelope.decode(original.envelope()).body.get("request").asString == "event")
                                originals[original.envelope()] = receipt
                        }
                    }
                    publicSpace = creator.create(SpaceProfile("A listed migration", "Actual public metadata", SpaceVisibility.PUBLIC))
                    creator.flush(publicSpace)
                    val unlisted = creator.create(SpaceProfile("Not in the migrated list", "Unlisted", SpaceVisibility.PUBLIC, listed = false))
                    creator.flush(unlisted)
                    privateSpace = creator.create(SpaceProfile("Keep this private", "No private directory entry"))
                    creator.flush(privateSpace)
                    creator.send(privateSpace, "An original encrypted private record")
                    creator.flush(privateSpace)
                }
                // Independently establish the prior schema layout around the
                // actual accepted rows. This is not an old-binary rehearsal.
                DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite")}").use { db ->
                    db.createStatement().use { sql ->
                        sql.execute("DROP TABLE public_spaces")
                        sql.execute("PRAGMA user_version=3")
                    }
                }
                var firstCursor: Long? = null
                repeat(2) {
                    SpacesServiceStore(directory, service, clock).use { reopened ->
                        assertEquals(4, com.google.gson.JsonParser.parseString(reopened.readiness()).asJsonObject.get("schema").asInt)
                        val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { reopened.exchange(it.envelope()) }
                        val page = reader.discover()
                        assertEquals(listOf(publicSpace), page.items.map { it.id })
                        assertEquals("Actual public metadata", page.items.single().profile.description)
                        if (firstCursor == null) firstCursor = page.nextCursor else assertEquals(firstCursor, page.nextCursor)
                        assertFalse(page.hasMore)
                        for ((original, receipt) in originals) assertEquals(receipt, reopened.exchange(original))
                        val restored = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { reopened.exchange(it.envelope()) }
                        restored.refresh(privateSpace)
                        assertEquals(listOf("An original encrypted private record"), restored.messages(privateSpace))
                        assertTrue(reader.communities().isEmpty())
                    }
                }
            }
        }
    }

    @Test fun pagedDiscoveryKeepsOnlyTwoHundredRecentProfilesAndPreservesPrivateWorkAfterReload() = runBlocking {
        SpacesServiceStore(files.newFolder("bounded-directory-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("bounded-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("bounded-visitor.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    fun reader() = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val first = reader()
                    val privateWork = first.create("My private work")
                    first.flush(privateWork)
                    first.send(privateWork, "A retained private message")
                    first.flush(privateWork)
                    first.saveDraft(privateWork, "Keep my separate unsent writing")
                    val expected = (0..200).map { number ->
                        val name = "Listed community" + number.toString().padStart(3, '0')
                        creator.create(SpaceProfile(name, "Discovery cache reference", SpaceVisibility.PUBLIC)).also { creator.flush(it) }
                    }
                    val received = mutableListOf<String>()
                    var cursor = 0L
                    do {
                        val page = first.discover(cursor = cursor)
                        assertTrue(page.items.size <= 50)
                        received += page.items.map { it.id }
                        if (page.hasMore) assertTrue(page.nextCursor > cursor)
                        cursor = page.nextCursor
                    } while (page.hasMore)
                    assertEquals(expected, received)
                    val restored = reader()
                    assertEquals(200, restored.cachedDirectory().size)
                    assertFalse(restored.cachedDirectory().any { it.id == expected.first() })
                    assertTrue(restored.cachedDirectory().any { it.id == expected.last() })
                    assertEquals(listOf(expected.first()), restored.discover("Listed community000").items.map { it.id })
                    assertEquals(200, first.cachedDirectory().size)
                    assertTrue(first.cachedDirectory().any { it.id == expected.first() })
                    assertFalse(first.cachedDirectory().any { it.id == expected[1] })
                    assertEquals(listOf(privateWork), restored.communities().map { it.id })
                    assertEquals(listOf("A retained private message"), restored.messages(privateWork))
                    assertEquals("Keep my separate unsent writing", restored.draft(privateWork))
                }
            }
        }
    }

    @Test fun aDirectoryCallbackCannotTurnAnOwnerSignedUnlistedProfileIntoAListing() = runBlocking {
        SpacesServiceStore(files.newFolder("unlisted-callback-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("unlisted-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("unlisted-visitor.sqlite").absolutePath).use { visitorStore ->
                    var creation: String? = null
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) {
                        remote.exchange(it.envelope()).also { receipt -> creation = receipt }
                    }
                    val space = creator.create(SpaceProfile("Shared only by link", "Do not list this", SpaceVisibility.PUBLIC, listed = false))
                    creator.flush(space)
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { request ->
                        val actual = remote.exchange(request.envelope())
                        val signed = SaitoEnvelope.decode(actual)
                        if (signed.body.get("request").asString == "page") {
                            val body = signed.body.deepCopy().apply {
                                add("items", JsonArray().apply { add(JsonObject().apply {
                                    addProperty("creation", requireNotNull(creation)); addProperty("listed", true)
                                }) })
                            }
                            SaitoTransaction.createSpaceReply(service, body, clock.millis(), visitor.publicKey).envelope()
                        } else actual
                    }
                    try { reader.discover(); fail("An owner-signed opt-out cannot be replaced by a directory flag") }
                    catch (_: IllegalArgumentException) { }
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun publicDiscoveryShowsActualListedProfilesWithoutJoiningOrExposingPrivateNames() = runBlocking {
        SpacesServiceStore(files.newFolder("directory-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visitor.sqlite").absolutePath).use { visitorStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(visitor, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val shown = creator.create(SpaceProfile("Saito Builders", "Build useful things together", SpaceVisibility.PUBLIC))
                    creator.flush(shown)
                    val privateSpace = creator.create(SpaceProfile("Private builders", "Keep this description private"))
                    creator.flush(privateSpace)
                    val unlisted = creator.create(SpaceProfile("Unlisted builders", "Public by link", SpaceVisibility.PUBLIC, listed = false))
                    creator.flush(unlisted)
                    val results = reader.discover("BUILD").items
                    assertEquals(listOf(shown), results.map { it.id })
                    assertEquals("Saito Builders", results.single().profile.name)
                    assertEquals("Build useful things together", results.single().profile.description)
                    assertEquals(SpaceVisibility.PUBLIC, results.single().profile.visibility)
                    assertTrue(results.single().profile.listed)
                    assertTrue("Discovery is not membership", reader.communities().isEmpty())
                    assertTrue(reader.discover("a name that is absent").items.isEmpty())
                    assertEquals(listOf(shown), reader.discover().items.map { it.id })
                }
            }
        }
    }
}

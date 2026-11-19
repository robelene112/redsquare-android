package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceCrypto
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

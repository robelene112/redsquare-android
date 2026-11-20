package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceChannelsTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test(timeout = 45_000) fun demotionStopsFuturePrivatePublicationUntilOldAdministratorKeysAreReplaced() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("demotion-keys-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("demotion-keys-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("demotion-keys-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("demotion-keys-reader.sqlite").absolutePath).use { readerStore ->
                        val readerWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        val originals = mutableListOf<SaitoTransaction>()
                        var loseRotationReply = true
                        fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            originals.add(original)
                            val reply = remote.exchange(original.envelope())
                            if (loseRotationReply && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "rotate-access") {
                                loseRotationReply = false
                                throw java.io.IOException("Owned accepted key-rotation reply lost")
                            }
                            reply
                        }
                        var creator = ownerClient()
                        val administrator = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(readerWallet, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Demotion keys", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (participant in listOf(administrator, reader)) { participant.openPublic(space); participant.joinPublic(space); participant.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private planning"), setOf(readerWallet.publicKey)); creator.flush(space)
                        for (participant in listOf(administrator, reader)) {
                            participant.refresh(space); participant.selectChannel(space, channel); participant.flush(space)
                            creator.admitPending(space); creator.flush(space); participant.refresh(space, channel)
                        }
                        administrator.refresh(space, channel)
                        creator.send(space, "Before the administrator change", channel); creator.flush(space)
                        administrator.refresh(space, channel); reader.refresh(space, channel)
                        fun savedKeys(store: SqliteClientFixture): SpaceCrypto = store.transaction {
                            val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                            SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                        }
                        val copied = savedKeys(adminStore)
                        val ownerKeys = savedKeys(ownerStore)
                        val past = originals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                        assertEquals("Before the administrator change", copied.read(past.envelope()).text)
                        creator.setAdministrator(space, member.publicKey, false)
                        try { creator.flush(space); fail("The accepted rotation reply is deliberately lost") }
                        catch (_: java.io.IOException) { }
                        val previousKeysPublication = ownerKeys.publish(owner, "message", "d".repeat(64),
                            requireNotNull(creator.roleChange(space)).id, "Must wait for the access update", clock.millis())
                        assertEquals("Copied old keys still exist; the receiver must not accept new content under them",
                            "Must wait for the access update", copied.read(previousKeysPublication.envelope()).text)
                        assertEquals("The membership boundary cannot admit future private content under a retired administrator key", "rejection",
                            SaitoEnvelope.decode(remote.exchange(previousKeysPublication.envelope())).body.get("stream").asString)
                        assertFalse("The native send state must wait for confirmed current keys", creator.community(space, channel).canSend)
                        creator = ownerClient(); creator.flush(space)
                        val rotations = originals.filter { SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString == "rotate-access" }
                        assertEquals(2, rotations.size); assertEquals(rotations[0].envelope(), rotations[1].envelope())
                        assertTrue(creator.community(space, channel).canSend)
                        assertTrue(creator.channels(space).single { it.id == channel }.metadataReady)
                        creator.send(space, "After the confirmed key update", channel); creator.flush(space)
                        reader.refresh(space, channel)
                        assertEquals(listOf("Before the administrator change", "After the confirmed key update"), reader.messages(space, channel))
                        val future = originals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                        try { copied.read(future.envelope()); fail("Copied retired keys cannot decrypt later accepted private content") }
                        catch (_: SpaceCrypto.MissingKeys) { }
                        assertEquals("Before the administrator change", copied.read(past.envelope()).text)
                        administrator.refresh(space, creator.channels(space).single { it.visibility == SpaceVisibility.PUBLIC }.id)
                        assertFalse(administrator.channels(space).any { it.id == channel })
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aDelayedRotationRejectionCannotReplaceKeysAcceptedFromAnotherHolder() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("rotation-race-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("rotation-race-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("rotation-race-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("rotation-race-reader.sqlite").absolutePath).use { readerStore ->
                        val readerWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        var loseDemotionReply = true
                        var holdReaderRotation = true
                        val readerOriginals = mutableListOf<SaitoTransaction>()
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            val body = SaitoEnvelope.decode(original.envelope()).body
                            val reply = remote.exchange(original.envelope())
                            if (loseDemotionReply && body.get("kind")?.asString == "set-role" && body.get("role")?.asString == "member") {
                                loseDemotionReply = false; assertEquals("control", SaitoEnvelope.decode(reply).body.get("stream").asString)
                                throw java.io.IOException("Owned demotion receipt lost before local rotation")
                            }
                            reply
                        }
                        val administrator = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        fun readerClient() = SpaceClient(readerWallet, "fixture-node", service.publicKey, readerStore, clock) { original ->
                            readerOriginals.add(original)
                            if (holdReaderRotation && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "rotate-access") {
                                holdReaderRotation = false
                                throw java.io.IOException("Owned reader rotation held before delivery")
                            }
                            remote.exchange(original.envelope())
                        }
                        var reader = readerClient()
                        val space = creator.create(SpaceProfile("Concurrent access updates", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (participant in listOf(administrator, reader)) { participant.openPublic(space); participant.joinPublic(space); participant.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private planning"), setOf(readerWallet.publicKey)); creator.flush(space)
                        for (participant in listOf(administrator, reader)) {
                            participant.refresh(space); participant.selectChannel(space, channel); participant.flush(space)
                            creator.admitPending(space); creator.flush(space); participant.refresh(space, channel)
                        }
                        creator.send(space, "Before concurrent access changes", channel); creator.flush(space); administrator.refresh(space, channel)
                        val copied = adminStore.transaction {
                            val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                            SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                        }
                        creator.setAdministrator(space, member.publicKey, false)
                        try { creator.flush(space); fail("The demotion receipt is deliberately lost") } catch (_: java.io.IOException) { }
                        reader.refresh(space, channel)
                        try { reader.flush(space); fail("The reader's original rotation is held before delivery") } catch (_: java.io.IOException) { }
                        creator.flush(space) // A different current holder actually rotates first.
                        reader = readerClient(); reader.refresh(space, channel) // Accept the winner before the stale local disposition.
                        reader.flush(space)
                        val attempts = readerOriginals.filter { SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString == "rotate-access" }
                        assertEquals(2, attempts.size); assertEquals(attempts[0].envelope(), attempts[1].envelope())
                        assertTrue(reader.community(space, channel).canSend)
                        reader.send(space, "After learning another holder's accepted keys", channel); reader.flush(space)
                        val future = readerOriginals.last { SaitoEnvelope.decode(it.envelope()).body.get("content_kind")?.asString == "message" }
                        try { copied.read(future.envelope()); fail("A stale rejection cannot restore a retired encryption key") }
                        catch (_: SpaceCrypto.MissingKeys) { }
                        creator.refresh(space, channel)
                        assertEquals(listOf("Before concurrent access changes", "After learning another holder's accepted keys"), creator.messages(space, channel))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun explicitRegrantAndRevokeCannotBypassAnOutstandingDeviceRetirement() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("retirement-revoke-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("retirement-revoke-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("retirement-revoke-admin.sqlite").absolutePath).use { adminStore ->
                    var head = ""
                    fun submit(original: SaitoTransaction) = remote.exchange(original.envelope()).also { reply ->
                        if (SaitoEnvelope.decode(reply).body.get("stream")?.asString == "control") head = original.id
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { submit(it) }
                    val administrator = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { submit(it) }
                    val space = creator.create(SpaceProfile("Retirement ordering", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    administrator.openPublic(space); administrator.joinPublic(space); administrator.flush(space); creator.refresh(space)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                    val profile = SpaceChannelProfile("Private planning")
                    val channel = creator.createPrivateChannel(space, profile, emptySet()); creator.flush(space)
                    administrator.refresh(space); administrator.selectChannel(space, channel); administrator.flush(space)
                    creator.admitPending(space); creator.flush(space); administrator.refresh(space, channel)
                    val keys = ownerStore.transaction {
                        val saved = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                        SpaceCrypto.restore(java.util.Base64.getDecoder().decode(saved.get("crypto").asString))
                    }
                    var operation = 100
                    fun event(kind: String, extra: JsonObject.() -> Unit) = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("operation_id", (++operation).toString(16).padStart(64, '0'))
                        addProperty("control_id", head); addProperty("channel_id", channel); extra()
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(submit(event("set-role") {
                        addProperty("member", member.publicKey); addProperty("role", "member")
                    })).body.get("stream").asString)
                    assertEquals("control", SaitoEnvelope.decode(submit(event("grant-channel-access") {
                        addProperty("member", member.publicKey)
                    })).body.get("stream").asString)
                    val changed = keys.remove(keys.bindings.keys.single { it != keys.credential })
                    val metadata = changed.session.publish(owner, "channel-profile", (++operation).toString(16).padStart(64, '0'), head,
                        JsonObject().apply { addProperty("version", 1); addProperty("channel_id", channel); add("profile", profile.json()) }.toString(), clock.millis())
                    val revoke = event("revoke-channel-access") {
                        addProperty("member", member.publicKey); addProperty("epoch", changed.session.epoch); addProperty("commit", changed.commit)
                        add("devices", JsonObject().apply { changed.bindings.forEach { (id, signer) -> addProperty(id, signer) } })
                        addProperty("channel_metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                    }
                    assertEquals("Metadata-producing revocation waits for the already accepted policy's key update", "rejection",
                        SaitoEnvelope.decode(submit(revoke)).body.get("stream").asString)
                    creator.refresh(space); creator.flush(space)
                    assertTrue("The actual rotation must remain recoverable after the rejected intervening revoke", creator.community(space, channel).canSend)
                    administrator.refresh(space, channel); administrator.flush(space)
                    creator.admitPending(space); creator.flush(space); administrator.refresh(space, channel)
                    assertTrue(administrator.channels(space).single { it.id == channel }.ready)
                    creator.send(space, "Regranted access uses a new admission", channel); creator.flush(space); administrator.refresh(space, channel)
                    assertEquals(listOf("Regranted access uses a new admission"), administrator.messages(space, channel))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aRejectedAdmissionKeepsHistoryReceivedAtTheSameEpochWhileItWasPending() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("same-epoch-history-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("same-epoch-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("same-epoch-reader.sqlite").absolutePath).use { readerStore ->
                    SqliteClientFixture(files.newFile("same-epoch-guest.sqlite").absolutePath).use { guestStore ->
                        val guestWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        var holdHistory = false
                        var holdAdmission = true
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            if (holdHistory && SaitoEnvelope.decode(original.envelope()).body.get("content_kind")?.asString == "history-keys") {
                                holdHistory = false
                                throw java.io.IOException("Owned historical key grant held before delivery")
                            }
                            remote.exchange(original.envelope())
                        }
                        fun readerClient() = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { original ->
                            if (holdAdmission && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "admit") {
                                holdAdmission = false
                                throw java.io.IOException("Owned admission held before delivery")
                            }
                            remote.exchange(original.envelope())
                        }
                        var reader = readerClient()
                        val guest = SpaceClient(guestWallet, "fixture-node", service.publicKey, guestStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Retained historical reading", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (participant in listOf(reader, guest)) { participant.openPublic(space); participant.joinPublic(space); participant.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                        val general = creator.community(space).channel
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private history"), setOf(guestWallet.publicKey)); creator.flush(space)
                        creator.send(space, "Writing before the reader joined", channel); creator.flush(space)
                        reader.refresh(space); reader.selectChannel(space, channel); reader.flush(space)
                        creator.admitPending(space); holdHistory = true
                        try { creator.flush(space); fail("The actual earlier key grant is held before delivery") } catch (_: java.io.IOException) { }
                        reader.refresh(space, channel)
                        assertFalse(reader.messages(space, channel).contains("Writing before the reader joined"))
                        guest.refresh(space); guest.selectChannel(space, channel); guest.flush(space)
                        reader.admitPending(space)
                        try { reader.flush(space); fail("The actual candidate admission is held before delivery") } catch (_: java.io.IOException) { }
                        creator.flush(space) // Deliver the actual queued historical grant, without advancing this channel's epoch.
                        creator.archiveChannel(space, general, true); creator.flush(space) // Advance the shared control head only.
                        reader.refresh(space, channel)
                        assertEquals(listOf("Writing before the reader joined"), reader.messages(space, channel))
                        reader = readerClient(); reader.flush(space) // The older admission now receives its genuine stale-head rejection.
                        assertEquals("A same-epoch rejection must preserve already received historical keys",
                            listOf("Writing before the reader joined"), reader.messages(space, channel))
                        reader.refresh(space, channel)
                        assertEquals(listOf("Writing before the reader joined"), reader.messages(space, channel))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun earlierPrivateKindsSurviveRealAdmissionEditingAndColdRestore() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("legacy-kind-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("legacy-kind-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("legacy-kind-admin.sqlite").absolutePath).use { adminStore ->
                    var head = ""
                    var join: String? = null
                    fun exchange(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { receipt ->
                        val received = SaitoEnvelope.decode(receipt)
                        if (received.body.get("stream")?.asString == "control") head = original.id
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "join-channel")
                            join = original.envelope()
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { exchange(it) }
                    fun restoredAdmin() = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { exchange(it) }
                    var admin = restoredAdmin()
                    val space = creator.create(SpaceProfile("Legacy private metadata", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    admin.openPublic(space); admin.joinPublic(space); admin.flush(space); creator.refresh(space)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                    val scope = SpaceCrypto.Scope(service.publicKey, space, "a".repeat(64))
                    var keys = SpaceCrypto.device(owner.publicKey, "b".repeat(64)).create(scope)
                    val profile = SpaceChannelProfile("earlier-reading-room", SpaceChannelKind.ARTICLE, "Earlier")
                    var operation = 100
                    fun event(kind: String, extra: JsonObject.() -> Unit) = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("operation_id", (++operation).toString(16).padStart(64, '0'))
                        addProperty("control_id", head); addProperty("channel_id", scope.channelId); extra()
                    }, clock.millis(), listOf(service.publicKey))
                    fun metadata() = keys.publish(owner, "channel-profile", (++operation).toString(16).padStart(64, '0'), head,
                        JsonObject().apply { addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", profile.json()) }.toString(), clock.millis())
                    fun encoded(original: SaitoTransaction) = java.util.Base64.getEncoder().encodeToString(original.envelope().toByteArray(Charsets.UTF_8))
                    val initial = metadata()
                    val creation = event("create-channel") {
                        addProperty("visibility", "private"); addProperty("epoch", 0); addProperty("key_generation", scope.generation)
                        addProperty("admin_posts_only", false); add("allowed_members", com.google.gson.JsonArray().apply { add(member.publicKey) })
                        add("devices", JsonObject().apply { keys.bindings.forEach { (id, signer) -> addProperty(id, signer) } })
                        addProperty("metadata", encoded(initial)) // Earlier signed creation has no visible channel_kind.
                    }
                    assertEquals("control", SaitoEnvelope.decode(exchange(creation)).body.get("stream").asString)
                    admin.refresh(space); admin.selectChannel(space, scope.channelId); admin.flush(space)
                    val requested = SaitoEnvelope.decode(requireNotNull(join))
                    val bound = requested.body.getAsJsonObject("key_package")
                    val admission = keys.admit(SpaceCrypto.BoundPackage(scope, bound.get("wallet").asString,
                        bound.get("device").asString, bound.get("package").asString, bound.get("signer").asString, bound.get("signature").asString))
                    keys = admission.session
                    val admittedMetadata = metadata()
                    val admitted = event("admit") {
                        addProperty("join", requireNotNull(join)); addProperty("epoch", keys.epoch)
                        addProperty("welcome", admission.welcome); addProperty("commit", admission.commit)
                        add("devices", JsonObject().apply { keys.bindings.forEach { (id, signer) -> addProperty(id, signer) } })
                        addProperty("channel_metadata", encoded(admittedMetadata))
                    }
                    assertEquals("control", SaitoEnvelope.decode(exchange(admitted)).body.get("stream").asString)
                    val history = keys.archiveHistory(owner, admission.bindings.keys.single { it != keys.credential },
                        (++operation).toString(16).padStart(64, '0'), head, clock.millis())
                    exchange(history.transaction)
                    admin.refresh(space, scope.channelId)
                    assertEquals(profile, admin.channels(space).single { it.id == scope.channelId }.profile)
                    admin.saveDraft(space, "Keep the legacy article draft", scope.channelId)
                    val renamed = profile.copy(name = "renamed-earlier-reading", category = "Reading together")
                    admin.editChannel(space, scope.channelId, renamed); admin.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, admin.channelEdit(space, scope.channelId)?.status)
                    admin = restoredAdmin(); admin.refresh(space, scope.channelId)
                    assertEquals(renamed, admin.channels(space).single { it.id == scope.channelId }.profile)
                    assertTrue(admin.channels(space).single { it.id == scope.channelId }.metadataReady)
                    assertEquals("Keep the legacy article draft", admin.draft(space, scope.channelId))
                    try { admin.editChannel(space, scope.channelId, renamed.copy(kind = SpaceChannelKind.CHAT)); fail("Legacy editing preserves its authenticated original kind") }
                    catch (_: IllegalArgumentException) { }
                    creator.refresh(space); creator.setAdministrator(space, member.publicKey, false); creator.flush(space)
                    val removed = keys.remove(admission.bindings.keys.single { it != keys.credential })
                    keys = removed.session
                    val replacementMetadata = keys.publish(owner, "channel-profile", (++operation).toString(16).padStart(64, '0'), head,
                        JsonObject().apply { addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", renamed.json()) }.toString(), clock.millis())
                    val revoked = event("revoke-channel-access") {
                        addProperty("member", member.publicKey); addProperty("epoch", keys.epoch); addProperty("commit", removed.commit)
                        add("devices", JsonObject().apply { keys.bindings.forEach { (id, signer) -> addProperty(id, signer) } })
                        addProperty("channel_metadata", encoded(replacementMetadata))
                    }
                    assertEquals("control", SaitoEnvelope.decode(exchange(revoked)).body.get("stream").asString)
                    val general = creator.community(space).channel
                    admin.refresh(space, general)
                    assertFalse(admin.channels(space).any { it.id == scope.channelId })
                    creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                    admin = restoredAdmin(); admin.refresh(space, general)
                    val available = admin.channels(space).firstOrNull { it.id == scope.channelId }
                    assertNotNull("The authorized legacy channel remains visible while current readmission is held", available)
                    val visible = requireNotNull(available)
                    assertEquals("Retained authenticated metadata remains available to the newly authorized Admin", renamed, visible.profile)
                    assertFalse(visible.ready); assertFalse(visible.metadataReady)
                    try { admin.editChannel(space, scope.channelId, renamed.copy(name = "Cannot edit with retired keys")); fail("Editing waits for current keys") }
                    catch (_: IllegalArgumentException) { }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun earlierRestrictedMetadataEditsRemainBoundToTheirOwnSignedGroup() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("earlier-private-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("earlier-private-edit-owner.sqlite").absolutePath).use { ownerStore ->
                val receipts = mutableListOf<String>()
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { receipt ->
                        if (SaitoEnvelope.decode(receipt).body.get("stream")?.asString == "control") receipts.add(receipt)
                    }
                }
                val space = creator.create(SpaceProfile("Earlier restricted edit", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                val rootHead = SaitoEnvelope.decode(SaitoEnvelope.decode(receipts.single()).body.get("event").asString).id
                val scope = SpaceCrypto.Scope(service.publicKey, space, "4".repeat(64))
                val keys = SpaceCrypto.device(owner.publicKey, "5".repeat(64)).create(scope)
                val initial = keys.publish(owner, "channel-profile", "6".repeat(64), rootHead, JsonObject().apply {
                    addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", SpaceChannelProfile("earlier-private").json())
                }.toString(), clock.millis())
                fun event(kind: String, operation: String, head: String, extra: JsonObject.() -> Unit) = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("kind", kind); addProperty("operation_id", operation.repeat(64))
                    addProperty("control_id", head); addProperty("channel_id", scope.channelId); extra()
                }, clock.millis(), listOf(service.publicKey))
                fun encoded(original: SaitoTransaction) = java.util.Base64.getEncoder().encodeToString(original.envelope().toByteArray(Charsets.UTF_8))
                val creation = event("create-channel", "7", rootHead) {
                    addProperty("visibility", "private"); addProperty("epoch", 0); addProperty("key_generation", scope.generation)
                    addProperty("admin_posts_only", false); addProperty("channel_kind", "chat")
                    add("allowed_members", com.google.gson.JsonArray())
                    add("devices", JsonObject().apply { keys.bindings.forEach { (id, signer) -> addProperty(id, signer) } })
                    addProperty("metadata", encoded(initial))
                }
                val created = remote.exchange(creation.envelope()); receipts.add(created)
                assertEquals("control", SaitoEnvelope.decode(created).body.get("stream").asString)
                val earlier = keys.publish(owner, "channel-profile-edit", "8".repeat(64), creation.id, JsonObject().apply {
                    addProperty("version", 2); addProperty("channel_id", scope.channelId)
                    addProperty("name", "compatible-earlier-rename"); addProperty("category", "History")
                }.toString(), clock.millis())
                assertFalse(SaitoEnvelope.decode(earlier.envelope()).body.has("target_channel_id"))
                val edited = remote.exchange(event("edit-channel", "9", creation.id) { addProperty("channel_metadata", encoded(earlier)) }.envelope())
                assertEquals("control", SaitoEnvelope.decode(edited).body.get("stream").asString); receipts.add(edited)
                val journal = nl.davidengelen.redsquare.spaces.SpaceJournal(space, service.publicKey)
                receipts.forEach(journal::receive)
                assertEquals(SpaceChannelKind.CHAT, journal.snapshot.channels.getValue(scope.channelId).kind)
                assertEquals("compatible-earlier-rename", com.google.gson.JsonParser.parseString(keys.read(earlier.envelope()).text).asJsonObject.get("name").asString)
            }
        }
    }

    @Test(timeout = 45_000) fun generalEncryptedMetadataCannotBeRedirectedToAnotherPrivateChildOrGeneral() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("private-edit-target-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-edit-target-owner.sqlite").absolutePath).use { ownerStore ->
                var other: String? = null
                var general: String? = null
                var checked = false
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-channel") {
                        for ((target, operation) in listOf(requireNotNull(other) to "d", requireNotNull(general) to "e")) {
                            val redirected = SaitoEnvelope.decode(original.envelope()).body.deepCopy().apply {
                                addProperty("channel_id", target); addProperty("operation_id", operation.repeat(64))
                            }
                            val request = SaitoTransaction.create(owner, redirected, clock.millis(), listOf(service.publicKey))
                            assertEquals("The signed metadata target cannot be replaced by an outer channel", "rejection",
                                SaitoEnvelope.decode(remote.exchange(request.envelope())).body.get("stream").asString)
                        }
                        val redirected = SaitoEnvelope.decode(original.envelope()).body.deepCopy().apply {
                            addProperty("channel_id", requireNotNull(general)); addProperty("operation_id", "f".repeat(64))
                        }
                        val metadataBody = SaitoEnvelope.decode(String(java.util.Base64.getDecoder().decode(redirected.get("channel_metadata").asString), Charsets.UTF_8))
                            .body.deepCopy().apply { remove("target_channel_id") }
                        val strippedMetadata = SaitoTransaction.create(owner, metadataBody, clock.millis(), listOf(service.publicKey))
                        redirected.addProperty("channel_metadata", java.util.Base64.getEncoder().encodeToString(strippedMetadata.envelope().toByteArray(Charsets.UTF_8)))
                        val strippedTarget = SaitoTransaction.create(owner, redirected, clock.millis(), listOf(service.publicKey))
                        assertEquals("General requires an explicit target, even when both envelopes are re-signed", "rejection",
                            SaitoEnvelope.decode(remote.exchange(strippedTarget.envelope())).body.get("stream").asString)
                        checked = true
                    }
                    remote.exchange(original.envelope())
                }
                val space = creator.create(SpaceProfile("Separate private metadata targets")); creator.flush(space)
                general = creator.community(space).channel
                val first = creator.createChannel(space, SpaceChannelProfile("first-private")); creator.flush(space)
                other = creator.createChannel(space, SpaceChannelProfile("second-private")); creator.flush(space)
                creator.editChannel(space, first, SpaceChannelProfile("renamed-first")); creator.flush(space)
                assertTrue(checked)
                assertEquals("renamed-first", creator.channels(space).single { it.id == first }.profile.name)
                assertEquals("second-private", creator.channels(space).single { it.id == other }.profile.name)
                assertEquals("general", creator.channels(space).single { it.id == general }.profile.name)
            }
        }
    }

    @Test(timeout = 45_000) fun anAdministratorWithoutCurrentChannelKeysCannotReplacePrivateMetadata() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("unready-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("unready-edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("unready-edit-admin.sqlite").absolutePath).use { adminStore ->
                    var originalEdit: SaitoTransaction? = null
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-channel") originalEdit = original
                        remote.exchange(original.envelope())
                    }
                    val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Key-holder editing", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    admin.openPublic(space); admin.joinPublic(space); admin.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private original"), emptySet()); creator.flush(space)
                    creator.editChannel(space, channel, SpaceChannelProfile("Private renamed")); creator.flush(space)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                    assertFalse(admin.channels(space).any { it.id == channel && it.ready })
                    val head = requireNotNull(creator.roleChange(space)).id
                    val body = SaitoEnvelope.decode(requireNotNull(originalEdit).envelope()).body.deepCopy()
                    val encoded = body.get("channel_metadata").asString
                    val metadataBody = SaitoEnvelope.decode(String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8)).body.deepCopy()
                    metadataBody.addProperty("control_id", head); metadataBody.addProperty("operation_id", "b".repeat(64))
                    val metadata = SaitoTransaction.create(member, metadataBody, clock.millis(), listOf(service.publicKey))
                    body.addProperty("control_id", head); body.addProperty("operation_id", "c".repeat(64))
                    body.addProperty("channel_metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                    val request = SaitoTransaction.create(member, body, clock.millis(), listOf(service.publicKey))
                    assertEquals("A role alone does not provide a bound channel key holder", "rejection",
                        SaitoEnvelope.decode(remote.exchange(request.envelope())).body.get("stream").asString)
                    assertEquals("Private renamed", creator.channels(space).single { it.id == channel }.profile.name)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun privateChannelRenamesStayEncryptedAndKeepReaderHistoryAndDraftsAfterProcessRecovery() =
        exercisePrivateEditing(SpaceVisibility.PUBLIC)
    @Test(timeout = 45_000) fun privateGeneralRenamesUseTheCurrentGeneralKeysAndPreserveHistory() =
        exercisePrivateEditing(SpaceVisibility.PRIVATE, general = true)
    @Test(timeout = 45_000) fun unrestrictedPrivateChildRenamesUseGeneralMetadataKeysAndPreserveItsOwnHistory() =
        exercisePrivateEditing(SpaceVisibility.PRIVATE)

    private fun exercisePrivateEditing(visibility: SpaceVisibility, general: Boolean = false) = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("private-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-edit-reader.sqlite").absolutePath).use { readerStore ->
                    val attempts = mutableListOf<String>()
                    var loseReply = false
                    fun manager() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val edit = SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-channel"
                        if (edit && attempts.isEmpty()) {
                            val changedKind = SaitoEnvelope.decode(original.envelope()).body.deepCopy().apply {
                                addProperty("channel_kind", "article"); addProperty("operation_id", "f".repeat(64))
                            }
                            val request = SaitoTransaction.create(owner, changedKind, clock.millis(), listOf(service.publicKey))
                            assertEquals("Even a current key holder cannot change the signed creation kind", "rejection",
                                SaitoEnvelope.decode(remote.exchange(request.envelope())).body.get("stream").asString)
                        }
                        if (edit) attempts.add(original.envelope())
                        val response = remote.exchange(original.envelope())
                        if (edit && loseReply) { loseReply = false; throw java.io.IOException("Owned accepted private edit reply lost") }
                        response
                    }
                    var creator = manager()
                    fun reader() = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    var participant = reader()
                    val originalProfile = SpaceChannelProfile("confidential-planning", category = "Private team")
                    val space = creator.create(SpaceProfile("Private channel metadata", visibility = visibility),
                        listOf(if (general) originalProfile else SpaceChannelProfile("general")))
                    creator.flush(space)
                    if (visibility == SpaceVisibility.PUBLIC) {
                        participant.openPublic(space); participant.joinPublic(space); participant.flush(space)
                    } else {
                        val invitation = creator.invite(space); creator.flush(space)
                        participant.join(invitation); participant.flush(space)
                        creator.admitPending(space); creator.flush(space); participant.refresh(space)
                    }
                    creator.refresh(space)
                    val channel = if (general) creator.community(space).channel else {
                        val created = if (visibility == SpaceVisibility.PUBLIC) creator.createPrivateChannel(space, originalProfile, setOf(member.publicKey))
                            else creator.createChannel(space, originalProfile)
                        creator.flush(space); participant.refresh(space); participant.selectChannel(space, created); participant.flush(space)
                        creator.admitPending(space); creator.flush(space); participant.refresh(space, created)
                        created
                    }
                    val message = participant.send(space, "The same private history survives renaming", channel); participant.flush(space)
                    participant.selectChannel(space, channel); participant.saveDraft(space, "Keep this private draft", channel)
                    creator.refresh(space, channel)
                    assertEquals(originalProfile, creator.channels(space).single { it.id == channel }.profile)
                    assertEquals(originalProfile, participant.channels(space).single { it.id == channel }.profile)
                    assertTrue(creator.channels(space).single { it.id == channel }.metadataReady)
                    val renamed = originalProfile.copy(name = "confidential-roadmap", category = "Planning together")
                    loseReply = true
                    val edit = creator.editChannel(space, channel, renamed)
                    try { creator.flush(space); fail("The accepted original reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelEdit(space, channel)?.status)
                    creator = manager(); creator.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.channelEdit(space, channel)?.status)
                    assertEquals(edit, creator.channelEdit(space, channel)?.id)
                    assertEquals(2, attempts.size); assertEquals(attempts[0], attempts[1])
                    val original = SaitoEnvelope.decode(attempts[0])
                    assertFalse(original.body.has("channel_profile"))
                    val signedMetadata = SaitoEnvelope.decode(String(java.util.Base64.getDecoder().decode(original.body.get("channel_metadata").asString), Charsets.UTF_8))
                    assertFalse(signedMetadata.body.has("profile")); assertTrue(signedMetadata.body.has("ciphertext"))
                    assertEquals("channel-profile-edit", signedMetadata.body.get("content_kind").asString)
                    try { creator.editChannel(space, channel, renamed.copy(kind = SpaceChannelKind.ARTICLE)); fail("Editing cannot change channel kind") }
                    catch (_: IllegalArgumentException) { }
                    participant.refresh(space, channel); participant = reader()
                    assertEquals(renamed, participant.channels(space).single { it.id == channel }.profile)
                    assertEquals(renamed, creator.channels(space).single { it.id == channel }.profile)
                    assertEquals(channel, participant.community(space).channel)
                    assertEquals("Keep this private draft", participant.draft(space, channel))
                    assertEquals(listOf(message), participant.timeline(space, channel = channel).map { it.id })
                    assertEquals(listOf(message), creator.timeline(space, channel = channel).map { it.id })
                    try { participant.editChannel(space, channel, originalProfile); fail("An ordinary member cannot edit private metadata") }
                    catch (_: IllegalArgumentException) { }
                    val forged = original.body.deepCopy().apply { addProperty("operation_id", "a".repeat(64)) }
                    val unauthorized = SaitoTransaction.create(member, forged, clock.millis(), listOf(service.publicKey))
                    assertEquals("rejection", SaitoEnvelope.decode(remote.exchange(unauthorized.envelope())).body.get("stream").asString)
                    creator.send(space, "New private messages still work", channel); creator.flush(space); participant.refresh(space, channel)
                    assertEquals(listOf("The same private history survives renaming", "New private messages still work"), participant.messages(space, channel))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aWithdrawnAdminRequestDoesNotBlockAnEligibleMemberWhenControlsChangedElsewhere() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-withdrawn-request-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-withdrawn-request-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-withdrawn-request-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-withdrawn-request-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Pending access follows current roles", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Selected active participants"), setOf(participant.publicKey)); creator.flush(space)
                        admin.refresh(space); admin.flush(space)
                        val demotion = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "set-role"); addProperty("space_id", space); addProperty("operation_id", "d".repeat(64))
                            addProperty("control_id", requireNotNull(creator.channelCreation(space)).id)
                            addProperty("member", member.publicKey); addProperty("role", "member")
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("control", SaitoEnvelope.decode(remote.exchange(demotion.envelope())).body.get("stream").asString)
                        reader.refresh(space); reader.flush(space)
                        // The normal owner worker has not performed a UI refresh since the external change.
                        creator.admitPending(space); creator.flush(space); reader.refresh(space); admin.refresh(space)
                        assertTrue("The eligible request after an obsolete request must complete", reader.channels(space).any { it.id == channel })
                        assertFalse("The withdrawn admin must not acquire channel keys", admin.channels(space).any { it.id == channel })
                        assertEquals(nl.davidengelen.redsquare.spaces.SpaceRole.MEMBER, creator.members(space).single { it.publicKey == member.publicKey }.role)
                        creator.send(space, "Current permissions govern admission", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("Current permissions govern admission"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anUnreadyPrivateChannelDoesNotBlockOtherAdmissionsAndItsRequestSurvives() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-deferred-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-deferred-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-deferred-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-deferred-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("One unavailable creator does not pause a community", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                        val unready = admin.createPrivateChannel(space, SpaceChannelProfile("Waiting for its creator"), emptySet()); admin.flush(space)
                        creator.refresh(space); creator.flush(space)
                        // The admin now goes offline with the only keys to the first requested channel.
                        val available = creator.createPrivateChannel(space, SpaceChannelProfile("Available owner-managed channel"), setOf(participant.publicKey)); creator.flush(space)
                        reader.refresh(space); reader.flush(space)
                        creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        assertTrue("A later request for a ready channel must be admitted", reader.channels(space).any { it.id == available })
                        assertEquals("Available owner-managed channel", reader.channels(space).single { it.id == available }.profile.name)
                        // Returning later must still process the earlier request exactly once.
                        admin.refresh(space); admin.flush(space); admin.admitPending(space); admin.flush(space); creator.refresh(space)
                        creator.admitPending(space); creator.flush(space)
                        assertTrue("The deferred request must survive until its key holder returns", creator.channels(space).any { it.id == unready })
                        assertEquals("Waiting for its creator", creator.channels(space).single { it.id == unready }.profile.name)
                        creator.send(space, "Both channels keep moving", available); creator.flush(space)
                        reader.selectChannel(space, available); reader.refresh(space)
                        assertEquals(listOf("Both channels keep moving"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aNewlyAdmittedOwnerCanAdmitAnotherMemberWithoutOpeningTheChannelListFirst() = runBlocking<Unit> {
        val participant = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-admin-profile-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-admin-profile-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-admin-profile-admin.sqlite").absolutePath).use { adminStore ->
                    SqliteClientFixture(files.newFile("restricted-admin-profile-participant.sqlite").absolutePath).use { participantStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val admin = SpaceClient(member, "fixture-node", service.publicKey, adminStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(participant, "fixture-node", service.publicKey, participantStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Administration does not require visiting a list", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(admin, reader)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); admin.refresh(space)
                        val profile = SpaceChannelProfile("Admin-created planning")
                        val channel = admin.createPrivateChannel(space, profile, setOf(participant.publicKey)); admin.flush(space)
                        creator.refresh(space); creator.flush(space)
                        reader.refresh(space); reader.flush(space)
                        admin.admitPending(space); admin.flush(space)
                        // Refresh installs the actual owner Welcome. It must not require a UI list read.
                        creator.refresh(space); creator.admitPending(space); creator.flush(space)
                        reader.refresh(space)
                        assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                        creator.send(space, "The owner can continue administration", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("The owner can continue administration"), reader.messages(space))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun restrictedChannelControlsAndRequestsRequireTheCorrectAudienceAndEncryptedScope() = runBlocking<Unit> {
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-boundary-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-boundary-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-boundary-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("restricted-boundary-other.sqlite").absolutePath).use { otherStore ->
                        lateinit var creation: SaitoTransaction
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") creation = original
                            remote.exchange(original.envelope())
                        }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val excluded = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Receiving permissions are authoritative", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        for (client in listOf(reader, excluded)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space)
                        val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Selected readers only"), setOf(member.publicKey)); creator.flush(space)
                        val accepted = SaitoEnvelope.decode(creation.envelope())
                        fun rejected(original: SaitoTransaction) {
                            val response = remote.exchange(original.envelope())
                            assertEquals("rejection", SaitoEnvelope.decode(response).body.get("stream").asString)
                            assertEquals("Rejected originals keep their disposition", response, remote.exchange(original.envelope()))
                        }
                        val foreignMetadata = accepted.body.deepCopy().apply {
                            addProperty("channel_id", "a".repeat(64)); addProperty("operation_id", "b".repeat(64)); addProperty("control_id", creation.id)
                        }
                        rejected(SaitoTransaction.create(owner, foreignMetadata, clock.millis(), listOf(service.publicKey)))
                        val invalidAudience = accepted.body.deepCopy().apply {
                            addProperty("channel_id", "c".repeat(64)); addProperty("operation_id", "d".repeat(64)); addProperty("control_id", creation.id)
                            addProperty("allowed_members", "This must be a member list")
                        }
                        rejected(SaitoTransaction.create(owner, invalidAudience, clock.millis(), listOf(service.publicKey)))
                        val scope = SpaceCrypto.Scope(service.publicKey, space, channel, accepted.body.get("key_generation").asString)
                        val packageData = SpaceCrypto.device(other.publicKey, "7".repeat(64)).keyPackage(scope, other)
                        val unauthorized = SaitoTransaction.create(other, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "join-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                            addProperty("control_id", creation.id); addProperty("operation_id", "e".repeat(64))
                            add("key_package", JsonObject().apply {
                                addProperty("wallet", other.publicKey); addProperty("device", packageData.deviceId)
                                addProperty("package", packageData.packageBytes); addProperty("signer", packageData.signer)
                                addProperty("signature", packageData.walletSignature)
                            })
                        }, clock.millis(), listOf(service.publicKey))
                        rejected(unauthorized)
                        excluded.refresh(space); creator.refresh(space)
                        assertFalse(excluded.channels(space).any { it.id == channel })
                        assertEquals(2, creator.channels(space).size)
                        assertTrue(creator.community(space).canSend)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun selectedMembersReadAPrivateChannelInsideAPublicSpaceWithoutExposingItsNameToOthers() = runBlocking<Unit> {
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("restricted-channel-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("restricted-channel-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("restricted-channel-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("restricted-channel-other.sqlite").absolutePath).use { otherStore ->
                        val controls = mutableListOf<String>()
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            val response = remote.exchange(original.envelope())
                            if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") controls.add(original.envelope())
                            response
                        }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val excluded = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Public with selected private conversations", visibility = SpaceVisibility.PUBLIC))
                        creator.flush(space)
                        for (client in listOf(reader, excluded)) { client.openPublic(space); client.joinPublic(space); client.flush(space) }
                        creator.refresh(space)
                        val general = creator.community(space).channel
                        creator.saveDraft(space, "Public writing survives a private channel", general)
                        val profile = SpaceChannelProfile("Private planning room", category = "Planning")
                        val channel = creator.createPrivateChannel(space, profile, setOf(member.publicKey))
                        creator.flush(space)
                        assertEquals(profile, creator.channels(space).single { it.id == channel }.profile)
                        assertEquals(SpaceVisibility.PRIVATE, creator.channels(space).single { it.id == channel }.visibility)
                        assertFalse("The control stream must not expose the private channel name", controls.single().contains("Private planning room"))
                        reader.refresh(space); excluded.refresh(space)
                        assertFalse(excluded.channels(space).any { it.id == channel })
                        try { excluded.selectChannel(space, channel); fail("Other Space members cannot request a restricted channel") }
                        catch (_: IllegalArgumentException) { }
                        reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                        creator.send(space, "Only admitted readers can decrypt this planning message", channel); creator.flush(space)
                        reader.selectChannel(space, channel); reader.refresh(space)
                        assertEquals(listOf("Only admitted readers can decrypt this planning message"), reader.messages(space))
                        excluded.refresh(space)
                        assertEquals(listOf(general), excluded.channels(space).map { it.id })
                        assertEquals("Public writing survives a private channel", creator.draft(space, general))
                        assertEquals(general, creator.community(space).channel)
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun uncertainChannelCreationRecoversTheSameOriginalAndOnlyShowsAcceptedChannels() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-creation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-creation-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-creation-reader.sqlite").absolutePath).use { readerStore ->
                    var loseReply = true
                    val delivered = mutableListOf<String>()
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "create-channel") {
                            delivered.add(original.envelope())
                            if (loseReply) { loseReply = false; throw java.io.IOException("Owned accepted channel creation reply lost") }
                        }
                        response
                    }
                    val creator = ownerClient()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Recoverable new channels", visibility = SpaceVisibility.PUBLIC))
                    creator.flush(space); reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "General draft survives creating a channel", general)
                    assertNull(creator.channelCreation(space))
                    val profile = SpaceChannelProfile("reading room", SpaceChannelKind.ARTICLE, "Knowledge")
                    val channel = creator.createChannel(space, profile)
                    val queued = requireNotNull(creator.channelCreation(space))
                    assertEquals(channel, queued.channelId); assertEquals(profile, queued.profile)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, queued.status)
                    assertEquals(listOf(general), creator.channels(space).map { it.id })
                    try { creator.flush(space); fail("The accepted creation reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelCreation(space)?.status)
                    assertEquals(listOf(general), creator.channels(space).map { it.id })
                    reader.refresh(space)
                    assertEquals(profile, reader.channels(space).single { it.id == channel }.profile)
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.channelCreation(space)?.status)
                    restored.flush(space)
                    assertEquals(queued.id, restored.channelCreation(space)?.id)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelCreation(space)?.status)
                    assertEquals(2, delivered.size); assertEquals(delivered[0], delivered[1])
                    assertEquals(profile, restored.channels(space).single { it.id == channel }.profile)
                    assertEquals(general, restored.community(space).channel)
                    assertEquals("General draft survives creating a channel", restored.draft(space, general))
                    try { reader.createChannel(space, SpaceChannelProfile("Unapproved structure")); fail("Members cannot create channels") }
                    catch (_: IllegalArgumentException) { }
                    assertNull(reader.channelCreation(space))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anUncertainChannelEditRetainsItsOriginalAndOnlyProjectsConfirmedMetadata() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-edit-intent-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-edit-intent-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-edit-intent-reader.sqlite").absolutePath).use { readerStore ->
                    var loseReply = true
                    val delivered = mutableListOf<String>()
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-channel") {
                            delivered.add(original.envelope())
                            if (loseReply) { loseReply = false; throw java.io.IOException("Owned accepted channel edit reply lost") }
                        }
                        response
                    }
                    val creator = ownerClient()
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                    val oldProfile = SpaceChannelProfile("workshop", category = "Conversations")
                    val space = creator.create(SpaceProfile("Honest channel edit receipts", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("general"), oldProfile)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.channels(space).single { it.profile == oldProfile }.id
                    val general = creator.community(space).channel
                    creator.saveDraft(space, "A workshop draft independent of its label", channel)
                    creator.saveDraft(space, "A separate general draft", general)
                    val renamed = SpaceChannelProfile("builders", category = "Projects")
                    val original = creator.editChannel(space, channel, renamed)
                    assertEquals(original, creator.channelEdit(space, channel)?.id)
                    assertEquals(renamed, creator.channelEdit(space, channel)?.profile)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.channelEdit(space, channel)?.status)
                    assertEquals(oldProfile, creator.channels(space).single { it.id == channel }.profile)
                    assertFalse(creator.community(space).canManage)
                    try { creator.flush(space); fail("The accepted edit reply is deliberately lost") } catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelEdit(space, channel)?.status)
                    assertEquals(oldProfile, creator.channels(space).single { it.id == channel }.profile)
                    reader.refresh(space, channel)
                    assertEquals(renamed, reader.channels(space).single { it.id == channel }.profile)
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.channelEdit(space, channel)?.status)
                    restored.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(original, restored.channelEdit(space, channel)?.id)
                    assertEquals(2, delivered.size); assertEquals(delivered[0], delivered[1])
                    assertEquals(renamed, restored.channels(space).single { it.id == channel }.profile)
                    assertNull(restored.channelEdit(space, general))
                    val another = renamed.copy(name = "build-together")
                    val rejectedEdit = restored.editChannel(space, channel, another)
                    val competing = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "set-listed"); addProperty("space_id", space); addProperty("control_id", original)
                        addProperty("listed", false); addProperty("operation_id", "e".repeat(64))
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(competing.envelope())).body.get("stream").asString)
                    restored.flush(space)
                    assertEquals(rejectedEdit, restored.channelEdit(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(renamed, restored.channels(space).single { it.id == channel }.profile)
                    restored.refresh(space)
                    val deliberate = restored.editChannel(space, channel, another)
                    assertNotEquals(rejectedEdit, deliberate); restored.flush(space); reader.refresh(space, channel)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelEdit(space, channel)?.status)
                    assertEquals(another, reader.channels(space).single { it.id == channel }.profile)
                    assertEquals("A workshop draft independent of its label", restored.draft(space, channel))
                    assertEquals("A separate general draft", restored.draft(space, general))
                    try { reader.editChannel(space, channel, oldProfile); fail("Members cannot prepare channel edits") }
                    catch (_: IllegalArgumentException) { }
                    assertNull(reader.channelEdit(space, channel))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun incompletePublicChannelEditsAreDurablyRejectedWithoutInterruptingWriting() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("invalid-channel-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("invalid-channel-edit-owner.sqlite").absolutePath).use { ownerStore ->
                var head: String? = null
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { response ->
                        val receipt = SaitoEnvelope.decode(response).body
                        if (receipt.get("stream")?.asString == "control") head = SaitoEnvelope.decode(receipt.get("event").asString).id
                    }
                }
                val space = creator.create(SpaceProfile("Invalid metadata does not block writing", visibility = SpaceVisibility.PUBLIC))
                creator.flush(space)
                val channel = creator.community(space).channel
                val queued = creator.send(space, "Valid writing survives an invalid edit")
                for ((index, invalid) in listOf(null, JsonPrimitive("This is not a channel profile object")).withIndex()) {
                    val original = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("kind", "edit-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                        addProperty("control_id", requireNotNull(head)); addProperty("operation_id", (index + 1).toString().repeat(64))
                        if (invalid != null) add("channel_profile", invalid)
                    }, clock.millis(), listOf(service.publicKey))
                    val rejected = remote.exchange(original.envelope())
                    assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                    assertEquals("Invalid original keeps its terminal disposition", rejected, remote.exchange(original.envelope()))
                }
                creator.flush(space); creator.refresh(space)
                assertTrue(creator.community(space).canSend)
                assertEquals("general", creator.channels(space).single().profile.name)
                assertEquals(listOf(queued), creator.timeline(space).map { it.id })
            }
        }
    }

    @Test(timeout = 45_000) fun authorizedPublicChannelEditsConvergeWithoutChangingIdentityHistoryOrDrafts() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("channel-edit-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("channel-edit-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("channel-edit-member.sqlite").absolutePath).use { memberStore ->
                    var head: String? = null
                    fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { response ->
                        val receipt = SaitoEnvelope.decode(response).body
                        if (receipt.get("stream")?.asString == "control") head = SaitoEnvelope.decode(receipt.get("event").asString).id
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    fun reader() = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val first = reader()
                    val originalProfile = SpaceChannelProfile("workshop", category = "Conversations")
                    val space = creator.create(SpaceProfile("A community with editable channels", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("general"), originalProfile))
                    creator.flush(space); first.openPublic(space); first.joinPublic(space); first.flush(space); creator.refresh(space)
                    val channel = creator.channels(space).single { it.profile == originalProfile }.id
                    val message = creator.send(space, "This history belongs to the same channel", channel); creator.flush(space)
                    first.refresh(space, channel); first.selectChannel(space, channel)
                    first.saveDraft(space, "A draft survives channel renaming", channel)
                    assertEquals(originalProfile, first.channels(space).single { it.id == channel }.profile)
                    var operation = 0
                    fun edit(actor: SaitoWallet, profile: SpaceChannelProfile, reference: String = requireNotNull(head)) =
                        SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("kind", "edit-channel"); addProperty("space_id", space); addProperty("channel_id", channel)
                            addProperty("control_id", reference); addProperty("operation_id", (++operation).toString(16).padStart(64, '0'))
                            add("channel_profile", profile.json())
                        }, clock.millis(), listOf(service.publicKey))
                    val unauthorized = edit(member, SpaceChannelProfile("An ordinary member cannot rename this"))
                    val rejected = deliver(unauthorized)
                    assertEquals("rejection", SaitoEnvelope.decode(rejected).body.get("stream").asString)
                    assertEquals(rejected, deliver(unauthorized))
                    val renamed = SpaceChannelProfile("build-together", category = "Projects")
                    val ownerEdit = edit(owner, renamed)
                    val accepted = deliver(ownerEdit)
                    assertEquals("An owner can edit an existing public channel", "control", SaitoEnvelope.decode(accepted).body.get("stream").asString)
                    assertEquals(accepted, deliver(ownerEdit))
                    first.refresh(space, channel)
                    assertEquals("Accepted metadata replaces a previously cached channel label", renamed, first.channels(space).single { it.id == channel }.profile)
                    val restored = reader()
                    assertEquals(channel, restored.community(space).channel)
                    assertEquals("A draft survives channel renaming", restored.draft(space, channel))
                    assertEquals(listOf(message), restored.timeline(space, channel = channel).map { it.id })
                    creator.refresh(space); creator.setAdministrator(space, member.publicKey, true); creator.flush(space); restored.refresh(space)
                    val old = edit(owner, SpaceChannelProfile("An older concurrent label", category = "Projects"))
                    val finalProfile = SpaceChannelProfile("builders", category = "Community")
                    val administratorEdit = edit(member, finalProfile)
                    assertEquals("control", SaitoEnvelope.decode(deliver(administratorEdit)).body.get("stream").asString)
                    val stale = deliver(old)
                    assertEquals("A reversed stale change must not overwrite the accepted label", "rejection", SaitoEnvelope.decode(stale).body.get("stream").asString)
                    assertEquals(stale, deliver(old))
                    val changeKind = edit(owner, finalProfile.copy(kind = SpaceChannelKind.ANNOUNCEMENT))
                    assertEquals("Metadata editing cannot silently change contribution rules", "rejection", SaitoEnvelope.decode(deliver(changeKind)).body.get("stream").asString)
                    creator.refresh(space, channel); restored.refresh(space, channel)
                    assertEquals(finalProfile, creator.channels(space).single { it.id == channel }.profile)
                    assertEquals(finalProfile, restored.channels(space).single { it.id == channel }.profile)
                    assertEquals("A draft survives channel renaming", restored.draft(space, channel))
                    assertEquals(listOf(message), creator.timeline(space, channel = channel).map { it.id })
                }
            }
        }
    }
}

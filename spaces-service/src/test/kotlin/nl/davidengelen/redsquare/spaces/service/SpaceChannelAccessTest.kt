package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceChannelAccessTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val departing = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val remaining = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun regrantCannotReviveTheDiscardedFirstAdmissionRequest() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("grant-term-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("grant-term-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("grant-term-member.sqlite").absolutePath).use { memberStore ->
                    val requests = mutableListOf<String>()
                    val admitted = mutableListOf<String>()
                    val manager = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val body = SaitoEnvelope.decode(original.envelope()).body
                        if (body.get("kind")?.asString == "admit") admitted.add(body.get("join").asString)
                        remote.exchange(original.envelope())
                    }
                    fun reader() = SpaceClient(departing, "fixture-node", service.publicKey, memberStore, clock) { original ->
                        if (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "join-channel") requests.add(original.envelope())
                        remote.exchange(original.envelope())
                    }
                    var member = reader()
                    val space = manager.create(SpaceProfile("Fresh access terms", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    member.openPublic(space); member.joinPublic(space); member.flush(space); manager.refresh(space)
                    val channel = manager.createPrivateChannel(space, SpaceChannelProfile("No discarded admission"), setOf(departing.publicKey)); manager.flush(space)
                    member.refresh(space); member.flush(space)
                    assertEquals(1, requests.size)
                    manager.revokeChannelAccess(space, channel, departing.publicKey); manager.flush(space); member.refresh(space)
                    manager.grantChannelAccess(space, channel, departing.publicKey); manager.flush(space)
                    member.refresh(space); member.flush(space)
                    assertEquals(2, requests.size); assertNotEquals(requests[0], requests[1])
                    manager.admitPending(space); manager.flush(space)
                    member = reader(); member.refresh(space)
                    assertEquals(listOf(requests[1]), admitted)
                    member.send(space, "Only the current admission device receives keys", channel); member.flush(space); manager.refresh(space, channel)
                    assertEquals(listOf("Only the current admission device receives keys"), manager.messages(space, channel))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun explicitlyGrantedMemberCanJoinAndRegainPrivateAccessWithoutLosingOtherWork() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("grant-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("grant-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("grant-member.sqlite").absolutePath).use { memberStore ->
                    val originals = mutableListOf<SaitoTransaction>()
                    var loseGrantReply = false
                    fun creator() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val body = SaitoEnvelope.decode(original.envelope()).body
                        val response = remote.exchange(original.envelope())
                        if (body.get("kind")?.asString == "grant-channel-access") {
                            originals.add(original)
                            if (loseGrantReply) { loseGrantReply = false; throw IOException("Accepted grant reply lost") }
                        }
                        response
                    }
                    var manager = creator()
                    val reader = SpaceClient(departing, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = manager.create(SpaceProfile("Explicit private access", visibility = SpaceVisibility.PUBLIC)); manager.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); manager.refresh(space)
                    val general = reader.community(space).channel
                    reader.saveDraft(space, "A General draft survives private access changes", general)
                    val channel = manager.createPrivateChannel(space, SpaceChannelProfile("Selected writers only"), emptySet()); manager.flush(space)
                    reader.refresh(space)
                    assertFalse(reader.channels(space).any { it.id == channel })
                    loseGrantReply = true
                    val grant = manager.grantChannelAccess(space, channel, departing.publicKey)
                    try { manager.flush(space); fail("Accepted grant response is lost") } catch (_: IOException) { }
                    manager = creator(); manager.flush(space)
                    assertEquals(grant, manager.channelAccessChange(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, manager.channelAccessChange(space, channel)?.status)
                    assertEquals(2, originals.size)
                    assertEquals(originals[0].envelope(), originals[1].envelope())
                    reader.refresh(space); reader.flush(space); manager.admitPending(space); manager.flush(space); reader.refresh(space)
                    assertEquals("Selected writers only", reader.channels(space).single { it.id == channel }.profile.name)
                    reader.send(space, "Explicit grant is usable", channel); reader.flush(space); manager.refresh(space, channel)
                    assertEquals(listOf("Explicit grant is usable"), manager.messages(space, channel))
                    manager.revokeChannelAccess(space, channel, departing.publicKey); manager.flush(space); reader.refresh(space)
                    assertFalse(reader.channels(space).any { it.id == channel })
                    manager.grantChannelAccess(space, channel, departing.publicKey); manager.flush(space)
                    reader.refresh(space); reader.flush(space)
                    assertEquals("Cached private history remains readable while the key holder has not admitted the new device",
                        listOf("Explicit grant is usable"), reader.messages(space, channel))
                    manager.admitPending(space); manager.flush(space); reader.refresh(space)
                    reader.send(space, "Fresh admission after revocation works", channel); reader.flush(space); manager.refresh(space, channel)
                    assertEquals(listOf("Explicit grant is usable", "Fresh admission after revocation works"), manager.messages(space, channel))
                    assertEquals("A General draft survives private access changes", reader.draft(space, general))
                    assertEquals(general, reader.community(space).channel)
                }
            }
        }
    }

    @Test(timeout = 45_000) fun revocationRequiresAuthorityAndACompleteCorrectDeviceChangeAtTheReceivingBoundary() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("access-boundary-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("access-boundary-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("access-boundary-member.sqlite").absolutePath).use { memberStore ->
                    var checkBoundary = true
                    lateinit var admittedDevices: JsonObject
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val body = SaitoEnvelope.decode(original.envelope()).body
                        if (body.get("kind")?.asString == "admit") admittedDevices = body.getAsJsonObject("devices").deepCopy()
                        if (body.get("kind")?.asString == "revoke-channel-access" && checkBoundary) {
                            checkBoundary = false
                            fun rejected(actor: SaitoWallet, operation: String, edit: JsonObject.() -> Unit) {
                                val candidate = SaitoTransaction.create(actor, body.deepCopy().apply {
                                    addProperty("operation_id", operation.repeat(64)); edit()
                                }, clock.millis(), listOf(service.publicKey))
                                val response = remote.exchange(candidate.envelope())
                                assertEquals("rejection", SaitoEnvelope.decode(response).body.get("stream").asString)
                                assertEquals("Invalid originals keep their terminal disposition", response, remote.exchange(candidate.envelope()))
                            }
                            rejected(departing, "1") { }
                            rejected(owner, "2") { remove("devices") }
                            rejected(owner, "3") { addProperty("devices", "This is not a device map") }
                            rejected(owner, "4") { addProperty("epoch", get("epoch").asLong + 1) }
                            rejected(owner, "5") { addProperty("member", owner.publicKey) }
                            rejected(owner, "6") { add("devices", admittedDevices.deepCopy()) }
                        }
                        remote.exchange(original.envelope())
                    }
                    val reader = SpaceClient(departing, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Receiving authority and device changes", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Only valid key changes"), setOf(departing.publicKey)); creator.flush(space)
                    reader.refresh(space); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    creator.revokeChannelAccess(space, channel, departing.publicKey); creator.flush(space); reader.refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.channelAccessChange(space, channel)?.status)
                    assertFalse(reader.channels(space).any { it.id == channel })
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aRejectedKeyChangePreservesWorkingKeysAndOnlyAnExplicitFreshChangeRemovesAccess() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("access-rejection-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("access-rejection-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("access-rejection-member.sqlite").absolutePath).use { memberStore ->
                    var injectConflict = true
                    lateinit var rejectedOriginal: SaitoTransaction
                    lateinit var rejectedReceipt: String
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val body = SaitoEnvelope.decode(original.envelope()).body
                        if (body.get("kind")?.asString == "revoke-channel-access" && injectConflict) {
                            injectConflict = false
                            val competing = SaitoTransaction.create(owner, JsonObject().apply {
                                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                                addProperty("kind", "set-listed"); addProperty("space_id", body.get("space_id").asString)
                                addProperty("control_id", body.get("control_id").asString); addProperty("operation_id", "f".repeat(64)); addProperty("listed", false)
                            }, clock.millis(), listOf(service.publicKey))
                            assertEquals("control", SaitoEnvelope.decode(remote.exchange(competing.envelope())).body.get("stream").asString)
                            rejectedOriginal = original
                            remote.exchange(original.envelope()).also { rejectedReceipt = it }
                        } else remote.exchange(original.envelope())
                    }
                    val reader = SpaceClient(departing, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Rejection does not discard working keys", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Keep current access until accepted"), setOf(departing.publicKey)); creator.flush(space)
                    reader.refresh(space); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    val first = creator.revokeChannelAccess(space, channel, departing.publicKey); creator.flush(space)
                    assertEquals(first, creator.channelAccessChange(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.REJECTED, creator.channelAccessChange(space, channel)?.status)
                    reader.refresh(space); reader.send(space, "Current keys still work after a rejected change", channel); reader.flush(space)
                    creator.refresh(space, channel)
                    assertEquals(listOf("Current keys still work after a rejected change"), creator.messages(space, channel))
                    val deliberate = creator.revokeChannelAccess(space, channel, departing.publicKey)
                    assertNotEquals(first, deliberate); creator.flush(space); reader.refresh(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.channelAccessChange(space, channel)?.status)
                    assertFalse(reader.channels(space).any { it.id == channel })
                    try { reader.send(space, "Access is actually withdrawn now", channel); fail("The old member cannot prepare new channel writing") }
                    catch (_: IllegalArgumentException) { }
                    assertEquals("A rejected original never becomes accepted later", rejectedReceipt, remote.exchange(rejectedOriginal.envelope()))
                }
            }
        }
    }

    @Test(timeout = 60_000) fun revokedRecipientsCannotDecryptCopiedFutureMessagesAndLostRepliesKeepTheSameKeyChange() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("access-rotation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("access-rotation-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("access-rotation-remaining.sqlite").absolutePath).use { remainingStore ->
                    val events = mutableListOf<SaitoTransaction>()
                    var head = ""
                    var loseReply = false
                    fun deliver(original: SaitoTransaction): String {
                        val response = remote.exchange(original.envelope())
                        if (SaitoEnvelope.decode(original.envelope()).body.get("request")?.asString == "event") events.add(original)
                        val receipt = SaitoEnvelope.decode(response).body
                        if (receipt.get("stream")?.asString == "control") head = SaitoEnvelope.decode(receipt.get("event").asString).id
                        return response
                    }
                    fun body(original: SaitoTransaction) = SaitoEnvelope.decode(original.envelope()).body
                    fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                        val response = deliver(original)
                        if (loseReply && body(original).get("kind")?.asString == "revoke-channel-access") {
                            loseReply = false; throw IOException("Owned accepted key-change reply lost")
                        }
                        response
                    }
                    val creator = ownerClient()
                    val reader = SpaceClient(remaining, "fixture-node", service.publicKey, remainingStore, clock) { deliver(it) }
                    val profile = SpaceProfile("Real key exclusion", visibility = SpaceVisibility.PUBLIC)
                    val space = creator.create(profile); creator.flush(space)
                    val general = creator.community(space).channel
                    fun referenceEvent(kind: String, operation: String, extra: JsonObject.() -> Unit) = SaitoTransaction.create(departing, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("operation_id", operation)
                        addProperty("control_id", head); extra()
                    }, clock.millis(), listOf(service.publicKey))
                    deliver(referenceEvent("join-public", "a".repeat(64)) {
                        addProperty("channel_id", general); addProperty("rules_token", profile.publicRulesToken)
                    })
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Changing real private access"), setOf(departing.publicKey, remaining.publicKey))
                    creator.flush(space)
                    val created = events.single { body(it).get("kind")?.asString == "create-channel" }
                    val scope = SpaceCrypto.Scope(service.publicKey, space, channel, body(created).get("key_generation").asString)
                    val referenceDevice = SpaceCrypto.device(departing.publicKey, "b".repeat(64))
                    val keyPackage = referenceDevice.keyPackage(scope, departing)
                    deliver(referenceEvent("join-channel", "c".repeat(64)) {
                        addProperty("channel_id", channel)
                        add("key_package", JsonObject().apply {
                            addProperty("wallet", departing.publicKey); addProperty("device", keyPackage.deviceId)
                            addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                            addProperty("signature", keyPackage.walletSignature)
                        })
                    })
                    creator.admitPending(space); creator.flush(space)
                    fun admissions() = events.filter { body(it).get("kind")?.asString == "admit" }
                    fun bindings(original: SaitoTransaction) = body(original).getAsJsonObject("devices").entrySet().associate { it.key to it.value.asString }
                    val first = admissions().single()
                    var reference = referenceDevice.join(scope, body(first).get("welcome").asString, bindings(first))
                    reader.refresh(space); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                    val second = admissions().last()
                    assertEquals(2, admissions().size)
                    val ownerCredential = reference.bindings.keys.single { it.split(':')[1] == owner.publicKey }
                    reference = reference.receiveCommit(body(second).get("commit").asString, ownerCredential, bindings(second))
                    creator.send(space, "Before access is withdrawn", channel); creator.flush(space)
                    fun lastMessage() = events.last { body(it).get("kind")?.asString == "content" && body(it).get("content_kind")?.asString == "message" }
                    val before = lastMessage()
                    assertEquals("Before access is withdrawn", reference.read(before.envelope()).text)
                    val copiedOldKeys = reference.save()
                    creator.saveDraft(space, "General writing survives a key change", general)
                    loseReply = true
                    val original = creator.revokeChannelAccess(space, channel, departing.publicKey)
                    assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.channelAccessChange(space, channel)?.status)
                    try { creator.flush(space); fail("The accepted key-change reply is deliberately lost") } catch (_: IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.channelAccessChange(space, channel)?.status)
                    reader.refresh(space)
                    reader.send(space, "After access is withdrawn", channel); reader.flush(space)
                    val after = lastMessage()
                    val oldReader = SpaceCrypto.restore(copiedOldKeys)
                    try { oldReader.read(after.envelope()); fail("Even a copied accepted future ciphertext must remain unreadable") }
                    catch (_: SpaceCrypto.MissingKeys) { }
                    assertEquals("Copied past bytes cannot be recalled", "Before access is withdrawn", oldReader.read(before.envelope()).text)
                    reader.selectChannel(space, channel); reader.refresh(space)
                    assertEquals(listOf("Before access is withdrawn", "After access is withdrawn"), reader.messages(space))
                    val restored = ownerClient()
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, restored.channelAccessChange(space, channel)?.status)
                    restored.flush(space)
                    assertEquals(original, restored.channelAccessChange(space, channel)?.id)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, restored.channelAccessChange(space, channel)?.status)
                    val attempts = events.filter { body(it).get("kind")?.asString == "revoke-channel-access" }
                    assertEquals(2, attempts.size); assertEquals(attempts[0].envelope(), attempts[1].envelope())
                    assertTrue("Channel access is separate from Space membership", restored.members(space).any { it.publicKey == departing.publicKey })
                    assertEquals(general, restored.community(space).channel)
                    assertEquals("General writing survives a key change", restored.draft(space, general))
                }
            }
        }
    }
}

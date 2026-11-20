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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceVisibilityTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val newcomer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun closingRecoversTheSameAcceptedOriginalAndPrivateKeysAfterReopeningLocalStorage() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-closing-recovery-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-closing-recovery-reader.sqlite").absolutePath).use { readerStore ->
                val originals = mutableListOf<String>()
                var loseReply = false
                fun deliver(original: SaitoTransaction): String {
                    val changing = SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "set-visibility"
                    if (changing) originals += original.envelope()
                    val receipt = remote.exchange(original.envelope())
                    if (changing && loseReply) { loseReply = false; throw java.io.IOException("Owned accepted closing reply loss") }
                    return receipt
                }
                val ownerPath = files.newFile("visibility-closing-recovery-owner.sqlite").absolutePath
                val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { deliver(it) }
                lateinit var space: String
                lateinit var oldPublic: String
                lateinit var intent: SpaceClient.VisibilityChange
                SqliteClientFixture(ownerPath).use { ownerStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    space = creator.create(SpaceProfile("Recover closing with care", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    oldPublic = creator.community(space).channel
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    creator.send(space, "Keep the earlier public conversation", oldPublic); creator.flush(space); reader.refresh(space)
                    creator.saveDraft(space, "Keep the owner's older public draft", oldPublic)
                    loseReply = true
                    creator.changeVisibility(space, SpaceVisibility.PRIVATE)
                    intent = requireNotNull(creator.visibilityChange(space))
                    try { creator.flush(space); fail("The owned transport must lose the accepted closing reply") }
                    catch (_: java.io.IOException) { }
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.visibilityChange(space)?.status)
                    assertEquals(SpaceVisibility.PUBLIC, creator.profile(space).visibility)
                }
                SqliteClientFixture(ownerPath).use { ownerStore ->
                    val recovered = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    assertEquals(intent.id, recovered.visibilityChange(space)?.id)
                    assertEquals(intent.entryChannelId, recovered.visibilityChange(space)?.entryChannelId)
                    assertEquals(false, recovered.visibilityChange(space)?.listed)
                    assertEquals(recovered.visibilityChange(space), recovered.management(space).visibility)
                    assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, recovered.visibilityChange(space)?.status)
                    recovered.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, recovered.visibilityChange(space)?.status)
                    assertEquals(2, originals.size); assertEquals(originals[0], originals[1])
                    assertEquals(SpaceVisibility.PRIVATE, recovered.profile(space).visibility)
                    assertEquals("Keep the owner's older public draft", recovered.draft(space, oldPublic))
                    assertEquals(listOf("Keep the earlier public conversation"), recovered.messages(space, oldPublic))
                    reader.refresh(space, oldPublic); reader.flush(space); recovered.admitPending(space); recovered.flush(space)
                    recovered.send(space, "The recovered private keys still work", intent.entryChannelId); recovered.flush(space)
                    reader.refresh(space, intent.entryChannelId)
                    assertEquals(listOf("The recovered private keys still work"), reader.messages(space, intent.entryChannelId))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun anOlderPublicSpaceWithoutADirectoryProfileCanStillBecomePrivate() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-legacy-unlisted-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-legacy-unlisted-visitor.sqlite").absolutePath).use { visitorStore ->
                val old = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
                val oldKeys = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(old)
                val oldMetadata = oldKeys.publish(owner, "metadata", "d".repeat(64), "0".repeat(128), "An older public community", clock.millis())
                val genesis = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", old.spaceId); addProperty("kind", "create"); addProperty("operation_id", "e".repeat(64))
                    addProperty("visibility", "public"); addProperty("channel_id", old.channelId); addProperty("epoch", 0)
                    addProperty("metadata", java.util.Base64.getEncoder().encodeToString(oldMetadata.envelope().toByteArray(Charsets.UTF_8)))
                    add("devices", JsonObject().apply { oldKeys.bindings.forEach { (credential, signer) -> addProperty(credential, signer) } })
                }, clock.millis(), listOf(service.publicKey))
                val creation = remote.exchange(genesis.envelope())
                assertEquals("control", SaitoEnvelope.decode(creation).body.get("stream").asString)
                val entry = SpaceCrypto.Scope(service.publicKey, old.spaceId, "f".repeat(64))
                val keys = SpaceCrypto.device(owner.publicKey, "1".repeat(64)).create(entry)
                val metadata = keys.publish(owner, "channel-profile", "2".repeat(64), genesis.id, JsonObject().apply {
                    addProperty("version", 1); addProperty("channel_id", entry.channelId)
                    add("profile", SpaceChannelProfile("members-lobby").json())
                }.toString(), clock.millis())
                val close = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", old.spaceId); addProperty("kind", "set-visibility"); addProperty("operation_id", "3".repeat(64))
                    addProperty("control_id", genesis.id); addProperty("visibility", "private")
                    addProperty("channel_id", entry.channelId); addProperty("key_generation", entry.generation); addProperty("epoch", 0)
                    add("devices", JsonObject().apply { keys.bindings.forEach { (credential, signer) -> addProperty(credential, signer) } })
                    addProperty("channel_metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                }, clock.millis(), listOf(service.publicKey))
                val result = remote.exchange(close.envelope())
                assertEquals("control", SaitoEnvelope.decode(result).body.get("stream").asString)
                val received = SpaceJournal(old.spaceId, service.publicKey).apply { receive(creation); receive(result) }.snapshot
                assertEquals(SpaceVisibility.PRIVATE, received.visibility)
                assertEquals(entry.channelId, received.entryChannelId)
                assertEquals(result, remote.exchange(close.envelope()))
                val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                assertTrue(visitor.discover().items.isEmpty())
            }
        }
    }

    @Test(timeout = 45_000) fun anEarlierPublicCategoryCanBeRenamedPrivatelyAfterClosing() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-existing-category-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-existing-category-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-existing-category-member.sqlite").absolutePath).use { memberStore ->
                    val originals = mutableListOf<String>()
                    fun deliver(original: SaitoTransaction): String {
                        originals += original.envelope()
                        return remote.exchange(original.envelope())
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create(SpaceProfile("Existing public organization", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    val category = creator.createCategory(space, SpaceCategoryProfile("Public projects")); creator.flush(space)
                    val notes = creator.createChannel(space, SpaceChannelProfile("project-notes", category = "Public projects"), category); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    creator.changeVisibility(space, SpaceVisibility.PRIVATE); creator.flush(space)
                    val entry = requireNotNull(creator.visibilityChange(space)).entryChannelId
                    reader.refresh(space); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, entry)
                    creator.editCategory(space, category, SpaceCategoryProfile("Private future planning")); creator.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.categoryChange(space)?.status)
                    reader.refresh(space, entry)
                    assertEquals("Private future planning", reader.categories(space).single { it.id == category }.profile.name)
                    assertTrue(reader.categories(space).single { it.id == category }.isPrivate)
                    assertEquals("Private future planning", reader.channels(space).single { it.id == notes }.profile.category)
                    val renamed = originals.map { SaitoEnvelope.decode(it).body }.single { it.get("kind")?.asString == "edit-category" }
                    assertFalse(renamed.has("category_profile"))
                    assertFalse(renamed.toString().contains("Private future planning"))
                }
            }
        }
    }

    @Test(timeout = 45_000) fun newPrivateChannelsAndFreshInvitationsUseTheNewEntranceWithoutReplacingOlderWork() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-private-work-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-private-work-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-private-work-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("visibility-private-work-newcomer.sqlite").absolutePath).use { newcomerStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val invited = SpaceClient(newcomer, "fixture-node", service.publicKey, newcomerStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Private work after closing", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        val oldPublic = creator.community(space).channel
                        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                        reader.saveDraft(space, "Keep the older public draft", oldPublic)
                        creator.changeVisibility(space, SpaceVisibility.PRIVATE); creator.flush(space)
                        val entry = requireNotNull(creator.visibilityChange(space)).entryChannelId
                        reader.refresh(space, oldPublic); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, entry)
                        reader.saveDraft(space, "Keep the private lobby draft", entry)
                        creator.send(space, "Earlier members lobby conversation", entry); creator.flush(space); reader.refresh(space, entry)
                        val category = creator.createCategory(space, SpaceCategoryProfile("Team library")); creator.flush(space)
                        val notes = creator.createChannel(space, SpaceChannelProfile("team-notes", category = "Team library"), category); creator.flush(space)
                        reader.refresh(space, entry)
                        val discovered = reader.channels(space).single { it.id == notes }
                        assertEquals("team-notes", discovered.profile.name)
                        assertEquals("Team library", discovered.profile.category)
                        assertEquals(SpaceVisibility.PRIVATE, discovered.visibility)
                        assertEquals(category, discovered.categoryId)
                        reader.selectChannel(space, notes); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, notes)
                        creator.send(space, "Keep these notes private", notes); creator.flush(space); reader.refresh(space, notes)
                        assertEquals(listOf("Keep these notes private"), reader.messages(space, notes))
                        val invitation = creator.invite(space); creator.flush(space)
                        invited.join(invitation); invited.flush(space); creator.admitPending(space); creator.flush(space); invited.refresh(space, entry)
                        assertEquals(listOf("Earlier members lobby conversation"), invited.messages(space, entry))
                        assertEquals("team-notes", invited.channels(space).single { it.id == notes }.profile.name)
                        reader.refresh(space, entry)
                        reader.leave(space); reader.flush(space)
                        assertEquals(SpaceClient.ChangeStatus.ACCEPTED, reader.membershipChange(space)?.status)
                        assertFalse(reader.community(space).joined)
                        creator.refresh(space, entry); creator.flush(space); invited.refresh(space, entry)
                        assertFalse(creator.members(space).any { it.publicKey == member.publicKey })
                        val returningInvitation = creator.invite(space); creator.flush(space)
                        reader.join(returningInvitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, entry)
                        assertTrue(reader.community(space).joined)
                        assertFalse(reader.community(space).membershipRequested)
                        assertEquals(entry, reader.community(space).channel)
                        assertEquals("Keep the older public draft", reader.draft(space, oldPublic))
                        assertEquals("Keep the private lobby draft", reader.draft(space, entry))
                        assertEquals(listOf("Earlier members lobby conversation"), reader.messages(space, entry))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun publicEntryWorksForACreatorWithoutPrivateKeysAndAMemberWithAnOlderPrivateStore() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-entry-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-entry-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-entry-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("visibility-entry-public-owner.sqlite").absolutePath).use { publicOwnerStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val publicOwner = SpaceClient(owner, "fixture-node", service.publicKey, publicOwnerStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Public entrance, private memories")); creator.flush(space)
                        val oldRoot = creator.community(space).channel
                        val invitation = creator.invite(space); creator.flush(space)
                        reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        reader.saveDraft(space, "Keep this old private draft", oldRoot)
                        creator.changeVisibility(space, SpaceVisibility.PUBLIC); creator.flush(space); reader.refresh(space)
                        val entry = creator.channels(space).single { it.visibility == SpaceVisibility.PUBLIC }.id
                        assertEquals(entry, publicOwner.openPublic(space).channel)
                        assertTrue(publicOwner.community(space).joined)
                        assertEquals(listOf(entry), publicOwner.channels(space).map { it.id })
                        reader.leave(space); reader.flush(space)
                        reader.openPublic(space); reader.joinPublic(space); reader.flush(space)
                        assertTrue(reader.community(space).joined)
                        assertEquals(entry, reader.community(space).channel)
                        assertEquals(listOf(entry), reader.channels(space).map { it.id })
                        assertEquals("Keep this old private draft", reader.draft(space, oldRoot))
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun makingASpacePublicAddsAPublicEntryWithoutPublishingEarlierPrivateHistory() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("visibility-newcomer.sqlite").absolutePath).use { newcomerStore ->
                        var head = ""
                        var position = -1L
                        var operation = 0
                        fun deliver(original: SaitoTransaction) = remote.exchange(original.envelope()).also { receipt ->
                            val body = SaitoEnvelope.decode(receipt).body
                            if (body.get("stream")?.asString == "control" && body.get("position").asLong > position) {
                                position = body.get("position").asLong; head = original.id
                            }
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                        val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, newcomerStore, clock) { deliver(it) }
                        val profile = SpaceProfile("A community with a new public lobby")
                        val space = creator.create(profile); creator.flush(space)
                        val privateChannel = creator.community(space).channel
                        val invitation = creator.invite(space); creator.flush(space)
                        reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        creator.send(space, "Earlier private conversation", privateChannel); creator.flush(space); reader.refresh(space)
                        val publicChannel = "f".repeat(64)
                        val publicProfile = profile.copy(visibility = SpaceVisibility.PUBLIC, listed = true)
                        fun change(actor: SaitoWallet) = SaitoTransaction.create(actor, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", "set-visibility"); addProperty("control_id", head)
                            addProperty("operation_id", (++operation).toString(16).padStart(64, '0'))
                            addProperty("visibility", "public"); add("public_profile", publicProfile.json())
                            addProperty("channel_id", publicChannel); addProperty("key_generation", "e".repeat(64)); addProperty("epoch", 0)
                            add("channel_profile", SpaceChannelProfile("public-lobby").json())
                            add("devices", JsonObject())
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("rejection", SaitoEnvelope.decode(deliver(change(member))).body.get("stream").asString)
                        val original = change(owner)
                        val receipt = deliver(original)
                        assertEquals("The owner can deliberately open a public lobby without changing earlier channel audiences", "control",
                            SaitoEnvelope.decode(receipt).body.get("stream").asString)
                        creator.refresh(space); reader.refresh(space, privateChannel)
                        assertEquals(SpaceVisibility.PUBLIC, creator.profile(space).visibility)
                        assertEquals(SpaceVisibility.PRIVATE, creator.channels(space).single { it.id == privateChannel }.visibility)
                        assertEquals(setOf(owner.publicKey, member.publicKey), creator.channels(space).single { it.id == privateChannel }.allowedMembers)
                        assertEquals(listOf("Earlier private conversation"), reader.messages(space, privateChannel))
                        assertEquals(listOf(space), visitor.discover().items.map { it.id })
                        assertTrue("Directory reading is not admission", visitor.communities().isEmpty())
                        visitor.openPublic(space)
                        assertEquals(listOf(publicChannel), visitor.channels(space).map { it.id })
                        assertFalse(visitor.community(space).joined)
                        visitor.joinPublic(space); visitor.flush(space); creator.refresh(space)
                        assertEquals(listOf(publicChannel), visitor.channels(space).map { it.id })
                        creator.send(space, "Still private after opening the lobby", privateChannel); creator.flush(space)
                        reader.refresh(space, privateChannel)
                        assertEquals(listOf("Earlier private conversation", "Still private after opening the lobby"), reader.messages(space, privateChannel))
                        creator.send(space, "Welcome to the public lobby", publicChannel); creator.flush(space); visitor.refresh(space, publicChannel)
                        assertEquals(listOf("Welcome to the public lobby"), visitor.messages(space, publicChannel))
                        try {
                            visitor.preview(invitation)
                            fail("A previous invitation cannot admit newcomers into earlier private groups")
                        } catch (denied: SpaceClient.ReadDenied) {
                            assertEquals(SpaceClient.ReadProblem.UNAVAILABLE, denied.problem)
                        }
                        assertEquals(receipt, deliver(original))
                        val publicInvitation = creator.invite(space)
                        fun exportedInvitation(link: String) = com.google.gson.JsonParser.parseString(
                            java.util.Base64.getUrlDecoder().decode(java.net.URI(link).rawFragment).toString(Charsets.UTF_8)).asJsonObject
                        assertEquals("A new invitation uses the current public entrance", publicChannel,
                            exportedInvitation(publicInvitation).getAsJsonObject("scope").get("channel").asString)
                        creator.flush(space)
                        val oldScope = exportedInvitation(invitation).getAsJsonObject("scope")
                        val oldGroup = SpaceCrypto.Scope(service.publicKey, space, privateChannel, oldScope.get("generation").asString)
                        val staleOperation = (++operation).toString(16).padStart(64, '0')
                        val nonce = ByteArray(12) { 7 }
                        val preview = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
                            init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(ByteArray(32) { 9 }, "AES"),
                                javax.crypto.spec.GCMParameterSpec(128, nonce))
                            updateAAD("SaitoSpacesPreview/v1:${oldGroup.groupId}:$staleOperation".toByteArray(Charsets.UTF_8))
                            java.util.Base64.getEncoder().encodeToString(nonce + doFinal(profile.json().toString().toByteArray(Charsets.UTF_8)))
                        }
                        val obsoleteInvitation = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", "invite"); addProperty("control_id", head)
                            addProperty("operation_id", staleOperation); addProperty("channel_id", privateChannel)
                            addProperty("invite_key", SaitoWallet.fromPrivateKey("0".repeat(63) + "5").publicKey)
                            addProperty("expires_at", clock.millis() + 604_800_000); addProperty("preview_format", "profile-v1")
                            addProperty("preview", preview)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("The receiving node rejects an invitation to the earlier private entrance", "rejection",
                            SaitoEnvelope.decode(deliver(obsoleteInvitation)).body.get("stream").asString)
                    }
                }
            }
        }
    }
    @Test(timeout = 45_000) fun openingACommunityRebindsPrivateNamesAndRecoversTheSameOriginalWithoutPublishingHistory() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-client-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-client-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-client-reader.sqlite").absolutePath).use { readerStore ->
                    SqliteClientFixture(files.newFile("visibility-client-visitor.sqlite").absolutePath).use { visitorStore ->
                        val visibilityOriginals = mutableListOf<String>()
                        var loseReply = false
                        fun deliver(original: SaitoTransaction): String {
                            val visibility = SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "set-visibility"
                            if (visibility) visibilityOriginals += original.envelope()
                            val receipt = remote.exchange(original.envelope())
                            if (visibility && loseReply) { loseReply = false; throw java.io.IOException("Owned accepted reply loss") }
                            return receipt
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, readerStore, clock) { deliver(it) }
                        val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, visitorStore, clock) { deliver(it) }
                        val space = creator.create(SpaceProfile("A community ready to open"), listOf(
                            SpaceChannelProfile("general"), SpaceChannelProfile("Planning old", category = "Behind the scenes")))
                        creator.flush(space)
                        val planning = creator.channels(space).single { it.profile.name == "Planning old" }.id
                        val invitation = creator.invite(space); creator.flush(space)
                        reader.join(invitation); reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space)
                        reader.selectChannel(space, planning); reader.flush(space)
                        creator.admitPending(space); creator.flush(space); reader.refresh(space, planning)
                        creator.send(space, "Private plan before opening", planning); creator.flush(space); reader.refresh(space, planning)
                        creator.selectChannel(space, planning); creator.saveDraft(space, "Preserve owner draft", planning)
                        reader.saveDraft(space, "Preserve member draft", planning)
                        loseReply = true
                        val original = creator.changeVisibility(space, SpaceVisibility.PUBLIC)
                        assertEquals(SpaceClient.ChangeStatus.QUEUED, creator.visibilityChange(space)?.status)
                        try { creator.flush(space); fail("The fixture must lose the first visibility reply") }
                        catch (_: java.io.IOException) { }
                        assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, creator.visibilityChange(space)?.status)
                        assertEquals(listOf(space), visitor.discover().items.map { it.id })
                        val recovered = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        recovered.flush(space); reader.refresh(space, planning)
                        assertEquals(SpaceClient.ChangeStatus.ACCEPTED, recovered.visibilityChange(space)?.status)
                        assertEquals(original, recovered.visibilityChange(space)?.id)
                        assertEquals(2, visibilityOriginals.size)
                        assertEquals(visibilityOriginals[0], visibilityOriginals[1])
                        val body = SaitoEnvelope.decode(visibilityOriginals.first()).body.toString()
                        assertFalse(body.contains("Planning old")); assertFalse(body.contains("Behind the scenes"))
                        assertEquals(planning, recovered.community(space).channel)
                        assertEquals("Preserve owner draft", recovered.draft(space, planning))
                        assertEquals("Preserve member draft", reader.draft(space, planning))
                        assertEquals("Behind the scenes", reader.channels(space).single { it.id == planning }.profile.category)
                        assertEquals(listOf("Private plan before opening"), reader.messages(space, planning))
                        visitor.openPublic(space)
                        assertTrue(visitor.channels(space).all { it.visibility == SpaceVisibility.PUBLIC })
                        assertTrue(visitor.categories(space).isEmpty())
                        visitor.joinPublic(space); visitor.flush(space); recovered.refresh(space, planning)
                        recovered.send(space, "Private plan after opening", planning); recovered.flush(space); reader.refresh(space, planning)
                        assertEquals(listOf("Private plan before opening", "Private plan after opening"), reader.messages(space, planning))
                        assertFalse(visitor.channels(space).any { it.id == planning })
                        assertTrue(visitor.categories(space).isEmpty())
                    }
                }
            }
        }
    }
    @Test(timeout = 45_000) fun legacyPrivateChannelKeepsItsImmutableKindWhenItsMetadataAudienceBecomesIndependent() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-legacy-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-legacy-owner.sqlite").absolutePath).use { ownerStore ->
                var head = ""
                var position = -1L
                fun deliver(original: SaitoTransaction) = remote.exchange(original.envelope()).also { receipt ->
                    val body = SaitoEnvelope.decode(receipt).body
                    if (body.get("stream")?.asString == "control" && body.get("position").asLong > position) {
                        position = body.get("position").asLong; head = original.id
                    }
                }
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                val space = creator.create(SpaceProfile("An older private channel")); creator.flush(space)
                val general = creator.community(space).channel
                val rootKeys = ownerStore.transaction {
                    val state = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/state")).toString(Charsets.UTF_8)).asJsonObject
                    SpaceCrypto.restore(java.util.Base64.getDecoder().decode(state.get("crypto").asString))
                }
                val scope = SpaceCrypto.Scope(service.publicKey, space, "b".repeat(64), "c".repeat(64))
                val keys = SpaceCrypto.device(owner.publicKey, "d".repeat(64)).create(scope)
                val profile = SpaceChannelProfile("Legacy writing", SpaceChannelKind.ARTICLE)
                val profileJson = JsonObject().apply {
                    addProperty("version", 1); addProperty("name", profile.name); addProperty("kind", "article"); addProperty("category", "")
                }
                val metadata = rootKeys.publish(owner, "channel-profile", "f".repeat(64), head, JsonObject().apply {
                    addProperty("version", 1); addProperty("channel_id", scope.channelId); add("profile", profileJson)
                }.toString(), clock.millis())
                // Older clients authenticated the immutable kind inside this encrypted
                // metadata instead of also declaring channel_kind in the public control.
                val legacy = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("kind", "create-channel"); addProperty("control_id", head)
                    addProperty("operation_id", "e".repeat(64)); addProperty("channel_id", scope.channelId)
                    addProperty("visibility", "private"); addProperty("key_generation", scope.generation); addProperty("epoch", 0)
                    addProperty("admin_posts_only", false)
                    add("devices", JsonObject().apply { keys.bindings.forEach { (credential, signer) -> addProperty(credential, signer) } })
                    addProperty("metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                }, clock.millis(), listOf(service.publicKey))
                assertEquals("control", SaitoEnvelope.decode(deliver(legacy)).body.get("stream").asString)
                creator.refresh(space)
                // The owned legacy client has these local group keys. Seed only its
                // declared local-store input; assertions use the client interface.
                ownerStore.transaction {
                    put("$space/channels/${scope.channelId}/state", JsonObject().apply {
                        add("scope", JsonObject().apply {
                            addProperty("service", service.publicKey); addProperty("space", space)
                            addProperty("channel", scope.channelId); addProperty("generation", scope.generation)
                        })
                        addProperty("crypto", java.util.Base64.getEncoder().encodeToString(keys.save()))
                        addProperty("joined", true); addProperty("content_cursor", 0)
                    }.toString().toByteArray(Charsets.UTF_8))
                }
                assertEquals(SpaceChannelKind.ARTICLE, creator.channels(space).single { it.id == scope.channelId }.profile.kind)
                creator.saveDraft(space, "Keep the older article draft", scope.channelId)
                creator.changeVisibility(space, SpaceVisibility.PUBLIC); creator.flush(space)
                val restored = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                val channel = restored.channels(space).single { it.id == scope.channelId }
                assertEquals("Legacy writing", channel.profile.name)
                assertEquals(SpaceChannelKind.ARTICLE, channel.profile.kind)
                assertEquals(SpaceVisibility.PRIVATE, channel.visibility)
                assertEquals(setOf(owner.publicKey), channel.allowedMembers)
                assertEquals("Keep the older article draft", restored.draft(space, scope.channelId))
                assertEquals(SpaceVisibility.PRIVATE, restored.channels(space).single { it.id == general }.visibility)
            }
        }
    }
    @Test(timeout = 45_000) fun legacyPublicCommunityUsesItsPublicChannelWithoutReclassifyingPrivateGeneral() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-legacy-public-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-legacy-public-visitor.sqlite").absolutePath).use { visitorStore ->
                SqliteClientFixture(files.newFile("visibility-legacy-public-creator.sqlite").absolutePath).use { publicOwnerStore ->
                    val space = "a".repeat(64)
                    val general = "b".repeat(64)
                    val scope = SpaceCrypto.Scope(service.publicKey, space, general, "c".repeat(64))
                    val keys = SpaceCrypto.device(owner.publicKey, "d".repeat(64)).create(scope)
                    val profile = SpaceProfile("An older public community", visibility = SpaceVisibility.PUBLIC)
                    val metadata = keys.publish(owner, "metadata", "f".repeat(64), "0".repeat(128), profile.json().toString(), clock.millis())
                    val creation = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", "create"); addProperty("operation_id", "1".repeat(64))
                        addProperty("visibility", "public"); add("public_profile", profile.json())
                        addProperty("channel_id", general); addProperty("key_generation", scope.generation); addProperty("epoch", 0)
                        addProperty("metadata", java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                        add("devices", JsonObject().apply { keys.bindings.forEach { (credential, signer) -> addProperty(credential, signer) } })
                        // The earlier public protocol left General private when these
                        // channel visibility/kind/metadata fields were not declared.
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(creation.envelope())).body.get("stream").asString)
                    val publicChannel = "e".repeat(64)
                    val channelCreation = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", "create-channel"); addProperty("operation_id", "2".repeat(64))
                        addProperty("control_id", creation.id); addProperty("channel_id", publicChannel)
                        addProperty("visibility", "public"); addProperty("key_generation", "3".repeat(64)); addProperty("epoch", 0)
                        add("channel_profile", SpaceChannelProfile("public-room").json())
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("control", SaitoEnvelope.decode(remote.exchange(channelCreation.envelope())).body.get("stream").asString)
                    // Legacy public communities could invite a member through their
                    // unrestricted private General even after adding a public channel.
                    // That already-valid signed control history must stay replayable.
                    val legacyInvitation = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", "invite"); addProperty("operation_id", "4".repeat(64))
                        addProperty("control_id", channelCreation.id)
                        addProperty("invite_key", SaitoWallet.fromPrivateKey("0".repeat(63) + "5").publicKey)
                        addProperty("expires_at", clock.millis() + 60_000)
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("An earlier General invitation remains valid without changing its old audience", "control",
                        SaitoEnvelope.decode(remote.exchange(legacyInvitation.envelope())).body.get("stream").asString)
                    val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    assertEquals(listOf(space), visitor.discover().items.map { it.id })
                    assertEquals(publicChannel, visitor.openPublic(space).channel)
                    assertEquals(listOf(publicChannel), visitor.channels(space).map { it.id })
                    assertFalse(visitor.community(space).joined)
                    assertTrue(visitor.communities().isEmpty())
                    val publicOwner = SpaceClient(owner, "fixture-node", service.publicKey, publicOwnerStore, clock) { remote.exchange(it.envelope()) }
                    assertEquals(publicChannel, publicOwner.openPublic(space).channel)
                    assertTrue(publicOwner.community(space).joined)
                    assertEquals(listOf(publicChannel), publicOwner.channels(space).map { it.id })
                    publicOwner.saveDraft(space, "Retain the public-channel draft", publicChannel)
                    val restored = SpaceClient(owner, "fixture-node", service.publicKey, publicOwnerStore, clock) { remote.exchange(it.envelope()) }
                    assertEquals("Retain the public-channel draft", restored.draft(space, publicChannel))
                }
            }
        }
    }
    @Test(timeout = 45_000) fun ownerCanOpenByLinkWithoutBrieflyListingTheCommunityInDiscover() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-unlisted-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-unlisted-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-unlisted-visitor.sqlite").absolutePath).use { visitorStore ->
                    val originals = mutableListOf<SaitoTransaction>()
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { originals += it; remote.exchange(it.envelope()) }
                    val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, visitorStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("Public by invitation or link")); creator.flush(space)
                    creator.changeVisibility(space, SpaceVisibility.PUBLIC, listed = false); creator.flush(space)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.visibilityChange(space)?.status)
                    val commands = originals.map { SaitoEnvelope.decode(it.envelope()).body }
                    val opening = commands.single { it.get("kind")?.asString == "set-visibility" }
                    assertFalse(opening.getAsJsonObject("public_profile").get("listed").asBoolean)
                    assertFalse("No later unlisting operation repairs a briefly public listing", commands.any { it.get("kind")?.asString == "set-listed" })
                    assertTrue("Discover never receives a listing the owner did not choose", visitor.discover().items.isEmpty())
                    assertEquals(SpaceVisibility.PUBLIC, creator.profile(space).visibility)
                    assertFalse(creator.profile(space).listed)
                    val opened = visitor.openPublicLink(creator.publicLink(space))
                    assertEquals(space, opened.id); assertFalse(opened.joined)
                    assertFalse(visitor.profile(space).listed)
                    visitor.joinPublic(space); visitor.flush(space)
                    assertTrue(visitor.community(space).joined)
                }
            }
        }
    }
    @Test(timeout = 45_000) fun closingAPublicCommunityAddsAPrivateEntranceAndStopsQueuedPublicContributions() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("visibility-private-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("visibility-private-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("visibility-private-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("visibility-private-visitor.sqlite").absolutePath).use { visitorStore ->
                        val originals = mutableListOf<SaitoTransaction>()
                        var head = ""
                        var position = -1L
                        fun deliver(original: SaitoTransaction) = remote.exchange(original.envelope()).also { receipt ->
                            originals += original
                            val body = SaitoEnvelope.decode(receipt).body
                            if (body.get("stream")?.asString == "control" && body.get("position").asLong > position) {
                                position = body.get("position").asLong; head = original.id
                            }
                        }
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                        val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                        val visitor = SpaceClient(newcomer, "fixture-node", service.publicKey, visitorStore, clock) { deliver(it) }
                        val space = creator.create(SpaceProfile("Close public rooms with care", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        val oldPublic = creator.community(space).channel
                        reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                        creator.send(space, "A previously public conversation", oldPublic); creator.flush(space)
                        reader.refresh(space, oldPublic); visitor.openPublic(space); visitor.refresh(space, oldPublic)
                        val queued = reader.send(space, "A public message queued before closing", oldPublic)
                        reader.saveDraft(space, "Preserve my separate public-channel draft", oldPublic)
                        creator.changeVisibility(space, SpaceVisibility.PRIVATE); creator.flush(space)
                        assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.visibilityChange(space)?.status)
                        val entry = requireNotNull(creator.visibilityChange(space)).entryChannelId
                        assertNotEquals(oldPublic, entry)
                        assertEquals(SpaceVisibility.PRIVATE, creator.profile(space).visibility)
                        assertFalse(creator.profile(space).listed)
                        assertTrue(visitor.discover().items.isEmpty())
                        assertEquals(listOf("A previously public conversation"), visitor.messages(space, oldPublic))
                        assertTrue(creator.channels(space).single { it.id == oldPublic }.archived)
                        assertEquals(SpaceVisibility.PRIVATE, creator.channels(space).single { it.id == entry }.visibility)
                        reader.flush(space); reader.refresh(space, oldPublic)
                        assertTrue(reader.timeline(space, channel = oldPublic).single { it.id == queued }.rejected)
                        assertEquals("Preserve my separate public-channel draft", reader.draft(space, oldPublic))
                        val reopen = SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", "archive-channel"); addProperty("control_id", head)
                            addProperty("operation_id", "9".repeat(64)); addProperty("channel_id", oldPublic); addProperty("archived", false)
                        }, clock.millis(), listOf(service.publicKey))
                        assertEquals("Public archives cannot become writable while the Space is private", "rejection",
                            SaitoEnvelope.decode(deliver(reopen)).body.get("stream").asString)
                        reader.flush(space); creator.admitPending(space); creator.flush(space); reader.refresh(space, entry)
                        creator.send(space, "Only current members read this", entry); creator.flush(space); reader.refresh(space, entry)
                        assertEquals(listOf("Only current members read this"), reader.messages(space, entry))
                        val encrypted = originals.map { SaitoEnvelope.decode(it.envelope()).body }.last {
                            it.get("kind")?.asString == "content" && it.get("content_kind")?.asString == "message"
                        }
                        assertTrue(encrypted.has("ciphertext")); assertFalse(encrypted.has("text"))
                        assertFalse(encrypted.toString().contains("Only current members read this"))
                        try { visitor.refresh(space, entry); fail("A public visitor cannot read the new private entrance") }
                        catch (denied: SpaceClient.ReadDenied) { assertEquals(SpaceClient.ReadProblem.UNAVAILABLE, denied.problem) }
                        assertEquals(listOf("A previously public conversation"), visitor.messages(space, oldPublic))
                        val invitation = creator.invite(space); creator.flush(space)
                        val exported = com.google.gson.JsonParser.parseString(java.util.Base64.getUrlDecoder()
                            .decode(java.net.URI(invitation).rawFragment).toString(Charsets.UTF_8)).asJsonObject
                        assertEquals(entry, exported.getAsJsonObject("scope").get("channel").asString)
                        assertEquals("Preserve my separate public-channel draft", reader.draft(space, oldPublic))
                    }
                }
            }
        }
    }
}

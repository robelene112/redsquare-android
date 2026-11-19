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

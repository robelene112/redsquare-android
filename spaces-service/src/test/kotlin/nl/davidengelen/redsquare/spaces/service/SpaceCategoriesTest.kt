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

class SpaceCategoriesTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test(timeout = 45_000) fun staffCanRenameACategoryWithoutLosingOrRelinkingItsChannels() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("categories-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("categories-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("categories-member.sqlite").absolutePath).use { memberStore ->
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
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create(SpaceProfile("Organized channels", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val category = "b".repeat(64)
                    fun original(actor: SaitoWallet, kind: String, name: String) = SaitoTransaction.create(actor, JsonObject().apply {
                        addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                        addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                        addProperty("operation_id", (++operation).toString(16).padStart(64, '0')); addProperty("category_id", category)
                        add("category_profile", JsonObject().apply { addProperty("name", name) })
                    }, clock.millis(), listOf(service.publicKey))
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(original(member, "create-category", "Projects"))).body.get("stream").asString)
                    val creation = original(owner, "create-category", "Projects")
                    val created = deliver(creation)
                    assertEquals("The owner can create a category with its own identity", "control", SaitoEnvelope.decode(created).body.get("stream").asString)
                    creator.refresh(space)
                    val first = creator.createChannel(space, SpaceChannelProfile("planning", category = "Projects")); creator.flush(space)
                    val second = creator.createChannel(space, SpaceChannelProfile("progress", category = "Projects")); creator.flush(space)
                    creator.send(space, "Retain the plan", first); creator.flush(space)
                    reader.refresh(space, first)
                    assertEquals("rejection", SaitoEnvelope.decode(deliver(original(member, "edit-category", "Member rewrite"))).body.get("stream").asString)
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space)
                    val edit = original(member, "edit-category", "Shared work")
                    assertEquals("control", SaitoEnvelope.decode(deliver(edit)).body.get("stream").asString)
                    assertEquals(created, deliver(creation))
                    creator.refresh(space); reader.refresh(space, first)
                    for (client in listOf(creator, reader)) {
                        assertEquals(setOf(first, second), client.channels(space).filter { it.profile.category == "Shared work" }.map { it.id }.toSet())
                        assertEquals(listOf("Retain the plan"), client.messages(space, first))
                    }
                }
            }
        }
    }
    @Test(timeout = 45_000) fun privateCategoryNamesStayEncryptedAndRenameOnlyForTheirActualAudience() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("private-category-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("private-category-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("private-category-member.sqlite").absolutePath).use { memberStore ->
                    val originals = mutableListOf<SaitoTransaction>()
                    var head = ""
                    var position = -1L
                    fun deliver(original: SaitoTransaction) = remote.exchange(original.envelope()).also { receipt ->
                        originals.add(original)
                        val body = SaitoEnvelope.decode(receipt).body
                        if (body.get("stream")?.asString == "control" && body.get("position").asLong > position) {
                            position = body.get("position").asLong; head = original.id
                        }
                    }
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { deliver(it) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { deliver(it) }
                    val space = creator.create(SpaceProfile("Private folders", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val channel = creator.createPrivateChannel(space, SpaceChannelProfile("Private planning"), setOf(member.publicKey)); creator.flush(space)
                    val category = "c".repeat(64)
                    fun categoryControl(kind: String, name: String, revision: String): SaitoTransaction {
                        val keys = ownerStore.transaction {
                            val data = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/channels/$channel/state")).toString(Charsets.UTF_8)).asJsonObject
                            SpaceCrypto.restore(java.util.Base64.getDecoder().decode(data.get("crypto").asString))
                        }
                        val metadata = keys.publish(owner, "category-profile", revision, head, JsonObject().apply {
                            addProperty("version", 1); addProperty("category_id", category)
                            add("profile", SpaceCategoryProfile(name).json())
                        }.toString(), clock.millis(), targetCategoryId = category)
                        return SaitoTransaction.create(owner, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                            addProperty("space_id", space); addProperty("kind", kind); addProperty("control_id", head)
                            addProperty("operation_id", revision); addProperty("category_id", category)
                            if (kind == "create-category") addProperty("default_group", channel)
                            add("category_metadata", JsonObject().apply {
                                addProperty(channel, java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                            })
                        }, clock.millis(), listOf(service.publicKey))
                    }
                    val first = categoryControl("create-category", "Confidential projects", "d".repeat(64))
                    assertEquals("A category can use its actual private audience without publishing its label", "control",
                        SaitoEnvelope.decode(deliver(first)).body.get("stream").asString)
                    creator.refresh(space)
                    creator.editChannel(space, channel, SpaceChannelProfile("Private planning", category = "Confidential projects")); creator.flush(space)
                    reader.refresh(space); reader.selectChannel(space, channel); reader.flush(space)
                    creator.admitPending(space); creator.flush(space); reader.refresh(space, channel)
                    val renamed = categoryControl("edit-category", "New confidential projects", "e".repeat(64))
                    assertEquals("control", SaitoEnvelope.decode(deliver(renamed)).body.get("stream").asString)
                    creator.refresh(space, channel); reader.refresh(space, channel)
                    for (client in listOf(creator, reader)) {
                        assertEquals("New confidential projects", client.channels(space).single { it.id == channel }.profile.category)
                        assertEquals(category, client.channels(space).single { it.id == channel }.categoryId)
                    }
                    assertEquals("control", SaitoEnvelope.decode(deliver(first)).body.get("stream").asString)
                    assertFalse(originals.any { SaitoEnvelope.decode(it.envelope()).body.toString().contains("Confidential projects") || SaitoEnvelope.decode(it.envelope()).body.toString().contains("New confidential projects") })
                }
            }
        }
    }

    @Test(timeout = 45_000) fun categoryEditingRecoversItsExactOriginalAndReachesOnlyTheCurrentChannelAudiences() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("category-api-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("category-api-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("category-api-first.sqlite").absolutePath).use { firstStore ->
                    SqliteClientFixture(files.newFile("category-api-second.sqlite").absolutePath).use { secondStore ->
                        val secondWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
                        val originals = mutableListOf<SaitoTransaction>()
                        var loseRename = true
                        fun creator() = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                            originals.add(original)
                            val reply = remote.exchange(original.envelope())
                            if (loseRename && SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString == "edit-category") {
                                loseRename = false
                                assertEquals("control", SaitoEnvelope.decode(reply).body.get("stream").asString)
                                throw java.io.IOException("Owned accepted rename reply lost")
                            }
                            reply
                        }
                        var author = creator()
                        val firstReader = SpaceClient(member, "fixture-node", service.publicKey, firstStore, clock) { remote.exchange(it.envelope()) }
                        val secondReader = SpaceClient(secondWallet, "fixture-node", service.publicKey, secondStore, clock) { remote.exchange(it.envelope()) }
                        val space = author.create(SpaceProfile("Separate teams", visibility = SpaceVisibility.PUBLIC)); author.flush(space)
                        for (reader in listOf(firstReader, secondReader)) { reader.openPublic(space); reader.joinPublic(space); reader.flush(space) }
                        author.refresh(space)
                        val first = author.createPrivateChannel(space, SpaceChannelProfile("First team", category = "Team projects"), setOf(member.publicKey)); author.flush(space)
                        val second = author.createPrivateChannel(space, SpaceChannelProfile("Second team", category = "Team projects"), setOf(secondWallet.publicKey)); author.flush(space)
                        val category = requireNotNull(author.channels(space).single { it.id == first }.categoryId)
                        assertEquals(category, author.channels(space).single { it.id == second }.categoryId)
                        for ((reader, channel) in listOf(firstReader to first, secondReader to second)) {
                            reader.refresh(space); reader.selectChannel(space, channel); reader.flush(space)
                            author.admitPending(space); author.flush(space); reader.refresh(space, channel)
                            assertEquals(listOf("Team projects"), reader.categories(space).map { it.profile.name })
                        }
                        val original = author.editCategory(space, category, SpaceCategoryProfile("Shared projects"))
                        assertEquals(SpaceClient.ChangeStatus.QUEUED, author.categoryChange(space)?.status)
                        try { author.flush(space); fail("The actual accepted rename reply is deliberately lost") } catch (_: java.io.IOException) { }
                        assertEquals(SpaceClient.ChangeStatus.AWAITING_CONFIRMATION, author.categoryChange(space)?.status)
                        author = creator(); author.flush(space)
                        assertEquals(SpaceClient.ChangeStatus.ACCEPTED, author.categoryChange(space)?.status)
                        val retries = originals.filter { it.id == original }
                        assertEquals(2, retries.size); assertEquals(retries[0].envelope(), retries[1].envelope())
                        for ((reader, channel) in listOf(firstReader to first, secondReader to second)) {
                            reader.refresh(space, channel)
                            assertEquals("Shared projects", reader.channels(space).single { it.id == channel }.profile.category)
                            assertEquals(category, reader.categories(space).single().id)
                        }
                        author.editChannel(space, first, SpaceChannelProfile("First team")); author.flush(space)
                        author.editCategory(space, category, SpaceCategoryProfile("Second team's work")); author.flush(space)
                        firstReader.refresh(space, first); secondReader.refresh(space, second)
                        assertTrue(firstReader.categories(space).isEmpty())
                        assertEquals("Second team's work", secondReader.categories(space).single().profile.name)
                        val last = SaitoEnvelope.decode(originals.last { SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString == "edit-category" }.envelope()).body
                        assertEquals(setOf(second), last.getAsJsonObject("category_metadata").keySet())
                        author.editChannel(space, second, SpaceChannelProfile("Second team")); author.flush(space)
                        assertFalse(author.categories(space).any { it.id == category })
                        assertThrows(IllegalArgumentException::class.java) {
                            author.editCategory(space, category, SpaceCategoryProfile("Must remain private"))
                        }
                        assertFalse(author.categories(space).any { it.profile.name == "Must remain private" })
                        assertFalse(originals.any { SaitoEnvelope.decode(it.envelope()).body.toString().contains("Team projects") || SaitoEnvelope.decode(it.envelope()).body.toString().contains("Shared projects") || SaitoEnvelope.decode(it.envelope()).body.toString().contains("Second team's work") })
                    }
                }
            }
        }
    }

    @Test(timeout = 45_000) fun publicAndPrivateStartersUseOneDurableCategoryAndExplicitCreationIsRecoverable() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("category-starters-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("category-starters-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                for (visibility in SpaceVisibility.entries) {
                    val space = creator.create(SpaceProfile("Categorized starter", visibility = visibility), listOf(
                        SpaceChannelProfile("general", category = "Conversations"), SpaceChannelProfile("planning", category = "Conversations")))
                    creator.flush(space)
                    val category = creator.categories(space).single()
                    assertEquals("Conversations", category.profile.name)
                    assertEquals(visibility == SpaceVisibility.PRIVATE, category.isPrivate)
                    assertEquals(setOf(category.id), creator.channels(space).map { it.categoryId }.toSet())
                    val added = creator.createCategory(space, SpaceCategoryProfile("Resources")); creator.flush(space)
                    assertEquals(added, creator.categoryChange(space)?.categoryId)
                    assertEquals(true, creator.categoryChange(space)?.creating)
                    assertEquals(visibility == SpaceVisibility.PRIVATE, creator.categoryChange(space)?.isPrivate)
                    assertEquals(SpaceClient.ChangeStatus.ACCEPTED, creator.categoryChange(space)?.status)
                    creator.editCategory(space, category.id, SpaceCategoryProfile("Community conversations")); creator.flush(space)
                    assertEquals(false, creator.categoryChange(space)?.creating)
                    assertEquals(setOf("Community conversations"), creator.channels(space).map { it.profile.category }.toSet())
                    assertEquals(setOf("Community conversations", "Resources"), creator.categories(space).map { it.profile.name }.toSet())
                    val opposite = if (visibility == SpaceVisibility.PUBLIC) SpaceVisibility.PRIVATE else SpaceVisibility.PUBLIC
                    assertThrows(IllegalArgumentException::class.java) {
                        creator.createCategory(space, SpaceCategoryProfile("Keep this draft private"), opposite)
                    }
                    assertEquals(setOf("Community conversations", "Resources"), creator.categories(space).map { it.profile.name }.toSet())
                }
            }
        }
    }

    @Test(timeout = 45_000) fun aNameOnlyChannelEditKeepsItsExplicitlyChosenCategoryWhenNamesDuplicate() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("category-identity-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("category-identity-owner.sqlite").absolutePath).use { ownerStore ->
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                val space = creator.create(SpaceProfile("Stable folders", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                val first = creator.createCategory(space, SpaceCategoryProfile("Projects")); creator.flush(space)
                val second = creator.createCategory(space, SpaceCategoryProfile("Projects")); creator.flush(space)
                val channel = creator.createChannel(space, SpaceChannelProfile("planning", category = "Projects")); creator.flush(space)
                creator.editChannel(space, channel, SpaceChannelProfile("planning", category = "Projects"), second); creator.flush(space)
                assertEquals(second, creator.channels(space).single { it.id == channel }.categoryId)
                creator.editChannel(space, channel, SpaceChannelProfile("renamed planning", category = "Projects")); creator.flush(space)
                assertEquals("Changing a channel name cannot silently move it to a same-named category", second,
                    creator.channels(space).single { it.id == channel }.categoryId)
                val direct = creator.createChannel(space, SpaceChannelProfile("second planning", category = "Projects"), second)
                assertEquals(second, creator.channelCreation(space)?.categoryId)
                creator.flush(space)
                assertEquals(second, creator.channels(space).single { it.id == direct }.categoryId)
                creator.editCategory(space, first, SpaceCategoryProfile("Other projects")); creator.flush(space)
                assertEquals("Projects", creator.channels(space).single { it.id == channel }.profile.category)
            }
        }
    }

    @Test(timeout = 45_000) fun anEncryptedCategoryEditCannotBeRedirectedToAnotherCategoryInTheSameGroup() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("category-target-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("category-target-owner.sqlite").absolutePath).use { ownerStore ->
                var head = ""
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { receipt ->
                        if (SaitoEnvelope.decode(receipt).body.get("stream")?.asString == "control") head = original.id
                    }
                }
                val space = creator.create("Private category targets"); creator.flush(space)
                val first = creator.createCategory(space, SpaceCategoryProfile("First category")); creator.flush(space)
                val second = creator.createCategory(space, SpaceCategoryProfile("Second category")); creator.flush(space)
                val channel = creator.community(space).channel
                val keys = ownerStore.transaction {
                    val data = com.google.gson.JsonParser.parseString(requireNotNull(get("$space/state")).toString(Charsets.UTF_8)).asJsonObject
                    SpaceCrypto.restore(java.util.Base64.getDecoder().decode(data.get("crypto").asString))
                }
                val operation = "f".repeat(64)
                val metadata = keys.publish(owner, "category-profile", operation, head, JsonObject().apply {
                    addProperty("version", 1); addProperty("category_id", first)
                    add("profile", SpaceCategoryProfile("A rename for the first category").json())
                }.toString(), clock.millis(), targetCategoryId = first)
                val redirected = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("kind", "edit-category"); addProperty("control_id", head)
                    addProperty("operation_id", operation); addProperty("category_id", second)
                    add("category_metadata", JsonObject().apply {
                        addProperty(channel, java.util.Base64.getEncoder().encodeToString(metadata.envelope().toByteArray(Charsets.UTF_8)))
                    })
                }, clock.millis(), listOf(service.publicKey))
                val receipt = remote.exchange(redirected.envelope())
                assertEquals("The receiver checks the encrypted record's target before accepting the control", "rejection",
                    SaitoEnvelope.decode(receipt).body.get("stream").asString)
                assertEquals(receipt, remote.exchange(redirected.envelope()))
                creator.refresh(space)
                assertEquals(setOf("First category", "Second category"), creator.categories(space).map { it.profile.name }.toSet())
            }
        }
    }
    @Test(timeout = 45_000) fun aLegacyNameOnlyEditAfterCategoryRenameKeepsItsStableFolderAndCanonicalLabel() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("legacy-category-label-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("legacy-category-label-owner.sqlite").absolutePath).use { ownerStore ->
                var head = ""
                val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { original ->
                    remote.exchange(original.envelope()).also { receipt ->
                        if (SaitoEnvelope.decode(receipt).body.get("stream")?.asString == "control") head = original.id
                    }
                }
                val space = creator.create(SpaceProfile("Legacy channel edits", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                val channel = creator.createChannel(space, SpaceChannelProfile("planning", category = "Projects")); creator.flush(space)
                val category = requireNotNull(creator.channels(space).single { it.id == channel }.categoryId)
                creator.editCategory(space, category, SpaceCategoryProfile("Shared work")); creator.flush(space)
                val original = SaitoTransaction.create(owner, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("kind", "edit-channel"); addProperty("control_id", head)
                    addProperty("operation_id", "f".repeat(64)); addProperty("channel_id", channel)
                    add("channel_profile", SpaceChannelProfile("renamed planning", category = "Projects").json())
                }, clock.millis(), listOf(service.publicKey))
                assertEquals("A name-only legacy edit preserves its previous category reference", "control",
                    SaitoEnvelope.decode(remote.exchange(original.envelope())).body.get("stream").asString)
                creator.refresh(space)
                val edited = creator.channels(space).single { it.id == channel }
                assertEquals(category, edited.categoryId)
                assertEquals(SpaceChannelProfile("renamed planning", category = "Shared work"), edited.profile)
            }
        }
    }

}

package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ProfilePublishingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private enum class Scenario { AVAILABLE, PREVIOUSLY_AVAILABLE, NEWER_PROFILE, NEWER_PROFILE_CACHE_FAILURE }

    @Test fun savingBioPublishesACompleteSnapshotAndPreservesOtherVerifiedFields() = publishAndReopen(Scenario.AVAILABLE)

    @Test fun previouslyAvailableProfileSurvivesEmptyReadsAndNavigationWithoutPublishingAgain() =
        publishAndReopen(Scenario.PREVIOUSLY_AVAILABLE)

    @Test fun aNewerVerifiedProfileStillBlocksOlderRetryAfterAFreshActivityWithStaleReads() = publishAndReopen(Scenario.NEWER_PROFILE)

    @Test fun newerProfileStillBlocksOlderRetryAfterPublicCacheSavingFailsAndEditorNavigation() {
        // API29 AtomicFile uses a different backup strategy. This controlled .new
        // obstruction exercises the API30+ write failure without corrupting readable bytes.
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 30)
        publishAndReopen(Scenario.NEWER_PROFILE_CACHE_FAILURE)
    }

    private fun publishAndReopen(scenario: Scenario) {
        val supersession = scenario == Scenario.NEWER_PROFILE || scenario == Scenario.NEWER_PROFILE_CACHE_FAILURE
        val historical = scenario == Scenario.PREVIOUSLY_AVAILABLE
        val cacheFailure = scenario == Scenario.NEWER_PROFILE_CACHE_FAILURE
        val reference = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("withMedia") }
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        assertEquals(owner, reference.get("field2").asString)
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        // Independently published, unsafe scalar1 fixture. Never fund or use on a real phone.
        if (original.publicKey != owner) store.replace(original, SaitoWallet.fromPrivateKey("0".repeat(63) + "1"))
        val bio = "A newly published bio 🌱\nKeep my other profile fields"
        val indexed = AtomicReference<JsonObject?>(null)
        val newer = AtomicReference<JsonObject?>(null)
        val stale = AtomicBoolean(false)
        val exactProfileReads = AtomicInteger()
        val newerBio = "Newer profile from another device"
        val supersededStatus = "A newer verified profile supersedes this update. This older update will not be sent again."
        val cacheWriteBlock = File(context.noBackupFilesDir, "profilemeta.owner.$owner.v1.new")
        val blockMarker = File(cacheWriteBlock, "controlled-write-failure")
        var cacheBlocked = false
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(owner, signed.author)
            assertEquals(listOf(owner), signed.recipients)
            assertEquals("Profile", signed.body.get("module").asString)
            assertEquals("update profile", signed.body.get("request").asString)
            assertEquals(1, signed.body.get("snapshot").asInt)
            assertTrue(signed.createdAt > 1700001000004L)
            val fields = signed.body.getAsJsonObject("data")
            assertEquals(bio, fields.get("description").asString)
            assertEquals("https://images.example.test/avatar.png", fields.get("image").asString)
            assertEquals("https://images.example.test/banner.png", fields.get("banner").asString)
            assertEquals(JsonParser.parseString("[\"https://archive.example.test\"]"), fields.get("archive"))
            assertEquals(JsonParser.parseString("{\"theme\":\"blue\"}"), fields.get("extension"))
            if (supersession) {
                val replacement = SaitoTransaction.create(SaitoWallet.fromPrivateKey("0".repeat(63) + "1"),
                    signed.body.deepCopy().apply { getAsJsonObject("data").addProperty("description", newerBio) },
                    signed.createdAt + 1)
                newer.set(JsonObject().apply {
                    addProperty("sig", replacement.id); addProperty("created_at", signed.createdAt + 1)
                    addProperty("field1", "Profile"); addProperty("field2", owner)
                    addProperty("tx", replacement.envelope())
                })
            } else indexed.set(JsonObject().apply {
                    addProperty("sig", signed.id); addProperty("created_at", signed.createdAt)
                    addProperty("field1", "Profile"); addProperty("field2", owner)
                    addProperty("tx", transaction.envelope())
                })
        }, profileArchiveResponse = { criteria ->
            assertEquals(owner, criteria.get("field2").asString)
            if (criteria.has("sig") && criteria.get("sig") == indexed.get()?.get("sig")) exactProfileReads.incrementAndGet()
            (if (stale.get()) { if (historical) emptyList() else listOf(reference) }
                else listOfNotNull(indexed.get(), newer.get(), reference)).filter {
                !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString
            }.joinToString(prefix = "[", postfix = "]")
        }, archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                            ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                            ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Your profile").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodesWithText("Original bio").fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                        ui.onNodeWithText("Original bio").assertIsDisplayed()
                        ui.onNodeWithText("Edit profile").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).assert(
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Original bio")))
                        ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                        assertTrue(peer.transactions.isEmpty())
                        if (cacheFailure) {
                            check(File(context.noBackupFilesDir, "profilemeta.owner.$owner.v1").isFile)
                            check(!cacheWriteBlock.exists() && cacheWriteBlock.mkdir())
                            cacheBlocked = true
                            blockMarker.writeText("Controlled public cache write failure")
                        }
                        ui.onNodeWithText("Save profile").assertIsEnabled().performClick()
                        val status = if (supersession) supersededStatus else "Profile available on this server."
                        ui.waitUntil(25_000) { ui.onAllNodesWithText(status).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(status).assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        capture(if (supersession) "superseded-profile-before-reopen.png" else "bio-snapshot-available.png", status)
                        if (cacheFailure) {
                            ui.waitUntil(20_000) {
                                ui.onAllNodesWithText(context.getString(R.string.profile_publish_error)).fetchSemanticsNodes().isNotEmpty() &&
                                    ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.onNodeWithText(context.getString(R.string.profile_publish_error)).assertIsDisplayed()
                            capture("superseded-cache-write-failed.png", supersededStatus)
                            stale.set(true)
                            ui.onNodeWithText("Back").performClick()
                            ui.waitUntil(20_000) {
                                ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                                    ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty()
                            }
                            ui.onNodeWithText("Edit profile").performClick()
                            ui.waitUntil(20_000) {
                                ui.onAllNodesWithText("Retry profile").fetchSemanticsNodes().isNotEmpty() &&
                                    ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.onNodeWithText(supersededStatus).assertIsDisplayed()
                            ui.onNodeWithText("Retry profile").assertIsNotEnabled()
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                                .assertIsDisplayed().assertIsNotEnabled()
                            capture("superseded-cache-failure-after-navigation.png", supersededStatus)
                            assertEquals(1, peer.transactions.size)
                        }
                        if (!supersession) File(context.cacheDir, "profile-edit-evidence/profile-snapshot-frames.json").writeText(
                            JsonObject().apply {
                                addProperty("synthetic", true)
                                addProperty("frame", Base64.getEncoder().encodeToString(peer.transactions.single()))
                                addProperty("envelope", requireNotNull(indexed.get()).get("tx").asString)
                            }.toString())
                        if (scenario == Scenario.AVAILABLE) {
                            ui.onNodeWithText("Back").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodesWithText(bio).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText(bio).assertIsDisplayed()
                        }
                        assertEquals(owner, store.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        peer.assertHealthy()
                    } catch (failure: Throwable) {
                        try { capture(if (cacheFailure) "superseded-cache-failure-navigation-failure.png"
                            else if (supersession) "superseded-profile-first-failure.png" else "bio-snapshot-first-failure.png") }
                        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
                if (historical) {
                    // Keep the confirmed signed candidate, then return successful empty archive responses.
                    stale.set(true)
                    ActivityScenario.launch<MainActivity>(intent).use {
                        try {
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText("Your profile").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                            ui.onNodeWithText(bio).assertIsDisplayed()
                            ui.onNodeWithText("Edit profile").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry profile") or hasText("Check profile")).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            val status = "This profile was previously available. Current availability is not confirmed."
                            ui.onNodeWithText(status).assertIsDisplayed()
                            ui.onNodeWithText("Profile available on this server.").assertDoesNotExist()
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                                .assertIsDisplayed().assertIsNotEnabled()
                            assertEquals(1, peer.transactions.size)
                            capture("previously-available-empty-reads.png", status)
                            val previousReads = exactProfileReads.get()
                            ui.onNodeWithText("Check profile").assertIsEnabled().performClick()
                            ui.waitUntil(20_000) { exactProfileReads.get() > previousReads &&
                                ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText(status).assertIsDisplayed()
                            assertEquals(1, peer.transactions.size)
                            ui.onNodeWithText("Back").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                            ui.onNodeWithText("Edit profile").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Check profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText(status).assertIsDisplayed()
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                                .assertIsDisplayed().assertIsNotEnabled()
                            capture("previously-available-after-navigation.png", status)
                            assertEquals(1, peer.transactions.size)
                            assertEquals(owner, store.loadExisting().publicKey)
                            peer.assertHealthy()
                        } catch (failure: Throwable) {
                            try { capture("previously-available-reopen-failure.png") }
                            catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                            throw failure
                        }
                    }
                }
                if (supersession && !cacheFailure) {
                    // Do not return to the public profile before closing: that refresh must not save the barrier for the editor.
                    stale.set(true)
                    ActivityScenario.launch<MainActivity>(intent).use {
                        try {
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText("Your profile").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                            ui.onNodeWithText(newerBio).assertIsDisplayed()
                            ui.onNodeWithText("Edit profile").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodesWithText(supersededStatus).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText(supersededStatus).assertIsDisplayed()
                            ui.onNodeWithText("Retry profile").assertIsNotEnabled()
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                                .assertIsDisplayed().assertIsNotEnabled()
                            capture("superseded-profile-reopened-stale.png", supersededStatus)
                            assertEquals(1, peer.transactions.size)
                            assertEquals(owner, store.loadExisting().publicKey)
                            peer.assertHealthy()
                        } catch (failure: Throwable) {
                            try { capture("superseded-profile-reopen-failure.png") }
                            catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                            throw failure
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var cleanupFailure: Throwable? = null
            try {
                if (cacheBlocked) {
                    if (blockMarker.exists()) check(blockMarker.delete())
                    check(cacheWriteBlock.delete())
                }
            } catch (cleanup: Throwable) { cleanupFailure = cleanup }
            try {
                val active = store.loadExisting()
                check(active.publicKey == owner)
                if (active.publicKey != original.publicKey) store.replace(active, original)
            } catch (cleanup: Throwable) {
                if (cleanupFailure != null) cleanupFailure.addSuppressed(cleanup) else cleanupFailure = cleanup
            }
            cleanupFailure?.let { if (primary != null) primary.addSuppressed(it) else throw it }
        }
    }

    private fun capture(name: String, expectedText: String? = null) {
        ui.waitForIdle()
        val file = File(context.cacheDir, "profile-edit-evidence/$name").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    }
}

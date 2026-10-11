package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ProfileNewEditPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun aSupersededUpdateCanBeRetiredForANewDurableEditWithoutResendingIt() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val reference = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("withMedia") }
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val walletStore = AndroidWalletStore(context)
        val original = walletStore.loadOrCreate()
        if (original.publicKey != owner) walletStore.replace(original, wallet)
        val oldBio = "My now superseded profile edit"
        val newerBio = "Newer verified profile to edit"
        val nextBio = "New bio after a superseded update 🌱"
        val status = "A newer verified profile supersedes this update. This older update will not be sent again."
        val publications = AtomicInteger()
        val oldId = AtomicReference<String>()
        val newer = AtomicReference<JsonObject?>()
        val published = AtomicReference<JsonObject?>()
        val stale = AtomicBoolean(false)
        fun row(transaction: SaitoTransaction): JsonObject {
            val signed = SaitoEnvelope.decode(transaction.envelope())
            return JsonObject().apply {
                addProperty("sig", transaction.id); addProperty("created_at", signed.createdAt)
                addProperty("field1", "Profile"); addProperty("field2", signed.author)
                addProperty("tx", transaction.envelope())
            }
        }
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(owner, signed.author); assertEquals(listOf(owner), signed.recipients)
            assertEquals("Profile", signed.body.get("module").asString)
            assertEquals("update profile", signed.body.get("request").asString)
            assertEquals(1, signed.body.get("snapshot").asInt)
            val fields = signed.body.getAsJsonObject("data")
            assertEquals("https://images.example.test/avatar.png", fields.get("image").asString)
            assertEquals("https://images.example.test/banner.png", fields.get("banner").asString)
            when (publications.incrementAndGet()) {
                1 -> {
                    assertEquals(oldBio, fields.get("description").asString)
                    oldId.set(transaction.id)
                    val replacement = SaitoTransaction.create(wallet, signed.body.deepCopy().apply {
                        getAsJsonObject("data").apply {
                            addProperty("description", newerBio)
                            add("archive", JsonParser.parseString("[\"https://newer-archive.example.test\"]"))
                            add("extension", JsonParser.parseString("{\"theme\":\"green\",\"keep\":\"verified\"}"))
                        }
                    }, Math.addExact(signed.createdAt, 60_000))
                    newer.set(row(replacement))
                }
                2 -> {
                    assertNotEquals(oldId.get(), transaction.id)
                    assertTrue(signed.createdAt > requireNotNull(newer.get()).get("created_at").asLong)
                    assertEquals(nextBio, fields.get("description").asString)
                    assertEquals(setOf("description", "image", "banner", "archive", "extension"), fields.keySet())
                    assertEquals(JsonParser.parseString("[\"https://newer-archive.example.test\"]"), fields.get("archive"))
                    assertEquals(JsonParser.parseString("{\"theme\":\"green\",\"keep\":\"verified\"}"), fields.get("extension"))
                    published.set(row(transaction))
                }
                else -> fail("A retired profile update must never be resent")
            }
        }, profileArchiveResponse = { criteria ->
            assertEquals(owner, criteria.get("field2").asString)
            val records = if (criteria.has("sig")) listOfNotNull(published.get(), newer.get(), reference)
                .filter { it.get("sig").asString == criteria.get("sig").asString }
            else if (stale.get()) listOf(reference) else listOfNotNull(published.get(), newer.get(), reference)
            records.joinToString(prefix = "[", postfix = "]")
        }, archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailure {
                        openEditor()
                        ui.onNode(hasSetTextAction()).performTextReplacement(oldBio)
                        ui.onNodeWithText("Save profile").assertIsEnabled().performClick()
                        waitFor(status)
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Retry profile").assertIsNotEnabled()
                        assertEquals(1, peer.transactions.size)
                        capture("profile-new-edit-superseded.png", status)
                        ui.onNodeWithText("Start new edit").assertIsEnabled().performClick()
                        waitFor("Start a new profile edit?")
                        ui.onNodeWithText("An update already received by a server may still appear. This stops retrying the saved update on this device.").assertIsDisplayed()
                        capture("profile-new-edit-confirmation.png", "Start a new profile edit?")
                        ui.onNodeWithText("Keep saved update").performClick()
                        ui.onNodeWithText(status).assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        ui.onNodeWithText("Start new edit").performClick()
                        ui.onNodeWithText("Discard and start").performClick()
                        val editable = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(newerBio)) and isEnabled()
                        ui.waitUntil(20_000) { ui.onAllNodes(editable).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(editable).assertIsDisplayed()
                        ui.onAllNodesWithText(status).assertCountEquals(0)
                        ui.onAllNodesWithText("Retry profile").assertCountEquals(0)
                        ui.onNodeWithText("Save profile").assertIsNotEnabled()
                        assertEquals(1, peer.transactions.size)
                        capture("profile-new-edit-ready.png", "Save profile")
                        ui.onNode(hasSetTextAction()).performTextReplacement(nextBio)
                        ui.onNodeWithText("Save draft").performClick()
                        waitForPublicEditor()
                        ui.onNodeWithText(newerBio).assertIsDisplayed()
                        ui.onAllNodesWithText(nextBio).assertCountEquals(0)
                        assertEquals(1, peer.transactions.size); peer.assertHealthy()
                    }
                }
                stale.set(true)
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailure {
                        openEditor()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            SemanticsProperties.EditableText, AnnotatedString(nextBio))).assertIsEnabled()
                        ui.onNodeWithText("Save profile").assertIsEnabled()
                        assertEquals(1, peer.transactions.size)
                        capture("profile-new-edit-cold-draft.png", "Save profile")
                        ui.onNodeWithText("Save profile").performClick()
                        waitFor("Profile available on this server.")
                        assertEquals(2, peer.transactions.size)
                        File(context.cacheDir, "profile-edit-evidence/profile-new-edit-frames.json").writeText(JsonObject().apply {
                            addProperty("synthetic", true)
                            addProperty("frame", Base64.getEncoder().encodeToString(peer.transactions.last()))
                            addProperty("envelope", requireNotNull(published.get()).get("tx").asString)
                        }.toString())
                        capture("profile-new-edit-available.png", "Profile available on this server.")
                        ui.onNodeWithText("Back").performClick()
                        waitForPublicEditor()
                        ui.onNodeWithText(nextBio).assertIsDisplayed()
                        capture("profile-new-edit-public.png", nextBio)
                        assertEquals(owner, walletStore.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        assertEquals(2, peer.transactions.size); peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                val active = walletStore.loadExisting(); check(active.publicKey == owner)
                if (original.publicKey != owner) walletStore.replace(active, original)
            } catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }

    @Test fun aColdPendingBioUpdateShowsItsSignedMediaOmissionsInsteadOfStalePublicImages() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val reference = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("withMedia") }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val owner = wallet.publicKey
        val walletStore = AndroidWalletStore(context)
        val original = walletStore.loadOrCreate()
        if (original.publicKey != owner) walletStore.replace(original, wallet)
        val fresh = SaitoTransaction.create(wallet, SaitoEnvelope.decode(reference.get("tx").asString).body.deepCopy().apply {
            getAsJsonObject("data").apply {
                addProperty("description", "Fresh baseline with generated defaults")
                remove("image"); remove("banner")
            }
        }, reference.get("created_at").asLong + 60_000)
        val freshRow = JsonObject().apply { addProperty("tx", fresh.envelope()) }
        val exposeFresh = AtomicBoolean(false)
        val stale = AtomicBoolean(false)
        val confirm = AtomicBoolean(false)
        val indexed = AtomicReference<JsonObject?>()
        val bio = "Pending bio keeps signed generated defaults 🌱"
        val status = "Save requested. Profile availability on this server is not confirmed."
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(owner, signed.author); assertEquals(listOf(owner), signed.recipients)
            assertEquals(bio, signed.body.getAsJsonObject("data").get("description").asString)
            assertFalse(signed.body.getAsJsonObject("data").has("image"))
            assertFalse(signed.body.getAsJsonObject("data").has("banner"))
            assertTrue(signed.createdAt > SaitoEnvelope.decode(fresh.envelope()).createdAt)
            assertNull(indexed.get()) // No retry may rebroadcast the retained candidate.
            indexed.set(JsonObject().apply {
                addProperty("sig", transaction.id); addProperty("tx", transaction.envelope())
            })
        }, profileArchiveResponse = { criteria ->
            assertEquals(owner, criteria.get("field2").asString)
            if (criteria.has("sig")) {
                if (confirm.get() && criteria.get("sig") == indexed.get()?.get("sig")) "[${indexed.get()}]" else "[]"
            } else if (exposeFresh.get() && !stale.get()) "[$freshRow]" else "[$reference]"
        }, archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailure {
                        openEditor()
                        ui.onNodeWithContentDescription("Profile picture").assertExists()
                        ui.onNodeWithContentDescription("Profile banner").assertExists()
                        ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                        exposeFresh.set(true)
                        ui.onNodeWithText("Save profile").performClick()
                        waitFor(status)
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        assertGeneratedDefaults()
                        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsNotEnabled()
                        assertEquals(1, peer.transactions.size); peer.assertHealthy()
                        capture("profile-pending-preview-mounted.png", status)
                    }
                }
                stale.set(true)
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailure {
                        openEditor(editable = false)
                        waitFor(status)
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(SemanticsMatcher.expectValue(
                            SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsNotEnabled()
                        assertEquals(1, peer.transactions.size)
                        assertGeneratedDefaults()
                        capture("profile-pending-preview-cold.png", status)
                        confirm.set(true)
                        ui.onNodeWithText("Retry profile").performClick()
                        waitFor("Profile available on this server.")
                        assertGeneratedDefaults()
                        assertEquals(1, peer.transactions.size); peer.assertHealthy()
                        capture("profile-pending-preview-available.png", "Profile available on this server.")
                        assertEquals(owner, walletStore.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                val active = walletStore.loadExisting(); check(active.publicKey == owner)
                if (original.publicKey != owner) walletStore.replace(active, original)
            } catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun assertGeneratedDefaults() {
        ui.onNodeWithContentDescription(context.getString(R.string.profile_generated_picture))
            .performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithContentDescription("Profile picture").assertCountEquals(0)
        ui.onAllNodesWithContentDescription("Profile banner").assertCountEquals(0)
    }

    private fun openEditor(editable: Boolean = true) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Your profile").performClick()
        waitForPublicEditor()
        ui.onNodeWithText("Edit profile").performClick()
        if (editable) ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForPublicEditor() = ui.waitUntil(20_000) {
        ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
            ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty()
    }

    private fun waitFor(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun captureFailure(block: () -> Unit) {
        try { block() } catch (failure: Throwable) {
            try { capture("profile-new-edit-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
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

package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** One recovery flow across three host-separated real processes; checkpoints are public synthetic data. */
class ProfileProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val checkpoint get() = File(context.cacheDir, "profile-process-checkpoint.json")
    private val bio = "Recover my newest bio and both prepared profile images 🌱"
    private val pending = "Save requested. Profile availability on this server is not confirmed."

    private fun guard() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    private fun owner() = AndroidWalletStore(context).loadExisting().publicKey

    private fun continuation(phase: String): JsonObject {
        guard()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals(phase, row.get("phase").asString)
        assertEquals(row.get("author").asString, owner())
        assertEquals("owner", row.get("backend").asString)
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        return row
    }

    @Test fun terminateInARealPickerWithLatestBioAndDurableImagesWhoseSourcesWereDeleted() {
        guard()
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach { check(it.delete()) }
        val author = owner()
        val owned = mutableListOf<Uri>()
        withImageFixtureCleanup({ cleanupSyntheticImages(context.contentResolver, owned) }) {
            val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
            launch(peer) {
                openEditor()
                ui.onNode(hasSetTextAction()).performTextReplacement("Earlier bio before the final picker")
                selectImage("Change profile picture", "red", "Profile picture", intArrayOf(220, 30, 40), owned)
                selectImage("Change banner", "blue", "Profile banner", intArrayOf(20, 60, 220), owned)
                owned.toList().forEach { uri ->
                    assertEquals(1, context.contentResolver.delete(uri, null, null)); owned.remove(uri)
                }
                assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                capture("prepared-before-death.png", "Save profile")
                ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                // No stub or result: launch the actual external picker only after latest edits are saved.
                clickPicker("Change profile picture")
                val device = UiDevice.getInstance(instrumentation)
                ui.waitUntil(15_000) { device.currentPackageName in setOf(
                    "com.android.documentsui", "com.google.android.photopicker",
                    "com.google.android.providers.media.module", "com.android.providers.media.module") }
                assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                assertEquals(author, owner())
                capture("external-picker-before-death.png")
                terminate(JsonObject().apply { addProperty("phase", "draft") })
            }
        }
    }

    @Test fun coldDraftRecoversLatestBioAndDeletedSourcePixelsThenTerminatesAfterUncertainPublication() {
        continuation("draft") // Never reset wallet/backend here: that would mask lost continuity.
        val author = owner()
        val observed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(author, signed.author); assertEquals(listOf(author), signed.recipients)
            assertEquals("Profile", signed.body.get("module").asString)
            assertEquals("update profile", signed.body.get("request").asString)
            assertEquals(1, signed.body.get("snapshot").asInt)
            val fields = signed.body.getAsJsonObject("data")
            assertEquals(setOf("description", "image", "banner"), fields.keySet())
            assertEquals(bio, fields.get("description").asString)
            assertPrepared(fields.get("image").asString, 8, 8, intArrayOf(220, 30, 40))
            assertPrepared(fields.get("banner").asString, 12, 4, intArrayOf(20, 60, 220))
            assertNull(observed.get())
            observed.set(JsonObject().apply {
                addProperty("sig", transaction.id); addProperty("tx", transaction.envelope())
                addProperty("frame", Base64.getEncoder().encodeToString(frame))
            })
        }, profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
        launch(peer) {
            openEditor()
            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsEnabled()
            assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
            assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            capture("cold-draft.png", "Save profile")
            ui.onNodeWithText("Save profile").performClick()
            waitFor(pending)
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsNotEnabled()
            assertEquals(1, peer.transactions.size); peer.assertHealthy()
            capture("uncertain-before-death.png", pending)
            terminate(requireNotNull(observed.get()).deepCopy().apply { addProperty("phase", "candidate") })
        }
    }

    @Test fun coldCandidateReconcilesReadOnlyAndVerifiedClearingKeepsPublicBioAndPixels() {
        val row = continuation("candidate")
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { criteria ->
            assertEquals(owner(), criteria.get("field2").asString)
            if (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) "[$row]" else "[]"
        }, archiveResponse = { "[]" })
        launch(peer) {
            openEditor(editable = false)
            waitFor("Profile available on this server.")
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsNotEnabled()
            assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
            assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            capture("cold-candidate-available.png", "Profile available on this server.")
            ui.onNodeWithText("Back").performClick()
            waitForPublicEditor()
            ui.onNodeWithText(bio).assertIsDisplayed()
            ui.onNodeWithText("Edit profile").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
            ui.onNodeWithText("Save profile").assertIsNotEnabled()
            ui.onAllNodesWithText("Retry profile").assertCountEquals(0)
            ui.onAllNodesWithText("Start new edit").assertCountEquals(0)
            assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
            assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            assertEquals(row.get("author").asString, owner())
            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
            capture("cleared-public-baseline.png", "Save profile")
        }
    }

    private fun launch(peer: ControlledFeedPeer, block: () -> Unit) {
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try { block() }
                catch (failure: Throwable) {
                    try { capture("failure.png") }
                    catch (captureFailure: Throwable) { failure.addSuppressed(captureFailure) }
                    throw failure
                }
            }
        }
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

    private fun clickPicker(control: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithText(control).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun selectImage(control: String, color: String, label: String, expected: IntArray, owned: MutableList<Uri>) {
        val images = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images") }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-profile-process-$color.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        owned.add(uri)
        context.contentResolver.openOutputStream(uri)!!.use {
            it.write(android.util.Base64.decode(images.get(color).asString.substringAfter(','), android.util.Base64.NO_WRAP))
        }
        Intents.init()
        try {
            Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            clickPicker(control)
            assertVisiblePixels(label, expected)
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Save draft") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } finally { Intents.release() }
    }

    private fun assertVisiblePixels(label: String, expected: IntArray) {
        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        val bitmap = ui.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
        assertColor(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), expected)
    }

    private fun assertPrepared(source: String, width: Int, height: Int, expected: IntArray) {
        assertTrue(source.length <= 512 * 1024 && source.startsWith("data:image/jpeg;base64,"))
        val bytes = Base64.getDecoder().decode(source.substringAfter(','))
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
            assertColor(bitmap.getPixel(width / 2, height / 2), expected)
        } finally { bitmap.recycle() }
    }

    private fun assertColor(pixel: Int, expected: IntArray) {
        listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).forEachIndexed { channel, value ->
            assertTrue(kotlin.math.abs(value - expected[channel]) <= 5)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun terminate(row: JsonObject) {
        row.addProperty("author", owner()); row.addProperty("backend", AndroidBackendStore(context).load().id)
        row.addProperty("pid", Process.myPid())
        checkpoint.outputStream().use { output -> output.write(row.toString().toByteArray()); output.fd.sync() }
        Process.killProcess(Process.myPid())
        error("Expected real process termination")
    }

    private fun capture(name: String, expectedText: String? = null) {
        val device = UiDevice.getInstance(instrumentation)
        expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
        val file = File(context.cacheDir, "profile-process-evidence/$name").apply { parentFile?.mkdirs() }
        device.dumpWindowHierarchy(File(file.parentFile, name.removeSuffix(".png") + ".xml"))
        check(device.takeScreenshot(file))
    }
}

package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Process
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Three host-separated real processes. Checkpoints contain public synthetic data only. */
class ImageProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val checkpoint get() = File(context.cacheDir, "image-process-checkpoint.json")
    private val text = "Recover prepared image after its source disappears"

    private fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    private fun fixture() = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
        .use { JsonParser.parseReader(it).asJsonObject }

    @Test fun terminateWithDurableImageDraftAfterDeletingItsSource() {
        prepare()
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") || it.name.startsWith("feed.") }?.forEach { check(it.delete()) }
        val fixture = fixture()
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-cold-image.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        try {
            context.contentResolver.openOutputStream(uri)!!.use {
                it.write(android.util.Base64.decode(fixture.getAsJsonObject("images").get("red").asString.substringAfter(','), android.util.Base64.NO_WRAP))
            }
            Intents.init()
            try {
                Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                    hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                    hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                    .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("ordinary")}]")
                launch(peer) {
                    ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    assertCachedOriginalImages()
                    openComposer()
                    ui.onNode(hasSetTextAction()).performTextReplacement(text)
                    ui.onNodeWithText("Add images").performScrollTo().assertIsDisplayed().performClick()
                    waitForPreparedImage()
                    assertEquals(1, context.contentResolver.delete(uri, null, null))
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    capture("durable-draft.png")
                    terminate(JsonObject().apply { addProperty("phase", "draft") })
                }
            } finally { Intents.release() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun coldDraftKeepsPixelsWithoutSourceAndTerminatesAfterUncertainSend() {
        prepare()
        assertEquals("draft", JsonParser.parseString(checkpoint.readText()).asJsonObject.get("phase").asString)
        val observed = AtomicReference<JsonObject?>()
        val rows = "[]" // The cold process must recover the original images from its signed cache.
        // The archive intentionally never confirms the candidate during this process.
        val serving = ControlledFeedPeer(rows, onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(candidate.envelope())
            assertEquals(text, post.text)
            assertEquals(1, post.images.size)
            assertTrue(post.images.single().startsWith("data:image/jpeg;base64,"))
            observed.set(postingArchiveRow(candidate))
        }, archiveResponse = { criteria -> if (criteria.has("sig")) "[]" else rows })
        launch(serving) {
            ui.waitUntil(20_000) { serving.requests.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            assertCachedOriginalImages()
            openComposer()
            waitForPreparedImage()
            ui.onNode(hasSetTextAction()).assert(hasText(text)).assertIsEnabled()
            assertTrue(serving.transactions.isEmpty())
            capture("cold-draft.png")
            ui.onNode(hasText("Send") and isEnabled()).performClick()
            waitFor("Submitted. Waiting for this server.")
            ui.waitUntil(10_000) { observed.get() != null }
            assertEquals(1, serving.transactions.size); serving.assertHealthy()
            terminate(requireNotNull(observed.get()).deepCopy().apply { addProperty("phase", "candidate") })
        }
    }

    @Test fun coldCandidateReconcilesWithoutRebroadcastingOrLosingItsImage() {
        prepare()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("candidate", row.get("phase").asString)
        val original = fixture().getAsJsonObject("ordinary")
        val peer = ControlledFeedPeer("[$original]", archiveResponse = { criteria ->
            if (criteria.get("sig")?.asString == row.get("sig").asString) "[$row]"
            else if (criteria.has("sig")) "[]" else "[$original]"
        })
        launch(peer) {
            openComposer(waitForEditable = false)
            waitFor("Send requested. Availability on this server is not confirmed.")
            waitForPreparedImage(editable = false)
            ui.onNode(hasSetTextAction()).assert(hasText(text)).assertIsNotEnabled()
            assertTrue(peer.transactions.isEmpty())
            ui.onNodeWithText("Retry").performClick()
            waitFor("Available on this server.")
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            capture("reconciled-image.png")
            ui.onNodeWithText("Back").performClick()
            openComposer()
            ui.onNode(hasSetTextAction()).assert(hasText(""))
            ui.onAllNodesWithContentDescription("Image 1 of 1").assertCountEquals(0)
        }
    }

    private fun launch(peer: ControlledFeedPeer, block: () -> Unit) {
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try { block() }
                catch (failure: Throwable) { capture("failure.png"); throw failure }
            }
        }
    }

    private fun assertCachedOriginalImages() {
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Signed image reference"))
        ui.onNodeWithText("Signed image reference").assertIsDisplayed()
        for ((index, expected) in listOf(intArrayOf(220, 30, 40), intArrayOf(20, 60, 220)).withIndex()) {
            val label = "Image ${index + 1} of 2"
            ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
            ui.onAllNodesWithContentDescription(label).assertCountEquals(1)
            ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(label))
            val bitmap = ui.onNodeWithContentDescription(label).assertIsDisplayed().captureToImage().asAndroidBitmap()
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).forEachIndexed { channel, value ->
                assertTrue(kotlin.math.abs(value - expected[channel]) <= 5)
            }
        }
    }

    private fun openComposer(waitForEditable: Boolean = true) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasText("New post") and hasClickAction()).performClick()
        if (waitForEditable) ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForPreparedImage(editable: Boolean = true) {
        ui.waitUntil(20_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isNotEmpty() &&
            (!editable || ui.onAllNodes(hasText("Remove image 1") and isEnabled()).fetchSemanticsNodes().isNotEmpty()) }
        val bitmap = ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertTrue(kotlin.math.abs(Color.red(pixel) - 220) <= 5)
        assertTrue(kotlin.math.abs(Color.green(pixel) - 30) <= 5)
        assertTrue(kotlin.math.abs(Color.blue(pixel) - 40) <= 5)
    }

    private fun waitFor(value: String) = ui.waitUntil(20_000) { ui.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }

    private fun terminate(row: JsonObject) {
        row.addProperty("pid", Process.myPid())
        checkpoint.outputStream().use { output -> output.write(row.toString().toByteArray()); output.fd.sync() }
        Process.killProcess(Process.myPid())
        error("Expected process termination")
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "image-process-evidence/$name").apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }
}

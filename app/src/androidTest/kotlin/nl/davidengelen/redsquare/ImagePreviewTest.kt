package nl.davidengelen.redsquare

import android.content.Intent
import android.content.ContentValues
import android.app.Activity
import android.app.Instrumentation
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.hamcrest.CoreMatchers.anyOf
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImagePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun selectedImageAndTextSurviveSavingAndRecreationWithoutPublishing() {
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val source = fixture.getAsJsonObject("images").get("red").asString
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-image-draft-reference.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        try {
            context.contentResolver.openOutputStream(uri)!!.use { output ->
                output.write(android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP))
            }
            Intents.init()
            try {
                Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                    hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                    hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                    .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("ordinary")}]")
                peer.start().use { server ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                        try {
                            ui.waitUntil(20_000) { peer.requests.get() > 0 &&
                                ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText("New post").performClick()
                            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNode(hasSetTextAction()).performTextReplacement("Saved image draft")
                            ui.onNodeWithText("Add images").assertIsDisplayed().performClick()
                            ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Save draft").performClick()
                            ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                            scenario.recreate()
                            ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNode(hasText("New post") and hasClickAction()).assertIsDisplayed().performClick()
                            ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isNotEmpty() }
                            ui.onNode(hasSetTextAction()).assert(hasText("Saved image draft"))
                            val bitmap = ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
                            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                            assertTrue(kotlin.math.abs(android.graphics.Color.red(pixel) - 220) <= 5)
                            assertTrue(kotlin.math.abs(android.graphics.Color.green(pixel) - 30) <= 5)
                            assertTrue(kotlin.math.abs(android.graphics.Color.blue(pixel) - 40) <= 5)
                            assertTrue(peer.transactions.isEmpty())
                            peer.assertHealthy()
                            capture("images-evidence/saved-draft.png")
                        } catch (failure: Throwable) {
                            capture("images-evidence/draft-failure.png")
                            throw failure
                        }
                    }
                }
            } finally { Intents.release() }
        } finally { context.contentResolver.delete(uri, null, null) }
    }

    @Test fun signedImagesDisplayInTheirOriginalOrderAndOpenAnImageViewer() {
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val original = fixture.getAsJsonObject("ordinary")
        val peer = ControlledFeedPeer("[$original]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() > 0 &&
                        ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Signed image reference"))
                    ui.onNodeWithText("Signed image reference").assertIsDisplayed()
                    for ((index, expected) in listOf(0xffdc1e28.toInt(), 0xff143cdc.toInt()).withIndex()) {
                        val label = "Image ${index + 1} of 2"
                        ui.waitUntil(15_000) { ui.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(label))
                        val bitmap = ui.onNodeWithContentDescription(label).assertIsDisplayed().captureToImage().asAndroidBitmap()
                        assertEquals(expected, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                    }
                    ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Image 1 of 2"))
                    ui.onNodeWithContentDescription("Image 1 of 2").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodesWithText("Close image").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Image 1 of 2").assertIsDisplayed()
                    capture("images-evidence/image-viewer.png")
                    ui.onNodeWithText("Close image").performClick()
                    ui.onNodeWithText("Signed image reference").assertExists()
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    capture("images-evidence/display-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun systemImagePickerReturnsAnOwnedSyntheticImageToTheDraft() {
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val name = "redsquare-system-picker-reference.png"
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.WIDTH, 12)
            put(MediaStore.Images.Media.HEIGHT, 8)
            put(MediaStore.Images.Media.DATE_TAKEN, 1_700_000_080_000L)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        try {
            context.contentResolver.openOutputStream(uri)!!.use { output ->
                output.write(android.util.Base64.decode(fixture.getAsJsonObject("images").get("green").asString.substringAfter(','), android.util.Base64.NO_WRAP))
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("ordinary")}]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 &&
                            ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("New post").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Add images") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Add images").performClick()
                        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.desc("Show roots")), 10_000)).click()
                            device.waitForIdle(1_000)
                            device.dumpWindowHierarchy(File(context.cacheDir, "images-evidence/system-picker-drawer.xml").apply { parentFile?.mkdirs() })
                            val title = androidx.test.uiautomator.By.text(ImageTestDocumentsProvider.TITLE)
                            val drawerTitle = androidx.test.uiautomator.By.text(ImageTestDocumentsProvider.TITLE)
                                .hasAncestor(androidx.test.uiautomator.By.res("com.android.documentsui", "roots_list"))
                            val selectedRoot = androidx.test.uiautomator.By.res("com.android.documentsui", "toolbar")
                                .hasDescendant(title)
                            val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                            while (!device.hasObject(selectedRoot) && android.os.SystemClock.uptimeMillis() < deadline) {
                                try { device.findObject(drawerTitle)?.click() }
                                catch (_: androidx.test.uiautomator.StaleObjectException) { /* Reacquire after transition. */ }
                                device.wait(androidx.test.uiautomator.Until.hasObject(selectedRoot), 1_000)
                            }
                            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.res("com.android.documentsui", "toolbar")
                                .hasDescendant(androidx.test.uiautomator.By.text(ImageTestDocumentsProvider.TITLE))), 10_000))
                        }
                        val selector = if (android.os.Build.VERSION.SDK_INT < 33) androidx.test.uiautomator.By.text(name)
                            else androidx.test.uiautomator.By.descStartsWith("Photo taken on ")
                        val file = device.wait(androidx.test.uiautomator.Until.findObject(selector), 15_000)
                        capture("images-evidence/system-picker.png")
                        val xml = File(context.cacheDir, "images-evidence/system-picker.xml")
                        device.dumpWindowHierarchy(xml)
                        assertNotNull("Synthetic image should be visible in the system picker", file)
                        assertEquals(1, device.findObjects(selector).size)
                        file!!.click()
                        capture("images-evidence/system-picker-selected.png")
                        device.dumpWindowHierarchy(File(context.cacheDir, "images-evidence/system-picker-selected.xml"))
                        val confirm = if (android.os.Build.VERSION.SDK_INT < 33) androidx.test.uiautomator.By.text("OPEN").enabled(true)
                            else androidx.test.uiautomator.By.textStartsWith("Add").enabled(true)
                        requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(confirm), 5_000)).click()
                        ui.waitUntil(20_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isNotEmpty() }
                        val bitmap = ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
                        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                        assertTrue(kotlin.math.abs(android.graphics.Color.red(pixel) - 30) <= 5)
                        assertTrue(kotlin.math.abs(android.graphics.Color.green(pixel) - 190) <= 5)
                        assertTrue(kotlin.math.abs(android.graphics.Color.blue(pixel) - 70) <= 5)
                        ui.onNode(hasText("Send") and isEnabled()).assertIsDisplayed()
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                        capture("images-evidence/system-picker-draft.png")
                    } catch (failure: Throwable) {
                        capture("images-evidence/system-picker-failure.png")
                        androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                            .dumpWindowHierarchy(File(context.cacheDir, "images-evidence/system-picker-failure.xml"))
                        throw failure
                    }
                }
            }
        } finally {
            context.contentResolver.delete(uri, null, null)
            val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
            if (device.currentPackageName in listOf("com.android.documentsui", "com.google.android.providers.media.module")) device.pressBack()
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, name).apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }
}

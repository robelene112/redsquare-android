package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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

    private fun capture(name: String) {
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).apply { parentFile?.mkdirs() }.outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}

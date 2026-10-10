package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Explicit public provider read. The Saito peer stays controlled; no wallet operation is sent. */
class PublicYouTubeReadTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("publicVideoRead") == "1")
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun trustedProviderPlayControlStartsTheDocumentedPublicExample() {
        val reference = instrumentation.context.assets.open("native-video-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val row = reference.getAsJsonObject("linked")
        val peer = ControlledFeedPeer("[$row]")
        val device = UiDevice.getInstance(instrumentation)
        peer.start().use { server ->
            // No provider fixture: this uses the real API/iframe and its public controls.
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 && ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val button = hasText("Watch on YouTube") and hasAnyAncestor(
                        hasAnyChild(hasText("Explicit signed YouTube link") and hasClickAction()))
                    ui.onNode(hasScrollAction()).performScrollToNode(button)
                    ui.onNode(button).assertIsDisplayed().performClick()
                    ui.waitUntil(30_000) {
                        ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() ||
                            ui.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
                    }
                    capture("public-ready")
                    ui.onNodeWithText("Use the player controls to start playback.").assertIsDisplayed()
                    ui.onNodeWithText("Playing YouTube video.").assertDoesNotExist()
                    val label = Pattern.compile("Play(?: video)?", Pattern.CASE_INSENSITIVE)
                    val play = device.wait(Until.findObject(By.desc(label)), 15_000)
                        ?: device.wait(Until.findObject(By.text(label)), 5_000)
                    requireNotNull(play) { "The public provider Play control was not exposed" }.click()
                    ui.waitUntil(30_000) { ui.onAllNodesWithText("Playing YouTube video.").fetchSemanticsNodes().isNotEmpty() }
                    capture("public-playing")
                    ui.onNodeWithText("Close video").performClick()
                    ui.onNodeWithText("All posts").assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("public-failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "video-evidence/$name.png").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, "$name.xml"))
    }
}

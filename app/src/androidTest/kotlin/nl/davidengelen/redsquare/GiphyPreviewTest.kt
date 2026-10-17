package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Provider HTTP and Saito are owned peers. No live key or public GIF search. */
class GiphyPreviewTest {
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

    @Test fun providerFailureCanRetryAndCloseWithoutLosingTheDraft() {
        val reference = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val peer = ControlledFeedPeer("[${reference.getAsJsonObject("ordinary")}]")
        MockWebServer().use { provider ->
            provider.enqueue(MockResponse.Builder().code(503).build())
            provider.enqueue(MockResponse.Builder().code(503).build())
            provider.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("giphyFixtureUrl", "http://127.0.0.1:${provider.port}")).use { scenario ->
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                            ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("New post").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("My authored work survives GIF search")
                        assertEquals(0, provider.requestCount)
                        ui.onNodeWithText("Add GIF").performScrollTo().assertIsDisplayed().performClick()
                        ui.waitUntil(10_000) { provider.requestCount == 1 &&
                            ui.onAllNodesWithText("GIF search is unavailable. Try again.").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Powered by GIPHY").assertIsDisplayed()
                        capture("provider-failure")
                        ui.onNodeWithText("Retry").performClick()
                        ui.waitUntil(10_000) { provider.requestCount == 2 &&
                            ui.onAllNodesWithText("GIF search is unavailable. Try again.").fetchSemanticsNodes().isNotEmpty() }
                        repeat(2) {
                            val request = requireNotNull(provider.takeRequest(2, TimeUnit.SECONDS))
                            assertEquals("GET", request.method)
                            assertEquals("/v1/gifs/trending", request.url.encodedPath)
                            assertEquals("owned-test-only", request.url.queryParameter("api_key"))
                        }
                        ui.onNodeWithText("Close GIF search").performClick()
                        ui.onNode(hasSetTextAction()).assert(hasText("My authored work survives GIF search"))
                        ui.onNodeWithText("Save draft").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                        scenario.recreate()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("New post").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).assert(hasText("My authored work survives GIF search"))
                        assertEquals(2, provider.requestCount)
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                        capture("restored-draft")
                    } catch (failure: Throwable) {
                        try { capture("failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "giphy-evidence/$name.png").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, "$name.xml"))
    }
}

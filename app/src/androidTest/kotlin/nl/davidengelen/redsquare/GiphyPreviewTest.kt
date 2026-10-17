package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
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

    @Test fun selectedAnimatedGifSurvivesSavedDraftAndFreshActivity() {
        val reference = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val gif = instrumentation.context.assets.open("owned-giphy-animation.gif").use { it.readBytes() }
        val thumbnail = android.util.Base64.decode(reference.getAsJsonObject("images").get("red").asString.substringAfter(','), android.util.Base64.NO_WRAP)
        val paths = ConcurrentLinkedQueue<String>()
        val peer = ControlledFeedPeer("[${reference.getAsJsonObject("ordinary")}]")
        MockWebServer().use { provider ->
            provider.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val originalPath = "/media/original.gif?token=owned%2Bonly&keep=1"
            val original = "http://127.0.0.1:${provider.port}$originalPath"
            provider.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths.add(request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: ""))
                    return when (request.url.encodedPath) {
                        "/v1/gifs/trending" -> MockResponse.Builder().setHeader("Content-Type", "application/json").body("""
                            {"meta":{"status":200},"pagination":{"offset":0,"count":1,"total_count":1},"data":[{
                              "id":"owned-two-frame","alt_text":"Owned two-frame GIF","title":"Owned animation",
                              "images":{"original":{"url":"$original"},"fixed_width_small":{"url":"http://127.0.0.1:${provider.port}/media/thumbnail.png"}},
                              "user":{"display_name":"Owned fixture artist","profile_url":"https://giphy.com/owned-fixture"},
                              "source_post_url":"https://example.com/owned-fixture"
                            }]}
                        """.trimIndent()).build()
                        "/media/thumbnail.png" -> MockResponse.Builder().setHeader("Content-Type", "image/png")
                            .body(Buffer().write(thumbnail)).build()
                        "/media/original.gif" -> MockResponse.Builder()
                            .setHeader("Content-Type", "image/gif").body(Buffer().write(gif)).build()
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
            peer.start().use { server ->
                val launchIntent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("giphyFixtureUrl", "http://127.0.0.1:${provider.port}")
                ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                    var originalActivity = 0
                    scenario.onActivity { originalActivity = System.identityHashCode(it) }
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                            ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("New post").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("My animated GIF draft")
                        assertEquals(0, provider.requestCount)
                        ui.onNodeWithText("Add GIF").performScrollTo().performClick()
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Owned two-frame GIF").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Powered by GIPHY").assertIsDisplayed()
                        ui.onNodeWithText("By Owned fixture artist").assertIsDisplayed()
                        ui.onNodeWithText("Source").assertIsDisplayed()
                        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("GIF preview: Owned two-frame GIF").fetchSemanticsNodes().isNotEmpty() }
                        val preview = ui.onNodeWithContentDescription("GIF preview: Owned two-frame GIF").performScrollTo().assertIsDisplayed()
                        ui.waitUntil(10_000) {
                            val bitmap = preview.captureToImage().asAndroidBitmap()
                            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == 0xffdc1e28.toInt()
                        }
                        assertGifContained("GIF preview: Owned two-frame GIF", "search-preview")
                        capture("search-preview")
                        paths.clear()
                        ui.onNodeWithText("Use GIF").performScrollTo().assertIsDisplayed().performClick()
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Close GIF search").fetchSemanticsNodes().isEmpty() }
                        ui.onNode(hasSetTextAction()).assert(hasText("My animated GIF draft"))
                        assertAnimation("Image 1 of 1")
                        assertGifContained("Image 1 of 1", "selected-image")
                        assertTrue(paths.contains(originalPath))
                        assertSelectedCredits("selected-credits")
                        ui.onNodeWithText("Save draft").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                        scenario.close()
                        ActivityScenario.launch<MainActivity>(launchIntent).use { fresh ->
                            fresh.onActivity { assertNotEquals("Reopening must use a new Activity", originalActivity, System.identityHashCode(it)) }
                            try {
                                ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                                paths.clear()
                                ui.onNodeWithText("New post").performClick()
                                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                ui.onNode(hasSetTextAction()).assert(hasText("My animated GIF draft"))
                                assertAnimation("Image 1 of 1")
                                assertTrue("Restoration must freshly load the exact original URL", paths.contains(originalPath))
                                assertGifContained("Image 1 of 1", "restored-image")
                                capture("animated-restored-draft")
                                assertSelectedCredits("fresh-activity-credits")
                                ui.onNodeWithText("Remove image 1").performScrollTo().performClick()
                                ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isEmpty() }
                                ui.onNode(hasSetTextAction()).assert(hasText("My animated GIF draft"))
                                ui.onNodeWithText("By Owned fixture artist").assertDoesNotExist()
                                ui.onNodeWithText("Source").assertDoesNotExist()
                                ui.onNodeWithText("Powered by GIPHY").assertDoesNotExist()
                                assertTrue(peer.transactions.isEmpty())
                                peer.assertHealthy()
                            } catch (failure: Throwable) {
                                try { capture("fresh-activity-failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                                throw failure
                            }
                        }
                    } catch (failure: Throwable) {
                        try { capture("animation-failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        }
    }

    private fun assertSelectedCredits(name: String) {
        ui.onNodeWithText("Powered by GIPHY").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("By Owned fixture artist").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Source").performScrollTo().assertIsDisplayed()
        capture(name)
    }

    private fun assertGifContained(label: String, name: String) {
        ui.waitForIdle()
        ui.onNodeWithContentDescription(label).assertIsDisplayed()
        // The UiDevice bitmap and accessibility bounds both use screen coordinates.
        val bounds = requireNotNull(UiDevice.getInstance(instrumentation).findObject(By.desc(label))).visibleBounds
        assertFalse("The image must have visible screen bounds", bounds.isEmpty)
        capture("$name-bounds")
        val screenshot = android.graphics.BitmapFactory.decodeFile(File(context.cacheDir, "giphy-evidence/$name-bounds.png").path)
        try {
            val x = bounds.centerX()
            assertTrue("The image boundary x sample must be visible", x in 0 until screenshot.width)
            fun isFixturePixel(pixel: Int): Boolean {
                val red = kotlin.math.abs(Color.red(pixel) - 220) <= 5 &&
                    kotlin.math.abs(Color.green(pixel) - 30) <= 5 && kotlin.math.abs(Color.blue(pixel) - 40) <= 5
                val blue = kotlin.math.abs(Color.red(pixel) - 20) <= 5 &&
                    kotlin.math.abs(Color.green(pixel) - 60) <= 5 && kotlin.math.abs(Color.blue(pixel) - 220) <= 5
                return red || blue
            }
            assertTrue("The image center sample must be visible", bounds.centerY() in 0 until screenshot.height)
            assertTrue("The same window screenshot must positively contain the GIF",
                isFixturePixel(screenshot.getPixel(x, bounds.centerY())))
            for (y in listOf(bounds.top - 8, bounds.bottom + 8)) {
                assertTrue("The image boundary sample must be visible", y in 0 until screenshot.height)
                assertFalse("GIF pixels must stay inside the image bounds ($label, y=$y, bounds=$bounds)",
                    isFixturePixel(screenshot.getPixel(x, y)))
            }
        } finally { screenshot.recycle() }
    }

    private fun assertAnimation(label: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        val image = ui.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
        var red = false
        var blue = false
        ui.waitUntil(10_000) {
            val bitmap = image.captureToImage().asAndroidBitmap()
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            red = red || (kotlin.math.abs(Color.red(pixel) - 220) <= 5 &&
                kotlin.math.abs(Color.green(pixel) - 30) <= 5 && kotlin.math.abs(Color.blue(pixel) - 40) <= 5)
            blue = blue || (kotlin.math.abs(Color.red(pixel) - 20) <= 5 &&
                kotlin.math.abs(Color.green(pixel) - 60) <= 5 && kotlin.math.abs(Color.blue(pixel) - 220) <= 5)
            red && blue
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "giphy-evidence/$name.png").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, "$name.xml"))
    }
}

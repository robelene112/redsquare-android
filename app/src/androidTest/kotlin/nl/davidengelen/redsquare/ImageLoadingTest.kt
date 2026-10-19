package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class ImageLoadingTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("feed.") || it.name.startsWith("postdraft.") }
            ?.forEach { check(it.delete()) }
    }

    private fun fixture() = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
        .use { JsonParser.parseReader(it).asJsonObject }

    @Test fun remoteFailuresKeepVerifiedTextReadableAndRetryCanRecoverOneImage() {
        val fixture = fixture()
        val missing = AtomicInteger()
        val red = android.util.Base64.decode(fixture.getAsJsonObject("images").get("red").asString.substringAfter(','), android.util.Base64.NO_WRAP)
        val media = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/missing.png" -> if (missing.incrementAndGet() == 1) MockResponse.Builder().code(404).build()
                        else MockResponse.Builder().body(Buffer().write(red)).build()
                    "/oversize.png" -> MockResponse.Builder().body(Buffer().write(ByteArray(2 * 1024 * 1024 + 1))).build()
                    "/malformed.png" -> MockResponse.Builder().body("Not an image").build()
                    else -> error("Unexpected media request")
                }
            }
            start(InetAddress.getByName("127.0.0.1"), 19125)
        }
        media.use {
            val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("remoteFailure")}]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.waitUntil(15_000) { ui.onAllNodesWithText("Image unavailable").fetchSemanticsNodes().size == 3 }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Remote image failures keep this post readable"))
                        ui.onNodeWithText("Remote image failures keep this post readable").assertIsDisplayed()
                        ui.onAllNodesWithText("Retry")[0].performScrollTo().assertIsDisplayed().performClick()
                        ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Image 1 of 3").fetchSemanticsNodes().isNotEmpty() }
                        assertColor("Image 1 of 3", intArrayOf(220,30,40))
                        ui.onAllNodesWithText("Image unavailable").assertCountEquals(2)
                        assertEquals(2, missing.get()); assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                        capture("remote-retry.png")
                    } catch (failure: Throwable) { capture("remote-failure.png"); throw failure }
                }
            }
        }
    }

    @Test fun leavingStalledDownloadsKeepsNavigationUsableAndReleasesImageCapacity() {
        val fixture = fixture()
        val requests = AtomicInteger()
        val media = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.url.encodedPath in listOf("/slow-a.png", "/slow-b.png"))
                    requests.incrementAndGet()
                    return MockResponse.Builder().onResponseStart(SocketEffect.Stall).build()
                }
            }
            start(InetAddress.getByName("127.0.0.1"), 19125)
        }
        media.use {
            val owner = ControlledFeedPeer("[${fixture.getAsJsonObject("remoteSlow")}]")
            val official = ControlledFeedPeer("[${fixture.getAsJsonObject("remoteFast")}]")
            owner.start().use { ownerServer -> official.start().use { officialServer ->
                // Start before either call can begin, excluding a natural timeout as a pass.
                val began = SystemClock.uptimeMillis()
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use {
                    try {
                        ui.waitUntil(10_000) { requests.get() == 2 }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Slow images keep navigation usable"))
                        ui.onNodeWithText("Slow images keep navigation usable").assertIsDisplayed()
                        ui.onNodeWithText("Settings").performClick()
                        ui.waitUntil(5_000) { ui.onAllNodesWithText("saito.io").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("saito.io").performScrollTo().performClick()
                        ui.waitUntil(8_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isNotEmpty() }
                        assertTrue("New image must not wait for obsolete 12-second HTTP timeouts", SystemClock.uptimeMillis() - began < 10_000)
                        assertColor("Image 1 of 1", intArrayOf(20,60,220))
                        ui.onNodeWithText("Node: saito.io").assertExists()
                        assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                        owner.assertHealthy(); official.assertHealthy()
                        capture("switched-after-stalled-images.png")
                    } catch (failure: Throwable) { capture("stalled-failure.png"); throw failure }
                }
            } }
        }
    }

    private fun assertColor(label: String, expected: IntArray) {
        ui.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(label))
        val bitmap = ui.onNodeWithContentDescription(label).assertIsDisplayed().captureToImage().asAndroidBitmap()
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).forEachIndexed { index, value ->
            assertTrue(kotlin.math.abs(value - expected[index]) <= 5)
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "images-evidence/$name").apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }
}

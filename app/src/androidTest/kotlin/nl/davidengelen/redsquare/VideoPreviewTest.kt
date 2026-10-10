package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class VideoPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun videoContactsProviderOnlyAfterTapAndClosingPreservesThePost() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val tx = SaitoTransaction.create(wallet, RedSquarePosts.message("Video reference https://youtu.be/M7lc1UVf-VE"), 1700000900000)
        val row = JsonObject().apply { addProperty("tx", tx.envelope()); addProperty("created_at", 1700000900000L) }
        val peer = ControlledFeedPeer("[$row]")
        val api = MockWebServer()
        api.enqueue(readyResponse())
        api.use {
            api.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("youtubeFixtureUrl", "http://127.0.0.1:${api.port}/iframe_api")).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodesWithText("Video reference https://youtu.be/M7lc1UVf-VE").fetchSemanticsNodes().isNotEmpty() }
                        assertEquals(0, api.requestCount)
                        watch("Video reference https://youtu.be/M7lc1UVf-VE")
                        ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                        assertEquals(1, api.requestCount)
                        val request = requireNotNull(api.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
                        assertEquals("https://${context.packageName}/", request.headers["Referer"])
                        capture("video-evidence/tapped-player.png")
                        ui.onNodeWithText("Close video").performClick()
                        ui.onNodeWithText("Video reference https://youtu.be/M7lc1UVf-VE").assertExists()
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                    } catch (failure: Throwable) {
                        failure.addSuppressed(AssertionError("Controlled provider requests: ${api.requestCount}"))
                        try { capture("video-evidence/tap-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        }
    }

    @Test fun selectedVideoSurvivesRotationWithAnExternalFallbackInSmallWindows() {
        val reference = instrumentation.context.assets.open("native-video-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val row = reference.getAsJsonObject("linked")
        val peer = ControlledFeedPeer("[$row]")
        val device = UiDevice.getInstance(instrumentation)
        val rotation = AutoCloseable {
            var failure: Throwable? = null
            for (restore in listOf<() -> Unit>({ device.setOrientationNatural() }, { device.unfreezeRotation() })) {
                try { restore() } catch (problem: Throwable) {
                    if (failure == null) failure = problem else failure.addSuppressed(problem)
                }
            }
            failure?.let { throw it }
        }
        rotation.use {
            val api = MockWebServer()
            api.use {
                api.enqueue(readyResponse())
                api.enqueue(readyResponse())
                api.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { server ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("youtubeFixtureUrl", "http://127.0.0.1:${api.port}/iframe_api")).use {
                        try {
                            stage("size: launched")
                            ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodesWithText("Explicit signed YouTube link").fetchSemanticsNodes().isNotEmpty() }
                            if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                            assertEquals(0, api.requestCount)
                            watch("Explicit signed YouTube link")
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(1, api.requestCount)
                            stage("size: portrait player ready, rotating")
                            device.setOrientationLeft()
                            ui.waitUntil(10_000) { device.displayWidth > device.displayHeight }
                            stage("size: landscape display, waiting for fallback")
                            ui.waitUntil(10_000) { ui.onAllNodesWithText("Open this video in YouTube or use a larger window.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(1, api.requestCount)
                            assertFalse(device.hasObject(By.desc("YouTube player")))
                            ui.onNodeWithText("Open in YouTube").assertIsDisplayed()
                            ui.onNodeWithText("Close video").assertIsDisplayed()
                            capture("video-evidence/small-window.png")
                            ui.onNodeWithText("Close video").performClick()
                            stage("size: closed fallback, returning to portrait")
                            device.setOrientationNatural()
                            ui.waitUntil(10_000) { device.displayHeight > device.displayWidth && ui.onAllNodesWithText("Explicit signed YouTube link").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(1, api.requestCount)
                            watch("Explicit signed YouTube link")
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                            val player = requireNotNull(device.wait(Until.findObject(By.desc("YouTube player")), 5_000))
                            val density = context.resources.displayMetrics.density
                            ui.waitUntil(5_000) {
                                device.findObject(By.desc("YouTube player"))?.visibleBounds?.let {
                                    it.width() / density >= 200 && it.height() / density >= 200
                                } == true
                            }
                            val bounds = player.visibleBounds
                            assertTrue("Player width ${bounds.width()}px, density $density", bounds.width() / density >= 200)
                            assertTrue("Player height ${bounds.height()}px, density $density", bounds.height() / density >= 200)
                            assertEquals(2, api.requestCount)
                            capture("video-evidence/portrait-bounds.png")
                            ui.onNodeWithText("Close video").performClick()
                            assertTrue(peer.transactions.isEmpty())
                            peer.assertHealthy()
                        } catch (failure: Throwable) {
                            failure.addSuppressed(AssertionError("Controlled provider requests: ${api.requestCount}"))
                            try { capture("video-evidence/size-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                            throw failure
                        }
                    }
                }
            }
        }
    }

    private fun readyResponse() = MockResponse.Builder().setHeader("Content-Type", "application/javascript").body("""
        window.YT={Player:function(element,options){
          var target=this;
          if(options.playerVars.autoplay!==0){options.events.onError({data:2});return;}
          target.cueVideoById=function(id){if(id!=='M7lc1UVf-VE')throw Error('Wrong video');window.referenceCued=id};
          target.pauseVideo=function(){};
          document.getElementById(element).style.color='white';
          document.getElementById(element).innerText='Controlled YouTube player';
          setTimeout(function(){options.events.onReady({target:target})},20);
        }};
        onYouTubeIframeAPIReady();
    """.trimIndent()).build()

    private fun watch(postText: String) {
        val button = hasText("Watch on YouTube") and hasAnyAncestor(hasAnyChild(hasText(postText) and hasClickAction()))
        ui.onNode(hasScrollAction()).performScrollToNode(button)
        ui.onNode(button).assertIsDisplayed().performClick()
    }

    private fun stage(message: String) {
        File(context.cacheDir, "video-evidence/stages.txt").apply { parentFile?.mkdirs() }
            .appendText("${android.os.SystemClock.elapsedRealtime()} $message\n")
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, name).apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    }
}

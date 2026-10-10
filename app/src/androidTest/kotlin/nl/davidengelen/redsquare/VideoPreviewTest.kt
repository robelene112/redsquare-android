package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
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
        api.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/javascript").body("""
            window.YT={Player:function(element,options){
              var target=this;
              if(options.playerVars.autoplay!==0){options.events.onError({data:2});return;}
              target.cueVideoById=function(id){if(id!=='M7lc1UVf-VE')throw Error('Wrong video');window.referenceCued=id};
              target.pauseVideo=function(){};
              document.getElementById(element).innerText='Controlled YouTube player';
              setTimeout(function(){options.events.onReady({target:target})},20);
            }};
            onYouTubeIframeAPIReady();
        """.trimIndent()).build())
        api.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("youtubeFixtureUrl", api.url("/iframe_api").toString())).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodesWithText("Video reference https://youtu.be/M7lc1UVf-VE").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Watch on YouTube"))
                        ui.onNodeWithText("Watch on YouTube").assertIsDisplayed()
                        assertEquals(0, api.requestCount)
                        ui.onNodeWithText("Watch on YouTube").performClick()
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
                        try { capture("video-evidence/tap-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        } finally { api.close() }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, name).apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }
}

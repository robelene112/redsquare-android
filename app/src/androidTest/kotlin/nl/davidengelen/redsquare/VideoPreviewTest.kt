package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
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
import org.hamcrest.CoreMatchers.allOf
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
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 && ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
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
                        captureFailure(failure, "video-evidence/tap-failure.png")
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
                            ui.waitUntil(20_000) { peer.requests.get() >= 2 && ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
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
                            assertFalse(device.hasObject(By.clazz("android.webkit.WebView")))
                            ui.onNodeWithText("Open in YouTube").assertIsDisplayed()
                            ui.onNodeWithText("Close video").assertIsDisplayed()
                            capture("video-evidence/small-window.png")
                            ui.onNodeWithText("Close video").performClick()
                            stage("size: closed fallback, returning to portrait")
                            device.setOrientationNatural()
                            ui.waitUntil(10_000) { device.displayHeight > device.displayWidth && ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(1, api.requestCount)
                            watch("Explicit signed YouTube link")
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                            val player = requireNotNull(device.wait(Until.findObject(By.clazz("android.webkit.WebView")), 5_000))
                            val density = context.resources.displayMetrics.density
                            ui.waitUntil(5_000) {
                                device.findObject(By.clazz("android.webkit.WebView"))?.visibleBounds?.let {
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
                            captureFailure(failure, "video-evidence/size-failure.png")
                            throw failure
                        }
                    }
                }
            }
        }
    }

    @Test fun aThreadVideoCanRetryProviderFailuresAndOpenItsCanonicalExternalLink() {
        val reference = instrumentation.context.assets.open("native-video-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val row = reference.getAsJsonObject("text")
        val postText = "Signed YouTube reference https://www.youtube.com/watch?v=M7lc1UVf-VE"
        val threadReads = java.util.concurrent.atomic.AtomicInteger()
        val peer = ControlledFeedPeer("[$row]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            check(criteria.get("flagged_ne").asInt == 1)
            check(criteria.get("limit").asInt in 1..100)
            when {
                criteria.has("field4") -> check(criteria.get("field4").asString == "")
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == row.get("sig").asString)
                    threadReads.incrementAndGet()
                }
                else -> error("Unexpected video archive query")
            }
            "[$row]"
        })
        val api = MockWebServer()
        api.use {
            api.enqueue(MockResponse.Builder().code(503).build())
            api.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/javascript").body("""
                window.YT={Player:function(element,options){
                  this.cueVideoById=function(id){if(id!=='M7lc1UVf-VE')throw Error('Wrong reference')};
                  var player=this;
                  setTimeout(function(){
                    options.events.onError({data:153});
                    options.events.onStateChange({data:5});
                    options.events.onReady({target:player});
                  },20);
                }};
                onYouTubeIframeAPIReady();
            """.trimIndent()).build())
            api.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/javascript").body("""
                window.YT={Player:function(element,options){
                  var target=this,root=document.getElementById(element);
                  target.cueVideoById=function(id){if(id!=='M7lc1UVf-VE')throw Error('Wrong reference');};
                  target.pauseVideo=function(){};
                  var button=document.createElement('button');button.innerText='Play controlled refused video';
                  root.appendChild(button);
                  button.onclick=function(event){
                    if(!event.isTrusted)return;
                    options.events.onError({data:150});
                    setTimeout(function(){root.style.color='white';root.innerText='Controlled provider explanation remains visible.';},1200);
                  };
                  setTimeout(function(){options.events.onReady({target:target});},20);
                }};
                onYouTubeIframeAPIReady();
            """.trimIndent()).build())
            api.enqueue(readyResponse())
            api.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { server ->
                Intents.init()
                val intents = AutoCloseable { Intents.release() }
                intents.use {
                    Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
                        android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, null))
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("youtubeFixtureUrl", "http://127.0.0.1:${api.port}/iframe_api")).use {
                        try {
                            ui.waitUntil(20_000) { peer.requests.get() >= 2 && ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                            if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                            ui.onNode(hasScrollAction()).performScrollToNode(hasText(postText))
                            ui.onNodeWithText(postText).performClick()
                            ui.waitUntil(20_000) { threadReads.get() > 0 && ui.onAllNodesWithText("Conversation").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(0, api.requestCount)
                            watch(postText)
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("YouTube could not load. Retry or open the video in YouTube.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(1, api.requestCount)
                            ui.onNodeWithText("Retry").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("YouTube could not verify this player. Try opening the video in YouTube.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(2, api.requestCount)
                            capture("video-evidence/provider-error.png")
                            ui.onNodeWithText("Open in YouTube").performClick()
                            Intents.intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://www.youtube.com/watch?v=M7lc1UVf-VE")))
                            ui.onNodeWithText("Retry").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(3, api.requestCount)
                            val device = UiDevice.getInstance(instrumentation)
                            requireNotNull(device.wait(Until.findObject(By.text("Play controlled refused video")), 10_000)).click()
                            ui.waitUntil(10_000) { ui.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
                            assertTrue("The provider explanation must remain visible after an embedding refusal",
                                device.wait(Until.hasObject(By.text("Controlled provider explanation remains visible.")), 5_000))
                            capture("video-evidence/embed-refused-explanation.png")
                            ui.onNodeWithText("Retry").performClick()
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                            assertEquals(4, api.requestCount)
                            ui.onNodeWithText("Close video").performClick()
                            ui.onNodeWithText("Conversation").assertIsDisplayed()
                            ui.onNodeWithText(postText).assertExists()
                            ui.onNodeWithText("Back").performClick()
                            ui.onNodeWithText("All posts").assertIsDisplayed()
                            assertTrue(peer.transactions.isEmpty())
                            peer.assertHealthy()
                        } catch (failure: Throwable) {
                            failure.addSuppressed(AssertionError("Provider requests ${api.requestCount}; thread reads ${threadReads.get()}"))
                            captureFailure(failure, "video-evidence/provider-failure.png")
                            throw failure
                        }
                    }
                }
            }
        }
    }

    @Test fun userPlaybackPausesInTheBackgroundAndRecreationDoesNotAutoplay() {
        val reference = instrumentation.context.assets.open("native-video-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val row = reference.getAsJsonObject("linked")
        val peer = ControlledFeedPeer("[$row]")
        val device = UiDevice.getInstance(instrumentation)
        val response = MockResponse.Builder().setHeader("Content-Type", "application/javascript").body("""
            window.YT={Player:function(element,options){
              var target=this,permission=false;
              if(options.playerVars.autoplay!==0){options.events.onError({data:2});return;}
              var button=document.createElement('button');
              button.innerText='Play controlled video';
              document.getElementById(element).appendChild(button);
              target.cueVideoById=function(id){if(id!=='M7lc1UVf-VE')throw Error('Wrong video');};
              target.playVideo=function(){
                if(!permission){options.events.onError({data:5});return;}
                permission=false;button.innerText='Playing controlled video';
                options.events.onStateChange({data:1});
              };
              target.pauseVideo=function(){
                permission=false;
                if(button.innerText==='Playing controlled video'){
                  button.innerText='Paused controlled video';options.events.onStateChange({data:2});
                }
              };
              button.onclick=function(event){if(event.isTrusted){permission=true;target.playVideo();}};
              setTimeout(function(){options.events.onReady({target:target})},20);
            }};
            onYouTubeIframeAPIReady();
        """.trimIndent()).build()
        val api = MockWebServer()
        api.use {
            api.enqueue(response)
            api.enqueue(response)
            api.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("youtubeFixtureUrl", "http://127.0.0.1:${api.port}/iframe_api")).use { scenario ->
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 && ui.onAllNodesWithText("Loading posts…").fetchSemanticsNodes().isEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        assertEquals(0, api.requestCount)
                        watch("Explicit signed YouTube link")
                        ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                        assertEquals(1, api.requestCount)
                        requireNotNull(device.wait(Until.findObject(By.text("Play controlled video")), 10_000)).click()
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Playing YouTube video.").fetchSemanticsNodes().isNotEmpty() }
                        assertTrue(device.wait(Until.hasObject(By.text("Playing controlled video")), 10_000))
                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                        assertTrue(device.wait(Until.hasObject(By.text("Paused controlled video")), 10_000))
                        ui.waitUntil(10_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                        assertEquals(1, api.requestCount)
                        capture("video-evidence/paused-player.png")
                        scenario.recreate()
                        ui.waitUntil(20_000) { ui.onAllNodesWithText("Use the player controls to start playback.").fetchSemanticsNodes().isNotEmpty() }
                        assertTrue(device.wait(Until.hasObject(By.text("Play controlled video")), 10_000))
                        assertFalse(device.hasObject(By.text("Playing controlled video")))
                        assertEquals(2, api.requestCount)
                        capture("video-evidence/recreated-player.png")
                        ui.onNodeWithText("Close video").performClick()
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                    } catch (failure: Throwable) {
                        failure.addSuppressed(AssertionError("Controlled provider requests: ${api.requestCount}"))
                        captureFailure(failure, "video-evidence/lifecycle-failure.png")
                        throw failure
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

    private fun captureFailure(failure: Throwable, name: String) {
        try { capture(name) } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, name).apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    }
}

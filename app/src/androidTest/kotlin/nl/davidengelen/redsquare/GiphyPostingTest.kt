package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real picker, signing and socket writes, confined to owned HTTP/Saito peers. */
class GiphyPostingTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter { file ->
            listOf("postdraft.", "feed.", "conversation.", "profile.", "quoteoriginal.").any(file.name::startsWith)
        }?.forEach { check(it.delete()) }
    }

    @Test fun ownerGifPost() = publish("post", SaitoBackend.OWNER, verifyPublishedCredits = true)
    @Test fun ownerGifReply() = publish("reply", SaitoBackend.OWNER)
    @Test fun ownerGifQuote() = publish("quote", SaitoBackend.OWNER)
    @Test fun officialGifPost() = publish("post", SaitoBackend.OFFICIAL)
    @Test fun officialGifReply() = publish("reply", SaitoBackend.OFFICIAL)
    @Test fun officialGifQuote() = publish("quote", SaitoBackend.OFFICIAL)

    private fun publish(kind: String, backend: SaitoBackend, verifyPublishedCredits: Boolean = false) {
        AndroidBackendStore(context).save(backend)
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val original = fixture.getAsJsonObject("ordinary").deepCopy().apply {
            addProperty("field1", "RedSquare"); addProperty("field4", ""); addProperty("field5", get("sig").asString)
        }
        val gif = instrumentation.context.assets.open("owned-giphy-animation.gif").use { it.readBytes() }
        val thumbnail = android.util.Base64.decode(fixture.getAsJsonObject("images").get("red").asString.substringAfter(','), android.util.Base64.NO_WRAP)
        val indexed = AtomicReference<JsonObject?>()
        val text = "Native GIF $kind ${backend.id}"
        val label = "${backend.id}-$kind"
        MockWebServer().use { provider ->
            provider.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val source = "http://127.0.0.1:${provider.port}/media/published.gif?token=owned%2Bonly&keep=1"
            provider.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/v1/gifs/trending" -> MockResponse.Builder().setHeader("Content-Type", "application/json").body("""
                        {"meta":{"status":200},"data":[{"id":"owned-published-gif","title":"Owned posting GIF",
                          "images":{"original":{"url":"$source"},"fixed_width_small":{"url":"http://127.0.0.1:${provider.port}/media/thumb.png"}},
                          "user":{"display_name":"Owned publisher","profile_url":"https://giphy.com/owned-publisher"},
                          "source":"https://example.com/owned-publisher"}]}
                    """.trimIndent()).build()
                    "/media/thumb.png" -> MockResponse.Builder().setHeader("Content-Type", "image/png").body(Buffer().write(thumbnail)).build()
                    "/media/published.gif" -> MockResponse.Builder().setHeader("Content-Type", "image/gif").body(Buffer().write(gif)).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
            val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
                val candidate = postingTransaction(frame)
                val message = SaitoEnvelope.decode(candidate.envelope())
                val data = message.body.getAsJsonObject("data")
                assertEquals("RedSquare", message.body.get("module").asString)
                assertEquals("create tweet", message.body.get("request").asString)
                assertEquals(text, data.get("text").asString)
                assertEquals(listOf(source), data.getAsJsonArray("images").map { it.asString })
                assertFalse(data.has("gifAttributions"))
                assertFalse(message.body.has("gifAttributions"))
                assertTrue(message.body.toString().toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
                assertEquals((if (kind == "post") listOf(message.author)
                    else listOf(message.author, original.get("field2").asString)).distinct(), message.recipients)
                if (kind == "reply") {
                    assertEquals(original.get("sig").asString, data.get("parent_id").asString)
                    assertEquals(original.get("sig").asString, data.get("thread_id").asString)
                } else assertFalse(data.has("parent_id"))
                if (kind == "quote") {
                    assertEquals(original.get("sig").asString, data.get("signature").asString)
                    assertEquals(original.get("tx").asString, data.get("retweet_tx").asString)
                    assertEquals(SaitoEnvelope.decode(original.get("tx").asString).body.getAsJsonObject("data").getAsJsonArray("images"),
                        data.getAsJsonObject("embedded").getAsJsonArray("images"))
                } else assertFalse(data.has("retweet_tx"))
                File(context.cacheDir, "giphy-posting-evidence/$label-frame.base64").apply { parentFile?.mkdirs() }
                    .writeText(java.util.Base64.getEncoder().encodeToString(frame))
                File(context.cacheDir, "giphy-posting-evidence/$label-source.txt").writeText(source)
                indexed.set(postingArchiveRow(candidate))
            }, archiveResponse = { criteria ->
                if (criteria.get("field1").asString != "RedSquare") "[]"
                else listOfNotNull(indexed.get(), original).filter { row ->
                    (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                        (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                        (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5").asString)
                }.joinToString(prefix = "[", postfix = "]")
            })
            peer.start().use { server ->
                val launchIntent = Intent(context, MainActivity::class.java)
                    .putExtra(if (backend == SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl",
                        "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("giphyFixtureUrl", "http://127.0.0.1:${provider.port}")
                ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        waitFor("Signed image reference")
                        ui.onNodeWithText("Node: ${backend.domain}").assertIsDisplayed()
                        when (kind) {
                            "post" -> ui.onNode(hasText("New post") and hasClickAction()).performClick()
                            "reply" -> {
                                ui.clickVisiblePostBody("Signed image reference")
                                waitFor("Conversation")
                                ui.onNodeWithText("Reply").performClick()
                            }
                            "quote" -> ui.clickVisiblePostContent("Quote")
                        }
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement(text)
                        ui.onNodeWithText("Add GIF").performScrollTo().performClick()
                        waitFor("Owned posting GIF")
                        ui.onNodeWithText("Use GIF").performScrollTo().performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Remove image 1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNode(hasText("Send") and isEnabled()).performClick()
                        ui.waitUntil(20_000) { peer.assertHealthy(); ui.onAllNodesWithText("Available on this node.").fetchSemanticsNodes().isNotEmpty() }
                        assertEquals(1, peer.transactions.size)
                        capture("$label-available")
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        val requests = peer.requests.get()
                        ui.onNodeWithText("Refresh").performClick()
                        ui.waitUntil(20_000) { peer.requests.get() > requests && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                        ui.onNodeWithText(text).assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        peer.assertHealthy()
                        capture("$label-indexed")
                        if (verifyPublishedCredits) {
                            assertPublishedCredits("$label-published-credits")
                            // Reopen the disposable composer, then leave and launch a fresh Activity.
                            ui.onNode(hasText("New post") and hasClickAction()).performClick()
                            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                                androidx.compose.ui.text.AnnotatedString("")))
                            ui.onNodeWithText("Back").performClick()
                            scenario.close()
                            ActivityScenario.launch<MainActivity>(launchIntent).use {
                                try {
                                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                                    ui.onNodeWithText(text).assertIsDisplayed()
                                    assertPublishedCredits("$label-fresh-published-credits")
                                    assertEquals(1, peer.transactions.size)
                                    peer.assertHealthy()
                                } catch (failure: Throwable) {
                                    try { capture("$label-fresh-published-failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                                    throw failure
                                }
                            }
                        }
                    } catch (failure: Throwable) {
                        try { capture("$label-failure") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        }
    }

    private fun assertPublishedCredits(name: String) {
        ui.waitUntil(10_000) { ui.onAllNodesWithText("By Owned publisher").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("By Owned publisher"))
        ui.onNodeWithText("By Owned publisher").assertIsDisplayed()
        ui.onNodeWithText("Source").assertIsDisplayed()
        capture(name)
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun capture(name: String) {
        val png = File(context.cacheDir, "giphy-posting-evidence/$name.png").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(png))
        device.dumpWindowHierarchy(File(png.parentFile, "$name.xml"))
    }
}

package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real composition/signing/socket flow; only the external image picker result is controlled. */
class ImagePostingTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") ||
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") ||
            it.name.startsWith("profile.") || it.name.startsWith("quoteoriginal.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun imageOnlyPostSignsAndServesItsOrderedPreparedImages() = publish("post", "", listOf("red", "blue"))
    @Test fun imageReplyKeepsItsParentThreadRecipientsAndAttachment() = publish("reply", "Native image reply", listOf("green"))
    @Test fun imageQuoteKeepsItsOwnImagesAndExactAuthenticatedOriginal() = publish("quote", "Native image quote", listOf("green"))

    private fun publish(kind: String, text: String, colors: List<String>) {
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val original = fixture.getAsJsonObject("ordinary").deepCopy().apply {
            addProperty("field1", "RedSquare")
            addProperty("field4", "")
            addProperty("field5", get("sig").asString)
        }
        val indexed = AtomicReference<JsonObject?>()
        val uris = mutableListOf<Uri>()
        withImageFixtureCleanup({ cleanupSyntheticImages(context.contentResolver, uris.toList()) }) {
            colors.forEachIndexed { index, color ->
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-$kind-$index.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }))
                uris.add(uri)
                context.contentResolver.openOutputStream(uri)!!.use { output ->
                    output.write(android.util.Base64.decode(fixture.getAsJsonObject("images").get(color).asString.substringAfter(','), android.util.Base64.NO_WRAP))
                }
            }
            val result = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size > 1) result.clipData = ClipData.newRawUri("Synthetic images", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            Intents.init()
            try {
                Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                    hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                    hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                    .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
                val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
                    val candidate = postingTransaction(frame)
                    val message = SaitoEnvelope.decode(candidate.envelope())
                    val data = message.body.getAsJsonObject("data")
                    assertEquals("create tweet", message.body.get("request").asString)
                    assertEquals(text, data.get("text").asString)
                    val images = data.getAsJsonArray("images")
                    assertEquals(colors.size, images.size())
                    images.forEachIndexed { index, image -> assertPreparedColor(image.asString, colors[index]) }
                    assertTrue(message.body.toString().toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
                    when (kind) {
                        "post" -> {
                            assertFalse(data.has("parent_id")); assertFalse(data.has("retweet_tx"))
                            assertEquals(listOf(message.author), message.recipients)
                        }
                        "reply" -> {
                            assertEquals(original.get("sig").asString, data.get("parent_id").asString)
                            assertEquals(original.get("sig").asString, data.get("thread_id").asString)
                            assertEquals(listOf(message.author, original.get("field2").asString).distinct(), message.recipients)
                        }
                        "quote" -> {
                            assertFalse(data.has("parent_id"))
                            assertEquals(original.get("sig").asString, data.get("signature").asString)
                            assertEquals(original.get("tx").asString, data.get("retweet_tx").asString)
                            val originalImages = SaitoEnvelope.decode(original.get("tx").asString).body.getAsJsonObject("data").getAsJsonArray("images")
                            assertEquals(originalImages, data.getAsJsonObject("embedded").getAsJsonArray("images"))
                            assertEquals(listOf(message.author, original.get("field2").asString).distinct(), message.recipients)
                        }
                    }
                    File(context.cacheDir, "images-evidence/$kind-frame.base64").apply { parentFile?.mkdirs() }
                        .writeText(java.util.Base64.getEncoder().encodeToString(frame))
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
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                        try {
                            ui.waitUntil(20_000) { peer.requests.get() > 0 &&
                                ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            waitFor("Signed image reference")
                            when (kind) {
                                "post" -> ui.onNode(hasText("New post") and hasClickAction()).performClick()
                                "reply" -> {
                                    ui.clickVisiblePostContent("Signed image reference")
                                    waitFor("Conversation")
                                    ui.onNodeWithText("Reply").performClick()
                                }
                                "quote" -> ui.clickVisiblePostContent("Quote")
                            }
                            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            if (text.isEmpty()) ui.onNode(hasSetTextAction()).assertTextEquals("")
                            else ui.onNode(hasSetTextAction()).performTextReplacement(text)
                            ui.onNodeWithText("Add images").performScrollTo().assertIsDisplayed().performClick()
                            Intents.intended(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                                hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                            // Quoted original has its own gallery; wait for the preparation control to settle.
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Remove image 1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            assertTrue(peer.transactions.isEmpty())
                            ui.onNode(hasText("Send") and isEnabled()).performClick()
                            ui.waitUntil(20_000) {
                                peer.assertHealthy()
                                ui.onAllNodesWithText("Available on this server.").fetchSemanticsNodes().isNotEmpty()
                            }
                            assertEquals(1, peer.transactions.size)
                            peer.assertHealthy()
                            capture("$kind-published.png")
                            ui.onNodeWithText("Back").performClick()
                            if (kind == "reply") {
                                waitFor("Conversation")
                                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                                ui.onNodeWithText(text).assertIsDisplayed()
                            } else {
                                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                val previousRequests = peer.requests.get()
                                ui.onNodeWithText("Refresh").performClick()
                                ui.waitUntil(20_000) { peer.requests.get() > previousRequests &&
                                    ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                                if (text.isNotEmpty()) {
                                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                                    ui.onNodeWithText(text).assertIsDisplayed()
                                }
                            }
                            assertEquals(1, peer.transactions.size)
                            peer.assertHealthy()
                        } catch (failure: Throwable) {
                            capture("$kind-publish-failure.png")
                            throw failure
                        }
                    }
                }
            } finally { Intents.release() }
        }
    }

    private fun assertPreparedColor(uri: String, color: String) {
        assertTrue(uri.startsWith("data:image/jpeg;base64,"))
        assertTrue(uri.length <= 512 * 1024)
        val bytes = android.util.Base64.decode(uri.substringAfter(','), android.util.Base64.NO_WRAP)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(12, bitmap.width); assertEquals(8, bitmap.height)
            val expected = when (color) {
                "red" -> intArrayOf(220, 30, 40)
                "blue" -> intArrayOf(20, 60, 220)
                else -> intArrayOf(30, 190, 70)
            }
            val pixel = bitmap.getPixel(6, 4)
            listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).forEachIndexed { index, channel ->
                assertTrue("Prepared $color channel $index", kotlin.math.abs(channel - expected[index]) <= 5)
            }
        } finally { bitmap.recycle() }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "images-evidence/$name").apply { parentFile?.mkdirs() }
        check(androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file))
    }
}

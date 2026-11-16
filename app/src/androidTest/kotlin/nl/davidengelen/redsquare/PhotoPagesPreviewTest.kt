package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Real signed photo posts and real binary callbacks, including the deployed Archive's minimum ten rows. */
class PhotoPagesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun allPhotoPostsAtOneTimestampStayReachableAfterAnOversizedFeedResponse() = exercise(search = false)
    @Test fun searchCanContinueThroughAllPhotoPostsAtOneTimestamp() = exercise(search = true)

    private fun exercise(search: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val backends = AndroidBackendStore(context)
        val oldBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.${SaitoBackend.OWNER.id}.",
            "search.${SaitoBackend.OWNER.id}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val mode = if (search) "search" else "feed"
        val timestamp = 1700000000123L
        val random = java.util.Random(125)
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(800 * 800) { random.nextInt() or (0xff shl 24) }
        bitmap.setPixels(pixels, 0, 800, 0, 0, 800, 800)
        val jpeg = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray()
        }
        bitmap.recycle()
        val image = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg)
        check(jpeg.size < 2 * 1024 * 1024)
        val rows = (1..20).map { number ->
            val body = JsonObject().apply {
                addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                add("data", JsonObject().apply {
                    addProperty("text", "Photo page item $number")
                    add("images", JsonArray().apply { add(image) })
                })
            }
            val envelope = SaitoTransaction.create(wallet, body, timestamp).envelope()
            val post = RedSquareFeed.decode(envelope)
            check(post.images.size == 1 && post.text == "Photo page item $number")
            JsonObject().apply {
                addProperty("id", number); addProperty("sig", post.id); addProperty("created_at", timestamp)
                addProperty("field1", "RedSquare"); addProperty("field2", wallet.publicKey)
                addProperty("field4", ""); addProperty("field5", post.threadId); addProperty("tx", envelope)
            }
        }
        val combinedBytes = JsonArray().apply { rows.forEach(::add) }.toString().toByteArray().size
        check(combinedBytes > SaitoPeer.MAX_RESPONSE_BYTES)
        check(JsonArray().apply { rows.take(10).forEach(::add) }.toString().toByteArray().size < SaitoPeer.MAX_RESPONSE_BYTES)
        val queries = CopyOnWriteArrayList<JsonObject>()
        val oversized = AtomicInteger()
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { query ->
            queries.add(query.deepCopy())
            val selected = if (query.get("field1")?.asString != "RedSquare" || (search && query.has("field4"))) emptyList() else rows.filter { row ->
                (!query.has("id") || query.get("id").asLong == row.get("id").asLong) &&
                    (!query.has("sig") || query.get("sig").asString == row.get("sig").asString) &&
                    (!query.has("created_at") || query.get("created_at").asLong == timestamp) &&
                    (!query.has("created_earlier_than") || timestamp < query.get("created_earlier_than").asLong) &&
                    (!query.has("created_later_than") || timestamp > query.get("created_later_than").asLong)
            }.let { matching ->
                if (query.has("ascending")) matching.sortedBy { it.get("id").asLong }
                else matching.sortedByDescending { it.get("id").asLong }
            }.take((query.get("limit")?.asInt ?: 10).coerceIn(10, 100))
            JsonArray().apply { selected.forEach(::add) }.toString().also {
                if (it.toByteArray().size + 5 > SaitoPeer.MAX_RESPONSE_BYTES) oversized.incrementAndGet()
            }
        }).apply { sendOversizedArchiveFrames = true }
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "photo-pages-evidence/$mode-failure.png")) }) {
                        if (search) {
                            ui.waitUntil(20_000) { ui.onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText("Search").performClick()
                            ui.onNode(hasSetTextAction()).performTextInput("Photo page")
                            ui.onNodeWithContentDescription("Search history").assertIsEnabled()
                            ui.onNodeWithContentDescription("Search history").performClick()
                        }
                        ui.waitUntil(30_000) {
                            peer.assertHealthy()
                            ui.onAllNodesWithText("Photo page item 20", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                        }
                        val list = ui.onNode(hasScrollToNodeAction())
                        val reached = mutableSetOf<Int>()
                        for (window in 0 until if (search) 5 else 1) {
                            val available = if (!search) (20 downTo 1).toList() else {
                                // A byte-bounded result window may replace earlier cards. Observe
                                // each native window before explicitly continuing to the next one.
                                val lowRead = queries.any { query -> query.has("ascending") }
                                if (lowRead) (1..10).toList() else (20 downTo 11).toList()
                            }
                            for (number in available) {
                                list.performScrollToIndex(0)
                                list.performScrollToNode(hasText("Photo page item $number"))
                                ui.onNodeWithText("Photo page item $number", useUnmergedTree = true).assertIsDisplayed()
                                reached.add(number)
                            }
                            if (search && reached.size < 20) {
                                list.performScrollToNode(hasText("Load more"))
                                ui.onNodeWithText("Load more").assertIsEnabled().performClick()
                                ui.waitUntil(30_000) { ui.onAllNodes(hasText("Load more") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            }
                            if (reached.size == 20) break
                        }
                        check(reached.size == 20)
                        check(oversized.get() == 1) { "The same oversized page was requested again" }
                        check(peer.transactions.isEmpty())
                        check(backends.load() == SaitoBackend.OWNER)
                        check(wallets.loadExisting().exportJson() == wallet.exportJson())
                        captureNativeScreen(File(context.cacheDir, "photo-pages-evidence/$mode-all-reached.png"))
                        File(context.cacheDir, "photo-pages-evidence/$mode-reads.json").writeText(JsonObject().apply {
                            addProperty("combinedBytes", combinedBytes); addProperty("oversizedReplies", oversized.get())
                            addProperty("reachedPosts", 20); addProperty("publications", peer.transactions.size)
                            add("queries", JsonArray().apply { queries.forEach(::add) })
                        }.toString())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() },
            { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(oldBackend) },
            { retained.restorePreferences() }) }
    }
}

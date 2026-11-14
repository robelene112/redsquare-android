package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreNestedRoutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aSecondStoreLinkReturnsToTheImmediateSellerChatBeforeItsOriginalFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun reference(name: String) = instrumentation.context.assets.open(name).reader().use { JsonParser.parseReader(it).asJsonObject }
        val first = reference("native-store-listing-reference.json")
        val second = reference("native-store-image-listing-reference.json")
        val seller = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val firstUrl = "https://saito.io/store/${seller.publicKey}/${first.get("nftId").asString}"
        val secondUrl = "https://saito.io/store/listing/${second.getAsJsonObject("listing").get("id").asString}"
        withStoreChatWallet(seller.publicKey) { wallet ->
            val incoming = PrivateChat.create(seller, wallet.publicKey, "Another product\n$secondUrl", 1_700_003_001_000)
            ChatHistoryStore(context, wallet, SaitoBackend.OWNER, seller.publicKey).save(listOf(incoming.envelope()))
            val work = ChatWorkStore(context, wallet, SaitoBackend.OWNER, seller.publicKey)
            work.saveDraft("Keep the immediate seller conversation draft")
            val post = SaitoTransaction.create(wallet, JsonObject().apply {
                addProperty("module", "RedSquare"); addProperty("request", "create tweet")
                add("data", JsonObject().apply { addProperty("text", "First Store destination\n$firstUrl") })
            }, 1_700_003_002_000)
            val rows = "[${postingArchiveRow(post)}]"
            lateinit var peer: ControlledFeedPeer
            withAppFrameFixture("store-nested", "store-nested-evidence", feedRows = rows, mapOfficialPeer = true,
                advertisedServices = "archive||;Store||;relay||", archiveResponse = { query ->
                    val matched = listOf(first, second).singleOrNull { it.getAsJsonObject("listing").get("id").asString == query.get("sig")?.asString }
                    if (matched != null) JsonArray().apply { add(JsonObject().apply { addProperty("tx", matched.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else if (query.get("field1")?.asString == "RedSquare") rows else "[]"
                }, configurePeer = { owned ->
                    peer = owned
                    owned.storeResponse = { request ->
                        assertEquals("load-listing", request.get("request").asString)
                        val exact = request.getAsJsonObject("data").get("signature")?.asString == second.getAsJsonObject("listing").get("id").asString
                        JsonObject().apply { add("listing", (if (exact) second else first).getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    }
                }) {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Open in Store").isDisplayed() }
                ui.onNodeWithText("Open in Store").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Message seller"))
            ui.onNodeWithText("Message seller").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Open in Store").isDisplayed() }
                ui.onNode(hasSetTextAction() and hasText("Message")).assertTextContains("Keep the immediate seller conversation draft")
                ui.onNodeWithText("Open in Store").performClick()
                ui.waitUntil(15_000) {
                    ui.onAllNodesWithText("Illustrated field notes access key").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("3.75 SAITO"))
            ui.onNodeWithText("3.75 SAITO").assertIsDisplayed()
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Open in Store").isDisplayed() }
                ui.onNode(hasSetTextAction() and hasText("Message")).assertTextContains("Keep the immediate seller conversation draft")
                captureNativeScreen(File(context.cacheDir, "store-nested-evidence/immediate-private-origin-restored.png"))
                androidx.test.espresso.Espresso.pressBack()
                ui.waitUntil(15_000) {
                    ui.onAllNodesWithText("Original creator-authenticated field notes.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("First Store destination").isDisplayed() }
                assertEquals("Keep the immediate seller conversation draft", work.load().draft)
                assertNull(work.load().transaction)
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
                captureNativeScreen(File(context.cacheDir, "store-nested-evidence/original-feed-restored.png"))
            }
        }
    }
}

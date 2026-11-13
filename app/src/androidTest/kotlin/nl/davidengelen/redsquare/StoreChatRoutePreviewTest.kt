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

class StoreChatRoutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aStoreLinkReturnsToTheSamePrivateConversationAndKeepsItsUnsentComposer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val seller = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        assertEquals(reference.get("seller").asString, seller.publicKey)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4").withChatContact(seller.publicKey)
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val state = ChatFixtureState(context, listOf("chat.", "chatwork.", "chatreactions.", "personal."), wallet.publicKey)
        state.prepare(); wallets.replace(original, wallet)
        var primary: Throwable? = null
        try {
            val url = "https://saito.io/store/${seller.publicKey}/${reference.get("nftId").asString}"
            val incoming = PrivateChat.create(seller, wallet.publicKey, "A shared Store offer\n$url", 1_700_003_000_000)
            ChatHistoryStore(context, wallet, SaitoBackend.OWNER, seller.publicKey).save(listOf(incoming.envelope()))
            val work = ChatWorkStore(context, wallet, SaitoBackend.OWNER, seller.publicKey)
            work.saveDraft("Keep this unsent conversation draft")
            lateinit var peer: ControlledFeedPeer
            withAppFrameFixture("store-chat-route", "store-chat-route-evidence", feedRows = "[]", mapOfficialPeer = true,
                advertisedServices = "archive||;Store||;relay||", archiveResponse = { query ->
                    if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                        JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else "[]"
                }, configurePeer = { owned ->
                    peer = owned
                    owned.storeResponse = { request ->
                        assertEquals("load-listing", request.get("request").asString)
                        JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    }
                }) {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
                ui.onNodeWithText("Chat").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Anon-${seller.publicKey.take(6)}").isDisplayed() }
                ui.onNodeWithText("Anon-${seller.publicKey.take(6)}").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Open in Store").isDisplayed() }
                ui.onNode(hasSetTextAction() and hasText("Message")).assertTextContains("Keep this unsent conversation draft")
                ui.onNodeWithText("Open in Store").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                ui.onNodeWithContentDescription("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Open in Store").isDisplayed() }
                ui.onNode(hasSetTextAction() and hasText("Message")).assertTextContains("Keep this unsent conversation draft")
                assertEquals("Keep this unsent conversation draft", work.load().draft)
                assertNull(work.load().transaction)
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
                captureNativeScreen(File(context.cacheDir, "store-chat-route-evidence/private-origin-and-draft-restored.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() }, {
                val current = wallets.loadExisting(); check(current.exportJson() == wallet.exportJson())
                wallets.replace(current, original); check(wallets.loadExisting().exportJson() == original.exportJson())
            })
        }
    }
}

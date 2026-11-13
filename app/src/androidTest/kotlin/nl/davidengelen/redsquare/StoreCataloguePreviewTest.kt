package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreCataloguePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun genuineStoreOffersAndOfficialCategoryFiltersLoadWithoutAnyPublication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-catalogue", "store-catalogue-evidence", feedRows = "[]", advertisedServices = "archive||;Store||",
            archiveResponse = { query ->
                if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                    JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                else "[]"
            },
            configurePeer = { owned ->
                peer = owned
                owned.storeResponse = response@ { request ->
                    if (request.get("request").asString == "load-listing") {
                        val data = request.getAsJsonObject("data")
                        assertEquals(reference.get("seller").asString, data.get("seller").asString)
                        assertEquals(reference.get("nftId").asString, data.get("nft_id").asString)
                        return@response JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                    }
                    assertEquals("load-listings", request.get("request").asString)
                    val data = request.getAsJsonObject("data")
                    assertEquals("Store", data.get("module").asString)
                    assertTrue(data.get("public_key").asString in listOf("", reference.get("seller").asString))
                    assertEquals(0, data.get("offset").asInt)
                    assertEquals(24, data.get("page_size").asInt)
                    assertEquals("active", data.get("status").asString)
                    reference.getAsJsonObject("page").deepCopy().apply {
                        addProperty("category", data.get("category").asString)
                        addProperty("public_key", data.get("public_key").asString)
                        if (data.get("category").asString !in listOf("", "Access Keys")) {
                            add("listings", JsonArray()); getAsJsonObject("pagination").apply { addProperty("total", 0); addProperty("total_pages", 0) }
                        }
                    }.toString()
                }
            }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodesWithText("Store").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            ui.onNodeWithText("Store").performScrollTo().assertIsDisplayed().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            ui.onNodeWithText("2.5 SAITO").assertIsDisplayed()
            ui.onNodeWithText("All categories").performClick()
            for (name in listOf("Apps & Games", "Extensions", "Themes", "Image NFTs", "Tokens", "Access Keys", "Merchandise", "Other")) {
                ui.onNodeWithContentDescription("Filter by $name").performScrollTo().assertIsDisplayed()
            }
            ui.onNodeWithContentDescription("Filter by Access Keys").performScrollTo().performClick()
            ui.waitUntil(15_000) { peer.storeRequests.any { it.getAsJsonObject("data").get("category").asString == "Access Keys" } }
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
            assertEquals(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER,
                nl.davidengelen.redsquare.saito.AndroidBackendStore(context).load())
            captureNativeScreen(File(context.cacheDir, "store-catalogue-evidence/real-filtered-store-offer.png"))
            ui.onNodeWithText("Field notes access key").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
            ui.onNodeWithText("Message seller").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("2.5 SAITO").performScrollTo().assertIsDisplayed()
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
            captureNativeScreen(File(context.cacheDir, "store-catalogue-evidence/authenticated-store-product.png"))
            ui.onNodeWithText("By ", substring = true).performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Seller’s store").isDisplayed() }
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            assertTrue(peer.storeRequests.any { it.getAsJsonObject("data").get("public_key")?.asString == reference.get("seller").asString })
            ui.onNodeWithContentDescription("Back").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
            ui.onNodeWithContentDescription("Back").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Access Keys") and hasClickAction() and !hasText("Field notes access key")).isDisplayed() }
            ui.onNode(hasText("Access Keys") and hasClickAction() and !hasText("Field notes access key")).performClick()
            ui.onNodeWithContentDescription("Filter by Other").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.store_empty)).isDisplayed() }
        }
    }
    @Test fun messageSellerCreatesAnUnsentProductReferenceInAnEmptyComposer() = sellerDraft(null)
    @Test fun messageSellerPreservesAnUnrelatedDraftUntilTheUserAddsTheReference() = sellerDraft("Keep my existing private draft")

    private fun sellerDraft(existing: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val seller = reference.get("seller").asString
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val wallet = nl.davidengelen.redsquare.saito.SaitoWallet.fromPrivateKey("0".repeat(63) + "4").withChatContact(seller)
        val state = ChatFixtureState(context, listOf("chat.", "chatwork.", "chatreactions.", "personal."), wallet.publicKey)
        state.prepare()
        wallets.replace(original, wallet)
        var primary: Throwable? = null
        try {
            val work = nl.davidengelen.redsquare.chat.ChatWorkStore(context, wallet, nl.davidengelen.redsquare.saito.SaitoBackend.OWNER, seller)
            if (existing != null) work.saveDraft(existing)
            lateinit var peer: ControlledFeedPeer
            withAppFrameFixture("seller-draft-${existing != null}", "store-catalogue-evidence", feedRows = "[]",
                advertisedServices = "archive||;Store||;relay||", archiveResponse = { query ->
                    if (query.get("sig")?.asString == reference.getAsJsonObject("listing").get("id").asString)
                        JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else "[]"
                }, configurePeer = { owned ->
                    peer = owned
                    owned.storeResponse = { request ->
                        if (request.get("request").asString == "load-listing")
                            JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                        else reference.get("page").toString()
                    }
                }) {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
                ui.onNodeWithText("Store").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                ui.onNodeWithText("Field notes access key").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                ui.onNodeWithText("Message seller").performScrollTo().performClick()
                val url = nl.davidengelen.redsquare.saito.SaitoBackend.OWNER.webUrl + "/store/$seller/" + reference.get("nftId").asString
                val draft = "Field notes access key\n$url"
                ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and hasText("Message")).isDisplayed() }
                val editor = ui.onNode(hasSetTextAction() and hasText("Message"))
                if (existing != null) {
                    editor.assertTextContains(existing)
                    assertEquals(existing, work.load().draft)
                    ui.onNodeWithText("Add product reference").performScrollTo().performClick()
                    editor.assertTextContains(existing + "\n\n" + draft)
                } else {
                    ui.waitUntil(15_000) { work.load().draft == draft }
                    editor.assertTextContains(draft)
                }
                androidx.test.espresso.Espresso.pressBack()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                val saved = work.load()
                assertEquals(if (existing == null) draft else existing + "\n\n" + draft, saved.draft)
                assertNull(saved.transaction)
                assertEquals(nl.davidengelen.redsquare.chat.ChatWorkStore.Status.NONE, saved.status)
                assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
                captureNativeScreen(File(context.cacheDir, "store-catalogue-evidence/unsent-seller-draft-${existing != null}.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() }, {
                val current = wallets.loadExisting()
                check(current.exportJson() == wallet.exportJson())
                wallets.replace(current, original)
                check(wallets.loadExisting().exportJson() == original.exportJson())
            })
        }
    }

}

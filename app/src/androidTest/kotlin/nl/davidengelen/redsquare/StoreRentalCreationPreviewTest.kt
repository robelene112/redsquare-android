package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalCreationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun rentalCreationIsAnExplicitNativeChoiceWithoutPublishingOrMovingTheMaster() {
        withStoreSellerFixture("rental-entry") { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Rental duration (hours)").isDisplayed() }
            ui.onNodeWithText("The Vault master stays yours. Create a separate temporary access key.").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "native-rental-creation-entry.png"))
        }
    }
    @Test fun aCreatorReviewsAndAuthenticatesSeparateRentalInventoryThenPublishesItsExactOffer() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("rental-mint", expected = 2, rental = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().performClick()
            ui.onNodeWithText("Rental duration (hours)").performScrollTo().performTextReplacement("3")
            ui.onNodeWithText("Item title").performScrollTo().performTextReplacement("Native sale")
            ui.onNodeWithText("Description").performScrollTo().performTextReplacement("Three hours of protected field notes.")
            ui.onNodeWithText("Price per item (SAITO)").performScrollTo().performTextReplacement("5")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Upload notes.txt").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Upload notes.txt").performScrollTo().performClick()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            val vector = context.assets.open("native-images-reference.json").reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val image = vector.getAsJsonObject("images").get("red").asString
            val name = "store-image-rental-cover.bin"
            val uri = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            scene.context.contentResolver.call(uri, "prepare", name, android.os.Bundle().apply { putByteArray("bytes", java.util.Base64.getDecoder().decode(image.substringAfter(','))) })
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.onNodeWithText("Choose rental cover").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Review rental inventory") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Review rental inventory").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Create rental inventory").isDisplayed() }
            ui.onNodeWithText("Review rental inventory").assertIsDisplayed()
            ui.onNodeWithText("Inventory deposit: 1 SAITO").performScrollTo().assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            ui.onNodeWithText("Create rental inventory").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check rental inventory") and isEnabled()).isDisplayed() }
            val mint = requireNotNull(scene.seller?.image())
            assertEquals("store-nft-rental", mint.type); assertEquals(3, requireNotNull(mint.rental).hours)
            assertTrue(mint.inputs.none { it.block == java.math.BigInteger("2000002") })
            captureNativeScreen(File(scene.evidence, "rental-inventory-authenticated-master-retained.png"))
            requireNotNull(scene.seller).confirmImage.set(true)
            val observed = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                var balance: WalletBalances? = null
                scene.activity.onActivity { balance = WalletBalances.fromIntent(it.intent) }
                requireNotNull(balance).read(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER, mint.author)
            }
            assertTrue(requireNotNull(observed).nfts.any { it.id == scene.reference.get("id").asString && it.type == "vault-nft-rental" })
            ui.onNodeWithText("Check rental inventory").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Continue listing") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Continue listing").performClick()
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Review sale offer") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Review sale offer").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Authorize and publish").isDisplayed() }
            ui.onNodeWithText("Authorize and publish").performClick()
            waitForSystemPin(device); assertEquals(1, scene.peer.transactions.size); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            val offered = storeSaleCandidate(scene)
            assertEquals(mint.nftId, offered.holding.id); assertEquals(mint.rental, offered.terms.rental)
            assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "native-rental-original-delegation-offered.png"))
        }
    }
    @Test fun aBrowserCreatedRentalRestoresTheNativeCreatorsMasterOnlyAfterProviderExpiry() {
        fun fixture(name: String) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(name).reader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val offered = fixture("native-store-rental-listing-reference.json")
        val paid = fixture("native-store-rental-purchase-reference.json")
        val data = paid.getAsJsonObject("rentalAccess")
        var expired = false
        var restored = false
        var restores = 0
        val archive: (com.google.gson.JsonObject) -> String? = { query ->
            val row = when {
                query.get("sig")?.asString == offered.getAsJsonObject("listing").get("id").asString -> offered.getAsJsonObject("listing").get("envelope")
                query.get("sig")?.asString == paid.getAsJsonObject("purchase").get("id").asString -> paid.getAsJsonObject("purchase").get("envelope")
                query.get("field4")?.asString == offered.get("nftId").asString -> paid.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("envelope")
                else -> null
            }
            row?.let { com.google.gson.JsonArray().apply { add(com.google.gson.JsonObject().apply { add("tx", it) }) }.toString() }
        }
        withVaultReadingFixture("creator-rental-history", creatorRental = true, archiveRead = archive,
            services = "archive||;vault||;Store||;vault-rental-restore||", captureFolder = "store-seller-evidence") { scene ->
            scene.peer.storeResponse = { query ->
                check(query.get("request").asString == "load-seller-inventory")
                com.google.gson.JsonObject().apply {
                    addProperty("seller", scene.reference.get("owner").asString)
                    add("active", com.google.gson.JsonArray().apply { add(offered.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply { addProperty("status", 1) }) })
                    add("sold", com.google.gson.JsonArray())
                }.toString()
            }
            scene.peer.vaultResponse = { request ->
                check(request.author == scene.reference.get("owner").asString)
                when (request.body.get("request").asString) {
                    "vault rental capabilities" -> "{\"rental_restore_protocol\":1}"
                    "vault restore rental" -> {
                        restores++
                        val inputs = request.body.getAsJsonObject("data")
                        assertEquals(data.getAsJsonObject("file").get("id").asString, inputs.get("file_id").asString)
                        assertEquals(paid.get("buyer").asString, inputs.get("renter").asString)
                        assertEquals(1700012800000L, inputs.get("expires_at").asLong)
                        if (!expired) "{\"status\":\"err\",\"err\":\"rental_restore_denied\"}"
                        else com.google.gson.JsonObject().apply {
                            restored = true; addProperty("status", "ok"); add("file_id", inputs.get("file_id"))
                            addProperty("owner", nl.davidengelen.redsquare.saito.SaitoVaultRules.rental(request.author).hash); addProperty("already_restored", false)
                        }.toString()
                    }
                    "vault access file" -> {
                        check(restored)
                        assertEquals(nl.davidengelen.redsquare.saito.SaitoVaultRules.rental(request.author).hash, request.body.getAsJsonObject("data").get("access_hash").asString)
                        com.google.gson.JsonObject().apply { addProperty("status", "success")
                            add("txs", com.google.gson.JsonArray().apply { add(data.getAsJsonObject("file").get("envelope")) }) }.toString()
                    }
                    else -> error("Unexpected creator rental request")
                }
            }
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Rental details").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Rental details").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Restore master access").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Restore master access").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Rental history or restoration could not be verified on this node. Try again.").isDisplayed() }
            assertFalse(restored); assertEquals(1, restores)
            expired = true
            ui.onNodeWithText("Restore master access").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Master access restored on this node.").isDisplayed() }
            assertTrue(restored); assertEquals(2, restores)
            captureNativeScreen(File(scene.evidence, "browser-rental-native-creator-restored.png"))
            ui.onNodeWithText("Done").performScrollTo().performClick()
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty())
        }
    }
    @Test fun theRealRentalDurationKeyboardEditsWithoutPublishingOrAuthorizingAnyOperation() {
        withStoreSellerFixture("rental-keyboard") { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().performClick()
            ui.onNodeWithText("Rental duration (hours)").performScrollTo().performClick()
            fun imeVisible(): Boolean {
                var visible = false
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val root = android.view.inspector.WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
                    visible = root?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                return visible
            }
            ui.waitUntil(5_000) { imeVisible() }
            ui.onNodeWithText("Rental duration (hours)").performTextReplacement("3")
            captureNativeScreen(File(scene.evidence, "rental-duration-real-ime.png"), "Rental duration (hours)")
            ui.onNodeWithText("Rental duration (hours)").performImeAction()
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            if (imeVisible()) device.pressBack()
            ui.waitUntil(5_000) { !imeVisible() }
            ui.onNodeWithText("Rental duration (hours)").assertTextContains("3")
            File(scene.evidence, "rental-keyboard-observation.json").writeText("{\"actualFocusedWindowIme\":true,\"imeActionDidNotPublish\":true,\"duration\":3}")
        }
    }

}

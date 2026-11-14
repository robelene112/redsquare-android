package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class StoreSellerPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun existingInventoryOfferIsReviewedRestoredAndDiscardedWithoutPublishing() = scene("review") { scene, _, _ ->
        openSelling()
        reviewOffer()
        ui.onNodeWithText("1 items · 5 SAITO each").assertIsDisplayed()
        ui.onNodeWithText("Authorize and publish").performScrollTo().assertIsEnabled()
        assertTrue(scene.peer.transactions.isEmpty())
        captureNativeScreen(File(scene.evidence, "existing-sale-before-authorization.png"))
        scene.activity.recreate()
        ui.waitUntil(15_000) { ui.onNodeWithText("New listing").isDisplayed() }
        ui.onNodeWithText("Native sale").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onNodeWithText("1 items · 5 SAITO each").isDisplayed() }
        ui.onNodeWithText("Discard unsent offer").performScrollTo().performClick()
        ui.waitUntil(5_000) { ui.onNodeWithText("New listing").isDisplayed() }
        assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
    }
    @Test fun lostSubmissionKeepsTheOriginalAndStoreApprovalRemainsASeparateObservation() = withVaultDeviceCredential { device ->
        scene("recovery", expected = 1) { scene, report, approved ->
            scene.peer.disconnectAfterTransaction = true
            openSelling(); reviewOffer()
            ui.onNodeWithText("Authorize and publish").performScrollTo().performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            val original = candidate(scene)
            assertEquals("Native sale", original.terms.title); assertEquals("5", original.terms.price)
            assertEquals(BigInteger.ONE, original.terms.quantity)
            captureNativeScreen(File(scene.evidence, "original-submitted-availability-unverified.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("New listing").isDisplayed() }
            assertEquals(original.transaction.encoded(), candidate(scene).transaction.encoded())
            scene.peer.disconnectAfterTransaction = false; report.set(true)
            ui.onNodeWithText("Refresh Store").performClick()
            val pending = "Available in this storefront. Not approved for the marketplace."
            ui.waitUntil(15_000) { ui.onNodeWithText(pending).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "reported-storefront-marketplace-not-approved.png"))
            approved.set(true)
            ui.onNodeWithText("Refresh Store").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available in this storefront. Approved for the marketplace.").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "reported-marketplace-approval.png"))
        }
    }
    private fun openSelling() {
        ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
        ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
        ui.onNodeWithText("Store").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("My store").isDisplayed() }
        ui.onNodeWithText("My store").performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
    }
    private fun reviewOffer() {
        ui.onNodeWithText("New listing").performClick()
        ui.onNodeWithText("Item title").performTextReplacement("Native sale")
        ui.onNodeWithText("Description").performTextReplacement("A genuine owned key, offered from Android.")
        ui.onNodeWithText("Price per item (SAITO)").performTextReplacement("5")
        ui.onNodeWithText("Field notes.txt").performScrollTo().performClick()
        ui.onNodeWithText("Review sale offer").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Authorize and publish").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private fun candidate(scene: VaultReadingScene): SaitoStoreListing {
        val wire = scene.peer.transactions.single().drop(1).toByteArray(); val header = ByteBuffer.wrap(wire)
        val bytes = wire.copyOf(93 + 59 * (header.getInt(0) + header.getInt(4)) + header.getInt(8))
        ByteBuffer.wrap(bytes).putInt(12, 0)
        return SaitoStoreListing.restore(Base64.getEncoder().encodeToString(bytes))
    }
    private fun scene(name: String, expected: Int = 0, block: (VaultReadingScene, AtomicBoolean, AtomicBoolean) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val saved = ChatFixtureState(context, listOf("store-listings.$owner.", "store-sale-draft.$owner."), owner)
        var primary: Throwable? = null
        try {
            saved.prepare()
            withVaultReadingFixture("store-sale-$name", services = "archive||;vault||;Store||", captureFolder = "store-seller-evidence", retainTransfers = true, expectedMintCount = expected) { scene ->
                val report = AtomicBoolean(false); val approved = AtomicBoolean(false)
                val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
                scene.peer.storeResponse = { query ->
                    val data = query.getAsJsonObject("data")
                    when (query.get("request").asString) {
                        "load-seller-inventory" -> JsonObject().apply {
                            addProperty("seller", owner)
                            add("active", JsonArray().apply { if (report.get()) {
                                val original = candidate(scene)
                                add(reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply {
                                    addProperty("seller", owner); addProperty("listing_signature", original.transaction.id); addProperty("title", "Native sale")
                                    addProperty("price", 500000000); addProperty("quantity_total", 1); addProperty("quantity_available", 1)
                                    addProperty("approved", if (approved.get()) 1 else 0)
                                })
                            } }); add("sold", JsonArray())
                        }.toString()
                        "load-listings" -> reference.getAsJsonObject("page").deepCopy().apply {
                            addProperty("public_key", data.get("public_key").asString); addProperty("category", data.get("category").asString)
                        }.toString()
                        else -> error("Unexpected owned Store request")
                    }
                }
                block(scene, report, approved)
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { saved.restoreFiles() }, { saved.restorePreferences() }) }
    }
}

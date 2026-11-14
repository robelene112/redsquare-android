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

    @Test fun existingInventoryOfferIsReviewedRestoredAndDiscardedWithoutPublishing() = withStoreSellerFixture("review") { scene, _, _ ->
        ui.openNativeStoreSelling()
        ui.reviewNativeStoreSale()
        ui.onNodeWithText("Quantity: 1 · 5 SAITO each").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Authorize and publish").assertIsEnabled()
        assertTrue(scene.peer.transactions.isEmpty())
        captureNativeScreen(File(scene.evidence, "existing-sale-before-authorization.png"))
        scene.activity.recreate()
        ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native sale"))
        ui.onNodeWithText("Native sale").performClick()
        ui.waitUntil(5_000) { ui.onNodeWithText("Quantity: 1 · 5 SAITO each").isDisplayed() }
        ui.onNodeWithText("Discard unsent offer").performClick()
        ui.waitUntil(5_000) { ui.onNodeWithText("New listing").isDisplayed() }
        assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
    }
    @Test fun lostSubmissionKeepsTheOriginalAndStoreApprovalRemainsASeparateObservation() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("recovery", expected = 1) { scene, report, approved ->
            scene.peer.disconnectAfterTransaction = true
            ui.openNativeStoreSelling(); ui.reviewNativeStoreSale()
            ui.onNodeWithText("Authorize and publish").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            val original = storeSaleCandidate(scene)
            assertEquals("Native sale", original.terms.title); assertEquals("5", original.terms.price)
            assertEquals(BigInteger.ONE, original.terms.quantity)
            captureNativeScreen(File(scene.evidence, "original-submitted-availability-unverified.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            assertEquals(original.transaction.encoded(), storeSaleCandidate(scene).transaction.encoded())
            scene.peer.disconnectAfterTransaction = false; report.set(true)
            ui.onNodeWithText("Refresh Store").performClick()
            val pending = "Available in this storefront. Not approved for the marketplace."
            ui.waitUntil(15_000) { ui.onNodeWithText(pending).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "reported-storefront-marketplace-not-approved.png"))
            ui.onNodeWithText("Request marketplace approval").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            assertTrue(scene.peer.storeRequests.any { it.get("request").asString == "submit-listing" })
            ui.onNodeWithText(pending).assertIsDisplayed()
            approved.set(true)
            ui.onNodeWithText("Refresh Store").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available in this storefront. Approved for the marketplace.").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "reported-marketplace-approval.png"))
        }
    }
}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreSellerRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aHeldStoreRefreshDoesNotLockTheRetainedOfferOrItsNavigation() = withStoreSellerFixture("held-reading") { scene, _, _ ->
        ui.openNativeStoreSelling(); ui.reviewNativeStoreSale()
        ui.onNodeWithText("Done").performClick()
        val before = scene.peer.storeRequests.size
        scene.peer.withholdStoreReply = true
        ui.onNodeWithText("Refresh Store").performClick()
        ui.waitUntil(15_000) { scene.peer.storeRequests.size > before }
        val start = android.os.SystemClock.elapsedRealtime()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native sale"))
        ui.onNodeWithText("Native sale").performClick()
        ui.waitUntil(2_000) { ui.onNodeWithText("Authorize and publish").isDisplayed() }
        assertTrue("An unrelated read cannot hold offer navigation", android.os.SystemClock.elapsedRealtime() - start < 2_000)
        ui.onNodeWithText("Authorize and publish").assertIsEnabled()
        ui.onNodeWithText("Done").assertIsEnabled()
        assertTrue(scene.peer.transactions.isEmpty())
        captureNativeScreen(File(scene.evidence, "saved-offer-usable-during-held-store-reading.png"))
        scene.peer.withholdStoreReply = false
        ui.onNodeWithText("Done").performClick()
        ui.onNodeWithText("Refresh Store").performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("Refresh Store") and isEnabled()).isDisplayed() }
        assertTrue(scene.peer.transactions.isEmpty())
    }
    @Test fun aRejectedMarketplaceRequestKeepsTheOriginalStorefrontAndCanBeRetried() = withStoreSellerFixture("marketplace-denied", prelisted = true) { scene, _, _ ->
        val response = requireNotNull(scene.peer.storeResponse)
        var denied = true
        scene.peer.storeResponse = { request ->
            if (request.get("request").asString == "submit-listing" && denied) "{\"err\":\"Unauthorized\"}" else response(request)
        }
        ui.openNativeStoreSelling()
        ui.waitUntil(15_000) { ui.onNodeWithText("Available in this storefront. Not approved for the marketplace.").isDisplayed() }
        ui.onNodeWithText("Request marketplace approval").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("This Store did not accept the marketplace submission. Your storefront listing is unchanged.").isDisplayed() }
        assertTrue(scene.peer.transactions.isEmpty())
        captureNativeScreen(File(scene.evidence, "marketplace-denied-original-storefront-retained.png"))
        denied = false
        ui.onNodeWithText("Request marketplace approval").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("Refresh Store") and isEnabled()).isDisplayed() }
        assertEquals(2, scene.peer.storeRequests.count { it.get("request").asString == "submit-listing" })
        ui.onNodeWithText("Available in this storefront. Marketplace review is pending.").performScrollTo().assertIsDisplayed()
        assertTrue(scene.peer.transactions.isEmpty())
    }
}

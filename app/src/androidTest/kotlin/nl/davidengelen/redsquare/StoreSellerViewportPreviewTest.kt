package nl.davidengelen.redsquare

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private object StoreSellerViewportMotion : MotionDurationScale { override val scaleFactor = 0f }

class StoreSellerViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(StoreSellerViewportMotion)
    @Test fun sellingReviewKeepsRealTermsAndActionsReachableInTwelveNativeCells() = withNativeModuleMotionScale(0) {
        for (width in listOf(320, 412)) withStoreSellerFixture("viewport-$width") { scene, _, _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(scene.activity, scene.evidence, "seller-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onAllNodesWithText("My store").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                if (ui.onAllNodesWithText("New listing").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) ui.onNodeWithText("My store").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
                ui.reviewNativeStoreSale()
                ui.onNodeWithText("Quantity: 1 · 5 SAITO each").performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-reviewed-quantity-price.png"))
                ui.onNodeWithText("Listing network fee: 0 SAITO. The existing item deposit is retained, not charged again.").performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-existing-deposit-no-new-fee.png"))
                ui.onNodeWithText("Authorize and publish").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithText("Done").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithText("Discard unsent offer").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(scene.evidence, "$label-stable-financial-actions.png"))
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
                ui.onNodeWithText("Discard unsent offer").performClick()
                ui.waitUntil(5_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
                ui.switchStackModule("Store", "Red Square", scrollTarget = true)
            }
        }
    }
}

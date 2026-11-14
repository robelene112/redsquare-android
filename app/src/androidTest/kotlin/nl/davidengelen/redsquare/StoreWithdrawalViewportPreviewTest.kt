package nl.davidengelen.redsquare

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private object StoreWithdrawalViewportMotion : MotionDurationScale { override val scaleFactor = 0f }

class StoreWithdrawalViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(StoreWithdrawalViewportMotion)
    @Test fun withdrawalTermsAndAuthorizationStayReachableInTwelveActualNativeCells() = withNativeModuleMotionScale(0) {
        for (width in listOf(320, 412)) withStoreSellerFixture("withdrawal-viewport-$width", crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
            val treasury = OwnedStoreWithdrawalFixture().also { it.attach(scene) }
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(scene.activity, scene.evidence, "withdrawal-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onAllNodesWithText("My store").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                if (ui.onAllNodesWithText("New listing").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) ui.onNodeWithText("My store").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
                ui.reviewNativeStoreWithdrawal()
                ui.onNodeWithText("Requested payout: 6.25 USDT").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Network: Ethereum").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("0x1111111111111111111111111111111111111111").performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-withdrawal-amount-network-destination.png"))
                ui.onNodeWithText("Service fee: 0.25 SAITO").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Signed Saito network fee: 0 SAITO").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("External network charges are not quoted by this service. The amount ultimately received is not guaranteed.").performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-withdrawal-fees-and-net-limits.png"))
                ui.onNodeWithText("Authorize withdrawal").assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithText("Done").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithText("Discard unsent request").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(scene.evidence, "$label-withdrawal-stable-financial-actions.png"))
                assertTrue(treasury.submissions.isEmpty()); assertTrue(scene.peer.transactions.isEmpty())
                ui.onNodeWithText("Discard unsent request").performClick()
                ui.waitUntil(5_000) { ui.onAllNodesWithText("Authorize withdrawal").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                ui.onNodeWithText("Done").performScrollTo().performClick()
                ui.waitUntil(5_000) { ui.onNodeWithText("New listing").isDisplayed() }
                ui.switchStackModule("Store", "Red Square", scrollTarget = true)
            }
        }
    }
}

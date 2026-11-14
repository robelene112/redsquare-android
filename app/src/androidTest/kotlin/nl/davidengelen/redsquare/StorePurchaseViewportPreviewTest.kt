package nl.davidengelen.redsquare

import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

private object StorePurchaseViewportMotionScale : MotionDurationScale { override val scaleFactor = 0f }

class StorePurchaseViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(StorePurchaseViewportMotionScale)
    @Test fun reviewTermsExactCostsAndFinancialActionsRemainReachableInTwelveNativeCells() = withNativeModuleMotionScale(0) {
        for (width in listOf(320, 412)) withStorePurchaseFixture("viewport-$width") { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(scene.activity, scene.evidence, "purchase-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                ui.onNodeWithText("Field notes access key").performClick()
                ui.waitUntil(15_000) { ui.onAllNodesWithText("2.5 SAITO").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Review purchase"))
                ui.onNodeWithText("Review purchase").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                val total = hasText("Total: 2.5 SAITO") and hasAnyAncestor(isDialog())
                ui.waitUntil(15_000) { ui.onAllNodes(total).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNode(total).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-exact-total.png"))
                ui.onNode(hasText("Network fee: 0 SAITO") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Confirm and pay").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                ui.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(scene.evidence, "$label-financial-actions.png"))
                ui.onNodeWithText(scene.node.reference.get("store").asString).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-store-and-reference.png"))
                assertTrue(scene.node.peer.transactions.isEmpty())
                ui.onNodeWithText("Done").performScrollTo().performClick()
                ui.waitUntil(5_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                ui.onNodeWithText("Back").performClick()
                ui.switchStackModule("Store", "Red Square", scrollTarget = true)
            }
        }
    }
}

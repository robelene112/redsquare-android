package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Rental purchase and imported Vault rights at actual width/theme/font settings. */
class StoreRentalViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(VaultViewportMotionScale)
    @Test fun rentalTermsCostsAndVaultRightsRemainReachableInTwelveNativeCells() = withNativeModuleMotionScale(0) {
        for (width in listOf(320, 412)) withStorePurchaseFixture("rental-viewport-$width", rental = true, browserRental = true, browserRentalChange = true) { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(scene.activity, scene.evidence, "rental-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                ui.switchStackModule("Red Square", "Store", scrollTarget = true)
                observeModuleWindowMotionScale(scene.activity, 0, File(scene.evidence, "$label-motion").apply { mkdirs() })
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
                ui.onNodeWithText("Field notes access key").performClick()
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh Store") and isEnabled()).isDisplayed() }
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Temporary file access"))
                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Review purchase"))
                ui.onNodeWithText("Review purchase").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
                val dialog = hasAnyAncestor(isDialog())
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Confirm and pay") and dialog).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNode(hasText("Temporary file access") and dialog).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(scene.evidence, "$label-rental-terms.png"))
                ui.onNode(hasText("Total: 2.5 SAITO") and dialog).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Confirm and pay").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(scene.evidence, "$label-rental-cost-actions.png"))
                ui.onNodeWithText("Done").performScrollTo().performClick()
                ui.waitUntil(5_000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                ui.onNodeWithText("Back").performClick()
                ui.switchStackModule("Store", "Vault", scrollTarget = true)
                ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
                ui.onNodeWithText("Keep offline").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp)
                captureNativeScreen(File(scene.evidence, "$label-vault-rights-actions.png"))
                ui.onNodeWithText("Save as…").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                assertTrue(scene.node.peer.transactions.isEmpty())
                assertEquals(0, requireNotNull(scene.rental).checkouts)
                ui.switchStackModule("Vault", "Red Square", scrollTarget = true)
            }
        }
    }
}

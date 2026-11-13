package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultAccessViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(VaultViewportMotionScale)
    @Test fun sourceInputsAndFileActionsRemainReachableInTwelveNativeViewportCells() =
        ui.withVaultDialogViewports("access", custom = true) { scene, label ->
            waitUntil(15_000) { onNodeWithText("Custom field notes.txt").isDisplayed() }
            onNodeWithText("Access file").performScrollTo().performClick()
            waitUntil(5_000) { onNodeWithText("File access").isDisplayed() }
            onNodeWithText("Access script").performScrollTo().assertIsDisplayed()
            captureNativeScreen(File(scene.evidence, "$label-script.png"))
            onNodeWithText("Unlock inputs").performScrollTo().assertIsDisplayed()
            onNodeWithText("Keep offline").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            onNodeWithText("Save as…").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            onNodeWithText("Close file access").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            captureNativeScreen(File(scene.evidence, "$label-inputs-actions.png"))
            onNodeWithText("Close file access").performClick()
            waitUntil(5_000) { onAllNodesWithText("File access").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        }
}

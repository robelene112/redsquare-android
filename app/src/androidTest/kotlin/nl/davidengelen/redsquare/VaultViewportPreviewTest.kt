package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun fileNamesAndControlsRemainUsableInBothThemesAndLargeTextAtNarrowAndWideWidths() =
        withVaultReadingFixture("viewport", captureFolder = "vault-viewport-evidence") { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            for (width in listOf(320, 412)) {
                ui.withPostViewports(scene.activity, scene.evidence, "vault-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                    ui.onNodeWithTag("vault-files").performScrollToNode(hasText("Field notes.txt"))
                    ui.onNodeWithText("Field notes.txt").assertIsDisplayed()
                    ui.onNodeWithText("Keep offline").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    ui.onNodeWithText("Save as…").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    captureNativeScreen(File(scene.evidence, "$label-file.png"))
                }
            }
        }
}

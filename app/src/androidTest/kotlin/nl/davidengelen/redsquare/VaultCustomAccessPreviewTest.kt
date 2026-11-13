package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

class VaultCustomAccessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun wrongAccessInputsCanBeCorrectedBeforeKeepingTheExactCustomFileOffline() =
        withVaultReadingFixture("custom-access", custom = true, captureFolder = "vault-custom-access-evidence") { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Custom field notes.txt").isDisplayed() }
            ui.onNodeWithText("Access file").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("File access").isDisplayed() }
            ui.onNodeWithText("Unlock inputs").performScrollTo().performTextReplacement("[{\"input\":\"wrong\"}]")
            ui.onNodeWithText("Keep offline").assertIsDisplayed().performClick()
            val denied = "Could not verify access. Check your inputs and this node, then try again."
            ui.waitUntil(15_000) { ui.onAllNodesWithText(denied).fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1 }
            ui.onNodeWithText(denied).performScrollTo().assertIsDisplayed()
            ui.onAllNodesWithText("Available offline").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "wrong-inputs-retained.png"))
            ui.onNodeWithText("Unlock inputs").performScrollTo().assertTextContains("wrong", substring = true)
                .performTextReplacement("[{\"input\":\"hello\"}]")
            ui.onNodeWithText("Keep offline").assertIsDisplayed().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "custom-file-available-offline.png"))
            ui.onNodeWithText("Save as…").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() }
            val saved = requireNotNull(scene.context.contentResolver.openInputStream(scene.destination)).use { it.readBytes() }
            assertArrayEquals(Base64.getDecoder().decode(scene.reference.get("fileContentsBase64").asString), saved)
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "custom-file-exported.png"))
        }
}

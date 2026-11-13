package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultKeyTransferPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aWholeKeyTransferCanBeReviewedReopenedAndDiscardedWithoutSending() =
        withVaultReadingFixture("key-transfer-review", captureFolder = "vault-key-transfer-evidence", retainTransfers = true) { scene ->
            val vector = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-transfer-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            ui.onNodeWithText("Transfer key").performScrollTo().performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Permanent transfer") and isHeading()).isDisplayed() }
            ui.onNodeWithText("Recipient public key").performScrollTo().performTextReplacement(vector.get("recipient").asString)
            ui.onNodeWithText("Review transfer").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Whole key: 2").isDisplayed() }
            ui.onNodeWithText("Locked deposit: 1 SAITO").assertIsDisplayed()
            ui.onNodeWithText("Network fee: 0 SAITO").assertIsDisplayed()
            ui.onNodeWithText("Transfer permanently").assertIsDisplayed().assertIsEnabled()
            ui.onNodeWithText(vector.get("recipient").asString).performScrollTo().assertIsDisplayed()
            captureNativeScreen(File(scene.evidence, "whole-key-reviewed-before-authorization.png"))
            assertTrue(scene.peer.transactions.isEmpty())
            ui.onNodeWithText("Close permanent transfer").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodes(hasText("Permanent transfer") and isHeading()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            }
            ui.onNodeWithText("Transfer not sent").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Review transfer").performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Permanent transfer") and isHeading()).isDisplayed() }
            ui.onNodeWithText("Whole key: 2").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Discard transfer").performClick()
            ui.waitUntil(5_000) {
                ui.onAllNodes(hasText("Permanent transfer") and isHeading()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            }
            ui.onAllNodesWithText("Transfer not sent").assertCountEquals(0)
            ui.onNodeWithText("Transfer key").performScrollTo().assertIsEnabled()
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "unsent-transfer-discarded.png"))
        }
}

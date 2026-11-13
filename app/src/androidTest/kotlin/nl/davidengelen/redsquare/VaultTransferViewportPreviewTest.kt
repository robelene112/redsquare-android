package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultTransferViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun wholeKeyCostsRecipientAndPermanentActionsRemainReachableInTwelveNativeCells() {
        val row = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-transfer-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        ui.withVaultDialogViewports("transfer") { scene, label ->
            waitUntil(15_000) { onNodeWithText("Field notes.txt").isDisplayed() }
            onNodeWithText("Transfer key").performScrollTo().performClick()
            waitUntil(5_000) { onNode(hasText("Permanent transfer") and isHeading()).isDisplayed() }
            onNodeWithText("Recipient public key").performScrollTo().performTextReplacement(row.get("recipient").asString)
            onNodeWithText("Review transfer").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            waitUntil(15_000) { onAllNodesWithText("Whole key: 2").fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1 }
            onNodeWithText("Whole key: 2").performScrollTo().assertIsDisplayed()
            onNodeWithText("Locked deposit: 1 SAITO").performScrollTo().assertIsDisplayed()
            onNodeWithText("Network fee: 0 SAITO").performScrollTo().assertIsDisplayed()
            captureNativeScreen(File(scene.evidence, "$label-whole-key-costs.png"))
            onNodeWithText(row.get("recipient").asString).performScrollTo().assertIsDisplayed()
            onNodeWithText("Transfer permanently").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            onNodeWithText("Discard transfer").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            captureNativeScreen(File(scene.evidence, "$label-recipient-actions.png"))
            onNodeWithText("Discard transfer").performClick()
            waitUntil(5_000) { onAllNodes(hasText("Permanent transfer") and isHeading()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        }
    }
}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.TransferStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual editable wallet task recreation without preparing or publishing a transfer. */
class WalletTransferRecreationTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val values = listOf(
        "Recipient address" to "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU",
        "Amount in SAITO" to "1.2300",
        "Fee in SAITO" to "0.0120",
        "Public memo" to "Exact editable memo, before review",
    )

    @Test fun allFourEditableValuesAndTheActiveTaskSurviveRecreationWithoutPreparingATransfer() {
        withAppFrameFixture("editable-rotation", evidenceFolder = "wallet-input-evidence", feedRows = "[]") { activity ->
            ui.openWallet()
            val owner = displayedWalletAddress(ui)
            val before = TransferStore(context, owner).pending()
            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
            values.forEach { (label, value) ->
                ui.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
            }
            assertEditableValues()
            captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/editable-before.png"))
            activity.recreate()
            ui.waitUntil(10_000) { ui.onAllNodes(hasContentDescription("Recipient address") and hasSetTextAction()).fetchSemanticsNodes().size == 1 }
            assertEditableValues()
            ui.onAllNodesWithText("Confirm SAITO transfer").assertCountEquals(0)
            ui.onAllNodesWithText("Authenticate and send").assertCountEquals(0)
            assertEquals(before, TransferStore(context, owner).pending())
            captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/editable-restored.png"))
        }
    }

    private fun assertEditableValues() {
        values.forEach { (label, value) ->
            ui.onNodeWithContentDescription(label).performScrollTo().assertTextEquals(label, value)
        }
    }
}

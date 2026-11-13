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
    @Test fun aLostTransferAnswerCanBeRecoveredAfterRecreationWithoutAnotherBroadcast() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("key-transfer-submission", captureFolder = "vault-key-transfer-evidence",
            retainTransfers = true, expectedMintCount = 1, keyTransfer = true) { scene ->
            val node = requireNotNull(scene.transfer)
            node.confirm.set(false)
            scene.peer.disconnectAfterTransaction = true
            val vector = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-transfer-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            ui.onNodeWithText("Transfer key").performScrollTo().performClick()
            ui.waitUntil(5_000) { ui.onNode(hasText("Permanent transfer") and isHeading()).isDisplayed() }
            ui.onNodeWithText("Recipient public key").performScrollTo().performTextReplacement(vector.get("recipient").asString)
            ui.onNodeWithText("Review transfer").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Whole key: 2").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty())
            ui.onNodeWithText("Transfer permanently").performClick()
            waitForSystemPin(device)
            assertTrue("No key is sent before real device authentication", scene.peer.transactions.isEmpty())
            enterRealSystemPin(device)
            ui.waitUntil(25_000) {
                ui.onNode(hasText("Retry original transfer") and isEnabled()).isDisplayed()
            }
            assertEquals(1, scene.peer.transactions.size)
            val original = requireNotNull(node.candidate()).transaction.encoded()
            ui.onAllNodesWithText("Transfer included on this node").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "submitted-key-still-unverified.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("Retry original transfer") and isEnabled()).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(original, requireNotNull(node.candidate()).transaction.encoded())
            node.confirm.set(true)
            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
            fun remaining() = (deadline - android.os.SystemClock.elapsedRealtime()).also { check(it > 0) }
            ui.onNodeWithText("Check transfer").performClick()
            val observed = "Observed in block 2000003. This is a selected-node observation."
            ui.waitUntil(remaining()) {
                ui.onAllNodesWithText(observed).fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1
            }
            ui.onNodeWithText(observed).performScrollTo().assertIsDisplayed()
            remaining()
            assertEquals("A read-only check cannot send another transfer", 1, scene.peer.transactions.size)
            assertEquals(original, requireNotNull(node.candidate()).transaction.encoded())
            captureNativeScreen(File(scene.evidence, "original-key-transfer-included.png"))
            ui.onNodeWithText("Close permanent transfer").performClick()
            ui.waitUntil(15_000) {
                ui.onAllNodesWithText("Transfer key").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
            }
            assertTrue(scene.peer.vaultRequests.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "moved-key-has-no-current-access-control.png"))
        }
    }

}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Actual native peer/document journeys; no production writes or ordinary-owner-device QA. */
class VaultReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aRealKeyDownloadsExactBytesAndItsOfflineCopySurvivesRecreationWithoutAWorkingNode() =
        withVaultReadingFixture("reading") { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "key.png"))
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "offline-saved.png"))
            scene.activity.onActivity {
                it.intent.putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen")
                it.intent.putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
            }
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            ui.onNodeWithText("Save as…").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() }
            val saved = scene.context.contentResolver.openInputStream(scene.destination)!!.use { it.readBytes() }
            assertArrayEquals(Base64.getDecoder().decode(scene.reference.get("fileContentsBase64").asString), saved)
            File(scene.evidence, "exported-file.bin").writeBytes(saved)
            captureNativeScreen(File(scene.evidence, "offline-recreated.png"))
            ui.onNodeWithText("Remove offline copy").performScrollTo().performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Offline copy removed").isDisplayed() }
            ui.onAllNodesWithText("Available offline").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "offline-removed.png"))
        }
    @Test fun laterFilePagesRemainSelectedWhenSwitchingBetweenNativeModules() =
        withVaultReadingFixture("pagination", inventory = true) { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            ui.onNodeWithTag("vault-files").performScrollToNode(hasText("Next files"))
            ui.onNodeWithText("Next files").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes 21.txt").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "page-two.png"))
            ui.switchStackModule("Vault", "Red Square", scrollTarget = true)
            ui.switchStackModule("Red Square", "Vault", scrollTarget = true)
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes 21.txt").isDisplayed() }
            ui.onAllNodesWithText("Field notes.txt").assertCountEquals(0)
            ui.onNodeWithTag("vault-files").performScrollToNode(hasText("Previous files"))
            ui.onNodeWithText("Previous files").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "page-one-restored.png"))
        }

}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class VaultLargeReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aFourMiBFileKeepsItsExactBytesThroughEncryptedPartsRestartAndDocumentExport() =
        withVaultReadingFixture("large", captureFolder = "vault-large-evidence", large = true) { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Large Vault.bin").isDisplayed() }
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            captureNativeScreen(File(scene.evidence, "four-mib-saved.png"))
            scene.activity.onActivity {
                it.intent.putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen")
                it.intent.putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
            }
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            ui.onNodeWithText("Save as…").performScrollTo().performClick()
            ui.waitUntil(20_000) { ui.onNodeWithText("File saved").isDisplayed() }
            val received = scene.context.contentResolver.openInputStream(scene.destination)!!.use { it.readBytes() }
            assertEquals(4 * 1024 * 1024, received.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(received).joinToString("") { "%02x".format(it) }
            assertEquals(scene.reference.getAsJsonObject("large").get("sha256").asString, hash)
            File(scene.evidence, "four-mib-export.json").writeText("{\"bytes\":${received.size},\"sha256\":\"$hash\"}")
            captureNativeScreen(File(scene.evidence, "four-mib-exported.png"))
            ui.onNodeWithText("Remove offline copy").performScrollTo().performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Offline copy removed").isDisplayed() }
            ui.onAllNodesWithText("Available offline").assertCountEquals(0)
        }
}

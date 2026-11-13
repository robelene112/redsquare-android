package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import nl.davidengelen.redsquare.saito.SaitoBackend

class VaultRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun transferringAVisibleKeyPreventsANewRetrievalWithItsCachedWitness() = withVaultReadingFixture("moved-key", captureFolder = "vault-recovery-evidence") { scene ->
        ui.openNativeVault()
        ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
        scene.ownsKey.set(false)
        ui.onNodeWithText("Keep offline").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Could not access this file.", substring = true).isDisplayed() }
        assertTrue(scene.peer.vaultRequests.isEmpty())
        ui.onAllNodesWithText("Available offline").assertCountEquals(0)
        captureNativeScreen(File(scene.evidence, "moved-key.png"))
    }

    @Test fun anUnavailableServiceIsDistinctFromAnEmptyVault() = withVaultReadingFixture("missing-service", "archive||", captureFolder = "vault-recovery-evidence") { scene ->
        ui.openNativeVault()
        ui.waitUntil(15_000) { ui.onNodeWithText("This node does not offer Vault.", substring = true).isDisplayed() }
        ui.onNodeWithText("Node settings").assertIsDisplayed()
        assertTrue(scene.peer.vaultRequests.isEmpty())
        ui.onAllNodesWithText("Files appear here when your wallet holds a compatible Vault access key.").assertCountEquals(0)
        captureNativeScreen(File(scene.evidence, "missing-service.png"))
    }

    @Test fun aSuccessfulServerReplyCannotSubstituteAnotherSignedPayloadForTheRequestedFile() = withVaultReadingFixture("wrong-file", captureFolder = "vault-recovery-evidence") { scene ->
        ui.openNativeVault()
        ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
        scene.peer.vaultResponse = {
            JsonObject().apply {
                addProperty("status", "success")
                add("txs", JsonArray().apply { add(scene.reference.getAsJsonObject("key").get("envelope").asString) })
            }.toString()
        }
        ui.onNodeWithText("Keep offline").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithText("Could not access this file.", substring = true).isDisplayed() }
        assertEquals(1, scene.peer.vaultRequests.size)
        ui.onAllNodesWithText("Available offline").assertCountEquals(0)
        captureNativeScreen(File(scene.evidence, "wrong-file.png"))
    }

    @Test fun aLocalStorageFailureDoesNotBlameSuccessfulFileAccessOrClaimAnOfflineCopy() = withVaultReadingFixture("storage-failure", captureFolder = "vault-recovery-evidence") { scene ->
        ui.openNativeVault()
        ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
        val owner = scene.reference.get("owner").asString
        val fileId = scene.reference.getAsJsonObject("file").get("id").asString
        val fault = File(scene.context.noBackupFilesDir, "vault.$owner.${SaitoBackend.OWNER.id}.copy.$fileId.info")
        check(fault.mkdir())
        try {
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not save the offline copy.", substring = true).isDisplayed() }
            assertEquals(1, scene.peer.vaultRequests.size)
            ui.onAllNodesWithText("Available offline").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "storage-failure.png"))
        } finally { check(fault.isDirectory && fault.listFiles().orEmpty().isEmpty() && fault.delete()) }
    }
    @Test fun aFailedDocumentExportClearsTemporaryBytesAndKeepsTheOfflineCopy() =
        withVaultReadingFixture("export-failure", captureFolder = "vault-recovery-evidence", failedExport = true) { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            fun storageText() = ui.onNodeWithText("Saved for this node:", substring = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
            val before = storageText()
            ui.onNodeWithText("Save as…").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Could not save this file.", substring = true).isDisplayed() }
            ui.waitUntil(10_000) { storageText() == before }
            ui.onNodeWithText("Available offline").assertIsDisplayed()
            ui.onAllNodesWithText("File saved").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "export-failure-cleared.png"))
        }

    @Test fun localExportStagingFailureExplainsDeviceStorageAndLeavesTheKeyUsable() =
        withVaultReadingFixture("export-staging", captureFolder = "vault-recovery-evidence") { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
            val owner = scene.reference.get("owner").asString
            val fault = File(scene.context.noBackupFilesDir, "vault.$owner.${SaitoBackend.OWNER.id}.export.info")
            check(fault.mkdir())
            try {
                ui.onNodeWithText("Save as…").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not prepare this file for export.", substring = true).isDisplayed() }
                ui.onNodeWithText("Keep offline").assertIsEnabled()
                ui.onAllNodesWithText("Could not access this file.", substring = true).assertCountEquals(0)
                captureNativeScreen(File(scene.evidence, "export-staging-failure.png"))
            } finally { if (fault.exists()) check(fault.isDirectory && fault.listFiles().orEmpty().isEmpty() && fault.delete()) }
        }

}

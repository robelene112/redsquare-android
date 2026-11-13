package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.vault.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual encrypted filesystem boundary and fresh native runtime; distinct-PID recovery is tested separately. */
class VaultCompletedCleanupPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aCompletedJournalRemainsRecoverableWhenSourceCleanupWasInterrupted() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("completed-cleanup", captureFolder = "vault-completed-cleanup-evidence", retainTransfers = true, minting = true) { scene ->
            ui.reviewVaultFile(scene, "vault-completed.bin", "The completed upload must survive interrupted local source cleanup".toByteArray())
            waitForSystemPin(device)
            val owner = AndroidWalletStore(scene.context).loadExisting().publicKey
            val metadata = scene.context.noBackupFilesDir.listFiles().orEmpty().single {
                it.name.startsWith("vault.$owner.owner.") && it.name.endsWith("draft.info")
            }
            val originalMetadata = metadata.readBytes()
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            val disk = VaultDisk(scene.context, owner, SaitoBackend.OWNER)
            assertFalse(disk.hasDraftData())
            // The successful upload has deleted its source chunks. Restoring its exact original
            // encrypted descriptor models termination between chunk deletion and descriptor unlink.
            metadata.writeBytes(originalMetadata)
            assertTrue(disk.hasDraftData())
            assertTrue("The original source descriptor must actually be incomplete", runCatching { disk.draft() }.isFailure)
            var endpoint: String? = null
            scene.activity.onActivity { endpoint = it.intent.getStringExtra("feedPeerUrl") }
            kotlinx.coroutines.runBlocking { VaultUploads.closeAndJoin(owner, SaitoBackend.OWNER, requireNotNull(endpoint)) }
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-completed.bin").isDisplayed() }
            assertFalse("Completion must finish the interrupted source cleanup", disk.hasDraftData())
            assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
            captureNativeScreen(File(scene.evidence, "completed-journal-wins-over-incomplete-source.png"))
        }
    }
}

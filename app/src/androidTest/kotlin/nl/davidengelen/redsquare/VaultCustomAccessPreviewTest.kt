package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import java.util.concurrent.CountDownLatch

class VaultCustomAccessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun wrongAccessInputsCanBeCorrectedBeforeKeepingTheExactCustomFileOffline() =
        withVaultReadingFixture("custom-access", custom = true, captureFolder = "vault-custom-access-evidence") { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Custom field notes.txt").isDisplayed() }
            ui.onNodeWithText("Access file").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("File access").isDisplayed() }
            ui.onNodeWithText("Access script").performScrollTo().performTextReplacement("not JSON")
            ui.onNodeWithText("Keep offline").performClick()
            val invalid = "Check the script and unlock inputs. The JSON or its values are not supported."
            ui.waitUntil(5_000) { ui.onNodeWithText(invalid).isDisplayed() }
            assertTrue(scene.peer.vaultRequests.isEmpty())
            // A valid floating literal exercises the Android-compatible formatter,
            // then must be refused because these rules change the original commitment.
            val altered = scene.reference.getAsJsonObject("script").deepCopy().apply {
                remove("witness"); add("editorNumber", com.google.gson.JsonParser.parseString("1e2"))
            }.toString()
            ui.onNodeWithText("Access script").performScrollTo().performTextReplacement(altered)
            ui.onNodeWithText("Keep offline").performClick()
            val changed = "These rules do not match this file. Restore the original script."
            ui.waitUntil(5_000) { ui.onNodeWithText(changed).isDisplayed() }
            assertTrue(scene.peer.vaultRequests.isEmpty())
            ui.onNodeWithText("Restore original script").performScrollTo().performClick()
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

    @Test fun stoppingFilePreparationRestartsTheCancelledFileDiscovery() {
        val reply = CountDownLatch(1)
        withVaultReadingFixture("discovery-restart", custom = true, captureFolder = "vault-custom-access-evidence",
            balanceReplyGate = reply) { scene ->
            val name = "unknown-stalled-discovery.bin"
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            val resolver = scene.context.contentResolver
            resolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", ByteArray(256 * 1024) { 73 }) })
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            try {
                ui.openNativeVault()
                ui.waitUntil(5_000) { scene.balanceRequests() > 0 }
                ui.onNodeWithText("Upload file").performScrollTo().performClick()
                ui.waitUntil(5_000) { ui.onNodeWithText("Preparing your file").isDisplayed() }
                ui.waitUntil(5_000) { resolver.call(source, "status", name, null)?.getBoolean("firstWritten") == true }
                ui.onNodeWithText("Cancel reading").performScrollTo().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Your files").isDisplayed() }
                ui.waitForIdle()
                reply.countDown()
                ui.waitUntil(15_000) { ui.onNodeWithText("Custom field notes.txt").isDisplayed() }
                assertTrue(scene.balanceRequests() >= 2)
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
                captureNativeScreen(File(scene.evidence, "cancelled-discovery-restarted-after-file-preparation.png"))
            } finally {
                reply.countDown()
                resolver.call(source, "release", name, null)
                ui.waitUntil(5_000) { resolver.call(source, "status", name, null)?.getInt("activeWorkers") == 0 }
            }
        }
    }

}

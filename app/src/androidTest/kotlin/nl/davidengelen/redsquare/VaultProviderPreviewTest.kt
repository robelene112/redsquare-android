package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultProviderPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aDelayedSharedProviderCanSurviveModuleChangesAndBeCancelledWithoutPublishing() =
        withVaultReadingFixture("delayed-provider", captureFolder = "vault-provider-evidence") { scene ->
            val name = "unknown-stalled-vault.bin"
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            val resolver = scene.context.contentResolver
            resolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", ByteArray(256 * 1024) { 73 }) })
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                scene.context.startActivity(Intent(scene.context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                    .setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, source)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                ui.waitUntil(15_000) { ui.onNodeWithText("Preparing file").isDisplayed() }
                ui.waitUntil(5_000) { resolver.call(source, "status", name, null)?.getBoolean("firstWritten") == true }
                captureNativeScreen(File(scene.evidence, "delayed-provider-preparing.png"))
                ui.switchStackModule("Vault", "Red Square")
                ui.switchStackModule("Red Square", "Vault", scrollTarget = true)
                ui.waitUntil(5_000) { ui.onNodeWithText("Preparing file").isDisplayed() }
                ui.onNodeWithText("Cancel reading").performScrollTo().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Your files").isDisplayed() }
                ui.onNodeWithText("File preparation stopped. Nothing was uploaded.").assertIsDisplayed()
                assertTrue(scene.peer.transactions.isEmpty())
                assertTrue(scene.peer.vaultUploads.isEmpty())
                captureNativeScreen(File(scene.evidence, "delayed-source-cancelled-no-write.png"))
            } finally {
                resolver.call(source, "release", name, null)
                ui.waitUntil(5_000) { resolver.call(source, "status", name, null)?.getInt("activeWorkers") == 0 }
            }
        }

    @Test fun aRevokedAndroidUriGrantShowsARecoverableReadErrorBeforeAnyPublication() =
        withVaultReadingFixture("revoked-provider", captureFolder = "vault-provider-evidence") { scene ->
            val name = "vault-granted-revoked.bin"
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            val resolver = scene.context.contentResolver
            resolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", "A granted file".toByteArray()) })
            requireNotNull(resolver.openInputStream(source)).use { assertEquals('A'.code, it.read()) }
            resolver.call(source, "revoke", name, null)
            try { resolver.openInputStream(source)?.close(); fail("The Android provider grant must actually be gone") }
            catch (_: SecurityException) { }
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.openNativeVault()
            ui.waitUntil(5_000) { ui.onNodeWithText("Upload file").isDisplayed() }
            ui.onNodeWithText("Upload file").performClick()
            ui.waitUntil(15_000) {
                ui.onNodeWithText("Could not read this file. Choose it again or try another file provider.").isDisplayed()
            }
            ui.onNodeWithText("Your files").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultUploads.isEmpty())
            ui.onAllNodesWithText("Create key and upload").assertCountEquals(0)
            captureNativeScreen(File(scene.evidence, "revoked-grant-read-error-before-write.png"))
        }
}

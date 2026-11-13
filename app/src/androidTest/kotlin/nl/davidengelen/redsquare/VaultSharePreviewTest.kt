package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultSharePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun androidShareOpensVaultReviewWithoutPublishingOrReplacingTheLaunchConfiguration() =
        withVaultReadingFixture("share", captureFolder = "vault-share-evidence") { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-shared.bin")
            scene.context.contentResolver.call(source, "prepare", "vault-shared.bin", Bundle().apply {
                putByteArray("bytes", "Shared into native Vault, not yet published".toByteArray())
            })
            val handlers = scene.context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream").setPackage(scene.context.packageName), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("Vault must appear as an Android Share target", handlers.any { it.activityInfo.name == MainActivity::class.java.name })
            scene.context.startActivity(Intent(scene.context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, source)
                .putExtra("vaultShareMarker", true).putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
            ui.onNodeWithText("vault-shared.bin").assertIsDisplayed()
            ui.onNodeWithText("1 SAITO").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultUploads.isEmpty())
            scene.activity.onActivity { assertFalse("A shared Intent must not replace node settings", it.intent.getBooleanExtra("vaultShareMarker", false)) }
            captureNativeScreen(File(scene.evidence, "shared-file-reviewed-before-any-write.png"))
            ui.onNodeWithText("Discard file").performScrollTo().performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Your files").isDisplayed() }
            val refresh = (hasContentDescription("Refresh Vault") or hasText("Refresh Vault")) and isEnabled()
            ui.waitUntil(5_000) { ui.onNode(refresh).isDisplayed() }
            val requestsBeforeRefresh = scene.balanceRequests()
            ui.onNode(refresh).performClick()
            ui.waitUntil(5_000) { scene.balanceRequests() > requestsBeforeRefresh }
            ui.waitUntil(5_000) { ui.onNode(refresh).isDisplayed() && ui.onNodeWithText("Field notes.txt").isDisplayed() }
            scene.peer.assertHealthy()
            captureNativeScreen(File(scene.evidence, "shared-review-discarded-original-node-refreshed.png"))
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultUploads.isEmpty())
        }
}

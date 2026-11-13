package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultUploadViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun theReviewedFileDepositAndActionsStayAccessibleInTwelveWidthFontAndThemeCells() {
        for (width in listOf(320, 412)) {
            withVaultReadingFixture("upload-viewport-$width", captureFolder = "vault-upload-viewport-evidence") { scene ->
                val name = "vault-long-reviewed-filename-for-accessibility.bin"
                val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
                scene.context.contentResolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", "Reviewed, not published".toByteArray()) })
                Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                    Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
                ui.openNativeVault()
                ui.waitUntil(5_000) { ui.onNodeWithText("Upload file").isDisplayed() }
                ui.onNodeWithText("Upload file").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
                ui.switchStackModule("Vault", "Red Square")
                ui.withPostViewports(scene.activity, scene.evidence, "vault-upload-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                    ui.switchStackModule("Red Square", "Vault", scrollTarget = true)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
                    ui.onNodeWithText(name).performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("1 SAITO").performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Network fee: 0 SAITO").performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(scene.evidence, "$label-reviewed-cost.png"))
                    ui.onNodeWithText("Create key and upload").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    ui.onNodeWithText("Discard file").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    captureNativeScreen(File(scene.evidence, "$label-reachable-actions.png"))
                    assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
                    ui.switchStackModule("Vault", "Red Square")
                    ui.waitUntil(5_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
            }
        }
    }
}

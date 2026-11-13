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
import org.junit.Rule
import org.junit.Test
import java.io.File

class VaultRulesViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule(VaultViewportMotionScale)
    @Test fun presetsRulesAndPublicationConsequencesRemainReachableInTwelveNativeCells() =
        ui.withVaultDialogViewports("rules", prepare = { scene ->
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/viewport-rules.bin")
            scene.context.contentResolver.call(source, "prepare", "viewport-rules.bin", Bundle().apply { putByteArray("bytes", byteArrayOf(1)) })
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            waitUntil(5_000) { onNodeWithText("Upload file").isDisplayed() }
            onNodeWithText("Upload file").performClick()
            waitUntil(15_000) { onNodeWithText("Review upload").isDisplayed() }
        }) { scene, label ->
            waitUntil(15_000) {
                onAllNodesWithText("Review upload").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            onNodeWithText("Review upload").performScrollTo().assertIsDisplayed()
            onNodeWithText("Change access rules").performScrollTo().performClick()
            waitUntil(5_000) { onNodeWithText("File access rules").isDisplayed() }
            onNode(hasText("Rental master key") and hasClickAction()).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            captureNativeScreen(File(scene.evidence, "$label-rental-rights.png"))
            onNode(hasText("Advanced") and hasClickAction()).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            onNodeWithText("Access script").performScrollTo().assertIsDisplayed()
            onNodeWithText("Use these rules").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            onNodeWithText("Close file access rules").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            captureNativeScreen(File(scene.evidence, "$label-advanced-source-actions.png"))
            onNodeWithText("Close file access rules").performClick()
            waitUntil(5_000) { onAllNodesWithText("File access rules").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        }
}

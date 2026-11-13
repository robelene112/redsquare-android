package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

class VaultUploadPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun choosingAFileShowsItsNativeReviewBeforeAnyMintOrUploadAndCanBeDiscarded() =
        withVaultReadingFixture("upload-review", captureFolder = "vault-upload-evidence") { scene ->
            val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-vault-mint-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-upload.bin")
            scene.context.contentResolver.call(source, "prepare", "vault-upload.bin", Bundle().apply {
                putByteArray("bytes", Base64.getDecoder().decode(reference.get("contentsBase64").asString))
            })
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
                Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.openNativeVault()
            ui.onNodeWithText("Upload file").assertIsDisplayed().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
            ui.onNodeWithText("vault-upload.bin").assertIsDisplayed()
            ui.onNodeWithText("Standard key").assertIsDisplayed()
            ui.onNodeWithText("1 SAITO").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultRequests.isEmpty())
            captureNativeScreen(File(scene.evidence, "review-before-submission.png"))
            ui.onNodeWithText("Discard file").performScrollTo().performClick()
            ui.waitUntil(10_000) { ui.onNodeWithText("Your files").isDisplayed() }
            ui.onAllNodesWithText("Review upload").assertCountEquals(0)
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.vaultRequests.isEmpty())
            captureNativeScreen(File(scene.evidence, "review-discarded.png"))
        }
}

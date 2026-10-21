package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** A saved local draft and later edits must not receive the same storage claim. */
class ComposerDraftStatusPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unconfirmedImageRemovalDoesNotClaimSavedAndCanBeRetriedAfterStorageReturns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "composer-draft-evidence").apply { mkdirs() }
        withAppFrameFixture("composer-media-storage", "composer-draft-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "composer-media-storage", scales = listOf(1.3f)) { label, _ ->
                val draft = "Keep this caption for $label."
                val editor = hasSetTextAction() and isEnabled()
                ui.onNode(hasText("New post") and hasClickAction()).performClick()
                ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                ui.onNode(editor).performTextReplacement(draft)
                ui.attachSyntheticImage()
                ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                val owner = AndroidWalletStore(context).loadExisting().publicKey
                val record = File(context.noBackupFilesDir, "postdraft.owner.$owner.v1")
                val original = record.readBytes()
                var replaced = false
                var primary: Throwable? = null
                try {
                    synchronized(nl.davidengelen.redsquare.saito.SaitoLocalState.lock) {
                        check(record.delete())
                        replaced = true
                        check(record.mkdir())
                    }
                    ui.onNodeWithText("Remove image 1").performScrollTo().assertIsEnabled().performClick()
                    val failure = context.getString(R.string.image_prepare_failed)
                    ui.waitUntil(10_000) {
                        ui.onAllNodesWithText(failure).fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodes(hasText("Remove image 1") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText(failure).performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(folder, "$label-unconfirmed-images.png"))
                    ui.onNodeWithText("Saved on this device").assertDoesNotExist()
                    ui.onNodeWithText("Unsaved changes").assertIsDisplayed()
                    ui.onNode(editor).assertTextEquals(draft)
                } catch (failure: Throwable) { primary = failure; throw failure }
                finally {
                    cleanupAfterTest(primary, {
                        if (replaced) synchronized(nl.davidengelen.redsquare.saito.SaitoLocalState.lock) {
                            if (record.isDirectory) check(record.delete())
                            else check(!record.exists() || record.isFile)
                            record.writeBytes(original)
                            assertTrue(record.readBytes().contentEquals(original))
                        }
                    }, { original.fill(0) })
                }
                // Repairing the fixture does not itself publish or acknowledge anything.
                ui.onNodeWithText("Remove image 1").performScrollTo().assertIsEnabled().performClick()
                ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Image 1 of 1").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                ui.onNode(editor).performScrollTo().assertTextEquals(draft)
                captureNativeScreen(File(folder, "$label-repaired-images.png"))
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            }
        }
    }

    @Test fun savedDraftAndNewEditsHaveDistinctTruthfulLocalStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "composer-draft-evidence").apply { mkdirs() }
        withAppFrameFixture("composer-draft", "composer-draft-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "composer-draft", scales = listOf(1.3f)) { label, _ ->
                val original = "First saved draft for $label."
                val edited = "An edited draft for $label."
                val editor = hasSetTextAction() and isEnabled()
                fun open() {
                    ui.onNode(hasText("New post") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                }
                fun home() {
                    ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                }
                open()
                ui.onNode(editor).performTextReplacement(original)
                ui.onNodeWithText("Save draft").performClick()
                home()
                open()
                ui.onNode(editor).assertTextEquals(original)
                captureNativeScreen(File(folder, "$label-saved.png"))
                ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                ui.onNode(editor).performTextReplacement(edited)
                ui.onNodeWithText("Unsaved changes").assertIsDisplayed()
                ui.onNodeWithText("Saved on this device").assertDoesNotExist()
                captureNativeScreen(File(folder, "$label-edited.png"))
                ui.onNodeWithText("Back").performClick()
                home()
                open()
                ui.onNode(editor).assertTextEquals(edited)
                ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                ui.onNodeWithText("Unsaved changes").assertDoesNotExist()
                captureNativeScreen(File(folder, "$label-returned.png"))
                ui.onNodeWithText("Save draft").performClick()
                home()
            }
        }
    }
}

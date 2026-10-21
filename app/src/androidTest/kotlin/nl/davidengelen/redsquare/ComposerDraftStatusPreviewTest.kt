package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A saved local draft and later edits must not receive the same storage claim. */
class ComposerDraftStatusPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

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

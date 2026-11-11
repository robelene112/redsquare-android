package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import nl.davidengelen.redsquare.feed.PublicStringStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StackLocalDraftTest {
    @get:Rule val ui = createEmptyComposeRule()

    private fun closeKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val keyboard = focusedWindowImeBottom() > 0
        if (keyboard) UiDevice.getInstance(instrumentation).pressBack()
        ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 && ui.onNodeWithContentDescription("Switch app: Stack").isDisplayed() }
    }

    @Test fun finalVisualCharacterSurvivesModuleChangeAndActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        withAppFrameFixture("stack-local-draft", "stack-draft-evidence", archiveResponse = { "[]" }) { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.switchStackModule("Red Square", "Stack")
            ui.onNodeWithContentDescription("Write an article").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Title")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("An actual local draft")
            val text = "The final visual character survives!"
            ui.onNodeWithContentDescription("Article body").performScrollTo().performTextReplacement(text)
            closeKeyboard()
            ui.switchStackModule("Stack", "Red Square")
            ui.switchStackModule("Red Square", "Stack")
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Article body").isDisplayed() }
            ui.onNodeWithContentDescription("Article body").assert(SemanticsMatcher("Exact final visual text remains, independently of renderer span annotations") {
                it.config[SemanticsProperties.EditableText].text == text
            })
            ui.onNodeWithText("Markdown").performScrollTo().performClick()
            ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo()
                .assert(SemanticsMatcher("Exact final visual character is in canonical Markdown") {
                    it.config[SemanticsProperties.EditableText].text.trim() == text
                })
            activity.recreate()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Markdown source")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo()
                .assert(SemanticsMatcher("Canonical Markdown remains after recreation") {
                    it.config[SemanticsProperties.EditableText].text.trim() == text
                })
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-draft-evidence/retained-draft.png"))
        }
    }

    @Test fun failedSavingKeepsTheEditorUntilTheSameDraftCanBeSaved() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        withAppFrameFixture("stack-draft-storage", "stack-draft-evidence", archiveResponse = { "[]" }) { activity ->
            val file = File(context.noBackupFilesDir, "stackdraft.${AndroidWalletStore(context).loadExisting().publicKey}.v1")
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.switchStackModule("Red Square", "Stack")
                ui.onNodeWithContentDescription("Write an article").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Title")).fetchSemanticsNodes().isNotEmpty() }
                synchronized(PublicStringStore.lock) { check(!file.exists()); check(file.mkdir()) }
                ui.onNodeWithText("Markdown").performScrollTo().performClick()
                val source = "## Keep this draft\n\nThe original **unsent** source.\n"
                ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(source)
                closeKeyboard()
                ui.onNodeWithContentDescription("Switch app: Stack").performClick()
                ui.onNode(hasText("Red Square") and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Could not save this draft. Your text stays here. Try again.").isDisplayed() }
                ui.onNodeWithContentDescription("Switch app: Stack").assertIsDisplayed()
                ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
                synchronized(PublicStringStore.lock) { assertTrue(file.delete()) }
                ui.revealAppNavigation()
                ui.switchStackModule("Stack", "Red Square")
                ui.switchStackModule("Red Square", "Stack")
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Markdown source")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
                activity.close()
                val coldIntent = android.content.Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                androidx.test.core.app.ActivityScenario.launch<MainActivity>(coldIntent).use {
                    ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
                    ui.switchStackModule("Red Square", "Stack")
                    ui.onNodeWithContentDescription("Write an article").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Markdown source")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo()
                        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
                    captureNativeScreen(File(context.cacheDir, "stack-draft-evidence/storage-recovered.png"))
                }
            } finally {
                activity.close()
                synchronized(PublicStringStore.lock) { if (file.isDirectory) check(file.delete()) }
            }
        }
    }
}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual saved-work screens and contextual recovery at320dp/130% in both themes. */
class PublishingWorkViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun savedAuthorChangesOpenTheSameUnsentTextWithTheirOriginalNodeInBothThemes() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val originalText = "An original post awaiting a local change."
        val original = postingArchiveRow(SaitoTransaction.create(wallet,
            RedSquarePosts.message(originalText), 1_700_000_000_000))
        val folder = File(context.cacheDir, "publishing-work-evidence").apply { mkdirs() }
        withAppFrameFixture("saved-author", "publishing-work-evidence", feedRows = "[$original]") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "saved-author", scales = listOf(1.3f)) { label, _ ->
                val text = "My retained changes for $label.\nKeep these words until I deliberately publish."
                ui.clickVisiblePostBody(originalText)
                ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                ui.clickVisiblePostMenuAction("Anon-${wallet.publicKey.take(6)}", "Edit")
                val editor = hasSetTextAction() and isEnabled()
                ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                ui.onNode(editor).performTextReplacement(text)
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                ui.onNodeWithText("More feed actions").performClick()
                ui.onNodeWithText("Saved post changes").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText(text).isDisplayed() }
                ui.onNodeWithText(SaitoBackend.OWNER.domain).assertIsDisplayed()
                ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                ui.onNodeWithText("Edit post").assertIsDisplayed().assertIsEnabled()
                captureNativeScreen(File(folder, "$label-list.png"), "Edit post")
                ui.onNodeWithText("Edit post").performClick()
                ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                ui.onNode(editor).assertTextEquals(text)
                captureNativeScreen(File(folder, "$label-reopened.png"))
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Saved post changes").isDisplayed() }
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            }
        }
    }

    @Test fun policyPausedQueueShowsItsOriginAndCancelsIntoTheSameEditableDraftInBothThemes() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val policy = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = policy.takeIf { it.exists() }?.readBytes()
        val candidates = mutableSetOf<String>()
        val folder = File(context.cacheDir, "publishing-work-evidence").apply { mkdirs() }
        var outerFailure: Throwable? = null
        try {
            // Migration-style protected fixture input, not a UI acceptance or real publication.
            policy.writeText("""{"revision":"owned-work-viewport","enforce":true,"ready":false}""")
            withAppFrameFixture("queued-work", "publishing-work-evidence") { activity ->
                var primary: Throwable? = null
                try {
                    ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                    ui.onNodeWithText("Later").performClick()
                    ui.withPostViewports(activity, folder, "queued-work", scales = listOf(1.3f)) { label, _ ->
                        val text = "My paused post for $label.\nKeep these exact words after cancellation."
                        // A recreated Activity retains composer memory. Prepare the latest draft through its real editor.
                        val editor = hasSetTextAction() and isEnabled()
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                        ui.onNode(editor).performTextReplacement(text)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.onNodeWithText("Save draft").performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                        val candidate = SaitoTransaction.create(wallet, RedSquarePosts.message(text), System.currentTimeMillis())
                        candidates.add(candidate.id)
                        PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
                            .save(PostDraftStore.Saved(text, candidate, queued = true))
                        ui.onNodeWithText("More feed actions").performClick()
                        ui.onNodeWithText("Queued posts").performClick()
                        ui.waitUntil(10_000) { ui.onNodeWithText(text).isDisplayed() }
                        ui.onNodeWithText(SaitoBackend.OWNER.domain).assertIsDisplayed()
                        ui.onNodeWithText("Queued").assertIsDisplayed()
                        ui.onNodeWithText("Cancel queued post").assertIsDisplayed().assertIsEnabled()
                        captureNativeScreen(File(folder, "$label-list.png"), "Cancel queued post")
                        ui.onNodeWithText("Cancel queued post").performClick()
                        ui.waitUntil(10_000) { ui.onNodeWithText("No queued posts").isDisplayed() }
                        captureNativeScreen(File(folder, "$label-empty.png"))
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                        ui.onNode(editor).assertTextEquals(text)
                        ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-editable.png"))
                        ui.onNodeWithText("Save draft").performClick()
                        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                    }
                } catch (failure: Throwable) { primary = failure; throw failure }
                finally {
                    cleanupAfterTest(primary, { activity.close() }, { cancelFixturePublishingJobs(context, candidates) })
                }
            }
        } catch (failure: Throwable) { outerFailure = failure; throw failure }
        finally {
            cleanupAfterTest(outerFailure, {
                if (previousPolicy == null) check(!policy.exists() || policy.delete()) else policy.writeBytes(previousPolicy)
            })
        }
    }
}

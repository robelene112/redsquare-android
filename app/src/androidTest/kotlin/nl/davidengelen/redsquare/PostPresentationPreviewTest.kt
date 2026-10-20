package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real post actions and identity through the native UI; the peer remains read-only. */
class PostPresentationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun postActionsKeepReportSecondaryAndFullIdentityReachable() =
        withAppFrameFixture("post-actions", "post-presentation-evidence") {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val row = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
            }
            val author = row.get("field2").asString
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithText("Report").assertDoesNotExist()
            val more = (hasText("Post actions for Anon-jesTu2") or
                hasContentDescription("Post actions for Anon-jesTu2")) and hasClickAction()
            val bounds = ui.onNode(more).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
            val minimum = 48 * context.resources.displayMetrics.density - 1
            assertTrue("Post actions must retain a full 48dp target", bounds.width >= minimum && bounds.height >= minimum)
            capture("feed-actions-secondary.png")
            ui.onNode(more).performClick()
            ui.onNodeWithText("Public identity").assertIsDisplayed().performClick()
            ui.onNodeWithText(author).assertIsDisplayed()
            capture("feed-full-public-identity.png")
            ui.onNodeWithText("Done").performClick()
            ui.onNode(more).performClick()
            ui.onNodeWithText("Report").assertIsDisplayed().performClick()
            ui.onNodeWithText(context.getString(R.string.report_confirm, "saito.davidengelen.nl")).assertIsDisplayed()
            capture("report-confirmation-after-menu-closed.png")
            ui.onNodeWithText(context.getString(R.string.report_cancel)).performClick()
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }

    @Test fun samePostKeepsIdentityTimeAndSecondaryActionsAcrossAllBrowseSurfaces() =
        withAppFrameFixture("post-contexts", "post-presentation-evidence") {
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            assertPostPresentation("feed")
            ui.onNodeWithText("Native Red Square reference post").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            assertPostPresentation("thread")
            ui.onNodeWithText("Back").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNodeWithContentDescription("View profile for Anon-jesTu2").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() &&
                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Quote") and hasClickAction())
            assertPostPresentation("profile")
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput("reference")
            ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Search history").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() &&
                ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Quote") and hasClickAction())
            assertPostPresentation("search")
        }

    private fun assertPostPresentation(surface: String) {
        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        ui.onNodeWithContentDescription("View profile for Anon-jesTu2").assertIsDisplayed().assertHasClickAction()
        // Independent fixture timestamp1700000000000, rendered on the UTC emulator.
        ui.onNode(hasText("11/14/23", substring = true) and hasText("10:13 PM", substring = true)).assertIsDisplayed()
        ui.onNodeWithContentDescription("Post actions for Anon-jesTu2").assertIsDisplayed().assertHasClickAction()
        for (action in listOf("Like", "Repost", "Quote"))
            ui.onNode(hasText(action) and hasClickAction()).assertIsDisplayed()
        ui.onNodeWithText("Report").assertDoesNotExist()
        capture("same-post-$surface.png")
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-presentation-evidence/$name"))
}

package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The same verified media/quote content through each real browsing route. */
class PostContentContextsTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun sameMediaAndQuoteRemainRecognizableInFeedThreadProfileAndSearchInBothThemes() {
        val reference = postContentFixture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "post-content-contexts-evidence").apply { mkdirs() }
        val profile = hasContentDescription("View profile for ${reference.authorName}") and hasClickAction()
        withAppFrameFixture("post-content-contexts", "post-content-contexts-evidence",
            feedRows = "[${reference.row}]") { activity ->
            ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "contexts", scales = listOf(1f)) { label, _ ->
                assertContent(reference, "$label-feed")
                ui.clickVisiblePostBody("Media and quote viewport preview")
                ui.waitUntil(15_000) { ui.onNodeWithText("Conversation").isDisplayed() &&
                    ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
                assertContent(reference, "$label-thread")
                home(profile)
                ui.clickVisiblePostProfile(reference.authorName)
                ui.waitUntil(15_000) { ui.onNode(hasText("Profile") and !hasClickAction()).isDisplayed() &&
                    ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
                assertContent(reference, "$label-profile")
                home(profile)
                ui.onNode(hasText("Search") and hasClickAction()).performClick()
                ui.onNode(hasSetTextAction()).performTextInput("Media and quote viewport")
                ui.waitUntil(15_000) { ui.onNode(hasText("Search history") and isEnabled()).isDisplayed() }
                ui.onNodeWithText("Search history").performClick()
                ui.waitUntil(15_000) {
                    ui.onNode(hasText("Media and quote viewport preview") and hasClickAction() and !hasSetTextAction())
                        .isDisplayed() && ui.onNode(hasText("Search history") and isEnabled()).isDisplayed()
                }
                assertContent(reference, "$label-search")
                home(profile)
            }
        }
    }

    private fun home(profile: SemanticsMatcher) {
        ui.onNode(hasText("Home") and hasClickAction()).performClick()
        ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
        ui.onNode(hasScrollAction()).performScrollToNode(profile)
        ui.onNode(profile).assertIsDisplayed()
    }

    private fun assertContent(reference: PostContentFixture, label: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = hasContentDescription("View profile for ${reference.authorName}") and hasClickAction()
        ui.onNode(hasScrollAction()).performScrollToNode(profile)
        ui.onNode(profile).assertIsDisplayed()
        ui.onNodeWithText("Media and quote viewport preview", useUnmergedTree = true).assertIsDisplayed()
        capture("$label-identity-body.png")
        ui.assertPostMediaContent(reference, quoteScrollDp = 24f) { suffix -> capture("$label-$suffix.png") }
        val actions = listOf("Like", "Repost", "Quote").map {
            hasContentDescription("$it post by ${reference.authorName}") and hasClickAction()
        }
        ui.onNode(hasScrollAction()).performScrollToNode(actions.last())
        val composer = hasText("New post") and hasClickAction()
        if (ui.onAllNodes(composer).fetchSemanticsNodes().isNotEmpty()) {
            ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                check(scroll(0f, 88 * context.resources.displayMetrics.density))
            }
            ui.waitForIdle()
        }
        val viewport = ui.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val minimum = 48 * context.resources.displayMetrics.density - 1
        val countPattern = Regex("[0-9]+ (?:likes?|reposts?)")
        val bounds = actions.map { target ->
            val node = ui.onNode(target).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
            assertFalse("Missing counts must remain absent from each route's accessible actions",
                countPattern.containsMatchIn(node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()))
            node.touchBoundsInRoot.also { rect ->
                assertTrue("Each route must retain complete reachable post actions", rect.width >= minimum &&
                    rect.height >= minimum && rect.left >= viewport.left && rect.right <= viewport.right &&
                    rect.top >= viewport.top && rect.bottom <= viewport.bottom)
                ui.onAllNodes(composer).fetchSemanticsNodes().forEach { floating ->
                    assertFalse(rect.overlaps(floating.touchBoundsInRoot))
                }
            }
        }
        bounds.forEachIndexed { index, left -> bounds.drop(index + 1).forEach { right ->
            assertFalse(left.overlaps(right))
        } }
        ui.onAllNodes(SemanticsMatcher("an observed social count") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { countPattern.matches(it.text) }
        }, useUnmergedTree = true).assertCountEquals(0)
        capture("$label-actions-without-counts.png")
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-content-contexts-evidence/$name"))
}

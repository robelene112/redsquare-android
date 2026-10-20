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

/** Verified content and actual image pixels through the same native post presentation. */
class PostContentViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun multipleImagesAndQuotedOriginalStayReadableWithoutInventedCountsAcrossTextSizesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = postContentFixture()
        val authorName = reference.authorName
        val countPattern = Regex("[0-9]+ (?:likes?|reposts?)")
        val countText = SemanticsMatcher("an observed social count") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { countPattern.matches(it.text) }
        }
        withAppFrameFixture("post-content", "post-content-evidence", feedRows = "[${reference.row}]") { activity ->
            val folder = File(context.cacheDir, "post-content-evidence").apply { mkdirs() }
            val profile = hasContentDescription("View profile for $authorName") and hasClickAction()
            ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "content") { label, _ ->
                ui.onNode(hasScrollAction()).performScrollToNode(profile)
                ui.onNode(profile).assertIsDisplayed()
                ui.onNodeWithText("Media and quote viewport preview", useUnmergedTree = true).assertExists()
                capture("$label-identity-body.png")
                ui.assertPostMediaContent(reference) { suffix -> capture("$label-$suffix.png") }
                val actions = listOf("Like", "Repost", "Quote").map {
                    hasContentDescription("$it post by $authorName") and hasClickAction()
                }
                ui.onNode(hasScrollAction()).performScrollToNode(actions.last())
                ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                    check(scroll(0f, 88 * context.resources.displayMetrics.density))
                }
                ui.waitForIdle()
                actions.forEach { action ->
                    val node = ui.onNode(action).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                    assertFalse("Missing counts must remain absent from the accessible action state",
                        countPattern.containsMatchIn(node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()))
                }
                ui.onAllNodes(countText, useUnmergedTree = true).assertCountEquals(0)
                capture("$label-actions-without-counts.png")
            }
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "post-content-evidence/$name"))
}

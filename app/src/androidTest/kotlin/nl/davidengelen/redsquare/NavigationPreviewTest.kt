package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public rendered text and actual native controls, using the agreed read-only frame peer. */
class NavigationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun largeTextKeepsTheFullFollowingLabelOnOneLine() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "navigation-evidence").apply { mkdirs() }
        withAppFrameFixture("navigation", "navigation-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "navigation", scales = listOf(2f)) { label, _ ->
                captureNativeScreen(File(folder, "$label.png"))
                val layouts = mutableListOf<TextLayoutResult>()
                ui.onNodeWithText("Following", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
                val layout = layouts.single()
                File(folder, "$label-layout.txt").writeText("size=${layout.size} widthOverflow=${layout.didOverflowWidth} heightOverflow=${layout.didOverflowHeight} exceeded=${layout.multiParagraph.didExceedMaxLines} paragraphHeight=${layout.multiParagraph.height} constraints=${layout.layoutInput.constraints} lineHeight=${layout.layoutInput.style.lineHeight}\n")
                assertEquals("Following must be a complete readable word", 1, layouts.single().lineCount)
                assertFalse("The full label must fit", layouts.single().hasVisualOverflow)
                ui.onNode(hasText("Following") and hasClickAction()).performClick()
                ui.onNode(hasText("Following") and isSelected()).assertIsDisplayed()
                ui.onNode(hasText("All posts") and hasClickAction()).performClick()
                ui.onNode(hasText("All posts") and isSelected()).assertIsDisplayed()
            }
        }
    }
}

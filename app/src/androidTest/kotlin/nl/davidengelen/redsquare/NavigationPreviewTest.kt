package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import android.graphics.Rect
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public rendered text and actual native controls, using the agreed read-only frame peer. */
class NavigationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun largeTextKeepsTheFullFollowingLabelOnOneLine() = verifyViewport(320)
    @Test fun pixelWidthKeepsNavigationReadableAtEveryTextSize() = verifyViewport(412)

    private fun verifyViewport(widthDp: Int) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "navigation-evidence").apply { mkdirs() }
        withAppFrameFixture("navigation", "navigation-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "navigation", widthDp = widthDp) { label, _ ->
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
                assertTargets(folder, label)
                ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                captureNativeScreen(File(folder, "$label.png"))
                val layouts = mutableListOf<TextLayoutResult>()
                ui.onNodeWithText("Following", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { check(it(layouts)) }
                val layout = layouts.single()
                File(folder, "$label-layout.txt").writeText("size=${layout.size} widthOverflow=${layout.didOverflowWidth} heightOverflow=${layout.didOverflowHeight} exceeded=${layout.multiParagraph.didExceedMaxLines} paragraphWidth=${layout.multiParagraph.width} paragraphHeight=${layout.multiParagraph.height} lineLeft=${layout.getLineLeft(0)} lineRight=${layout.getLineRight(0)} lineEnd=${layout.getLineEnd(0, visibleEnd = true)} ellipsized=${layout.isLineEllipsized(0)} constraints=${layout.layoutInput.constraints} lineHeight=${layout.layoutInput.style.lineHeight}\n")
                assertEquals("Following must be a complete readable word", 1, layouts.single().lineCount)
                // GetTextLayoutResult reports the paragraph's allocated width even when Text
                // paints a narrower intrinsic line. Verify the actual visible line, not that allocation.
                assertEquals("Following", layout.layoutInput.text.text)
                assertEquals("All nine letters must be visible", 9, layout.getLineEnd(0, visibleEnd = true))
                assertFalse("The label must not be ellipsized", layout.isLineEllipsized(0))
                assertTrue("The full label must fit", layout.getLineLeft(0) >= 0 &&
                    layout.getLineRight(0) <= layout.size.width && !layout.didOverflowHeight)
                ui.onNode(hasText("Following") and hasClickAction()).performClick()
                ui.onNode(hasText("Following") and isSelected()).assertIsDisplayed()
                ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
                captureNativeScreen(File(folder, "$label-following.png"))
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNode(hasText("Following") and isSelected()).isDisplayed() }
                ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                ui.onNode(hasText("All posts") and hasClickAction()).performClick()
                ui.onNode(hasText("All posts") and isSelected()).assertIsDisplayed()
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                ui.onNode(hasText("More feed actions") and hasClickAction()).performClick()
                ui.onNodeWithText("Queued posts").assertIsDisplayed()
                ui.onNodeWithText("Saved post changes").assertIsDisplayed()
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            }
        }
    }
    private fun assertTargets(folder: File, label: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val minimum = 48 * context.resources.displayMetrics.density - 1
        val rectangles = mutableListOf<Rect>()
        val observed = JsonObject()
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (name in listOf("Home", "Search", "Notifications", "Chat", "Your profile", "All posts", "Following")) {
            ui.onNode(hasText(name) and tab).assertIsDisplayed()
            var native = requireNotNull(device.findObject(By.text(name)))
            while (!native.isClickable && !native.isSelected) native = requireNotNull(native.parent)
            val bounds = native.visibleBounds
            assertTrue("$name retains a48dp native target", bounds.width() >= minimum && bounds.height() >= minimum)
            assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= device.displayWidth && bounds.bottom <= device.displayHeight)
            assertTrue("Navigation targets must not overlap", rectangles.none { Rect.intersects(it, bounds) })
            rectangles += bounds
            observed.addProperty(name, bounds.flattenToString())
            assertEquals("Only Home and All posts start selected", name in listOf("Home", "All posts"), native.isSelected)
        }
        File(folder, "$label-targets.json").writeText(JsonObject().apply {
            addProperty("minimumPixels", minimum); addProperty("disjoint", true); add("targets", observed)
        }.toString())
    }

}

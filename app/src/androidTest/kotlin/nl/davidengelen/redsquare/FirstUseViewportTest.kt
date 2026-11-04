package nl.davidengelen.redsquare

import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual first-use reading space, with the system's full font scale and native controls. */
class FirstUseViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowFirstUseRetainsUsefulReadingSpace() = verify(320)
    @Test fun pixelWidthFirstUseRetainsUsefulReadingSpace() = verify(412)

    private fun verify(width: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "first-use-evidence").apply { mkdirs() }
        val densityFailures = mutableListOf<String>()
        withAppFrameFixture("first-use-$width", "first-use-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.withPostViewports(activity, folder, "first-use", widthDp = width) { name, theme ->
                val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
                val list = ui.onNodeWithTag("feed-post-list").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val post = ui.onNodeWithTag("post-body-text", useUnmergedTree = true).assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                val compose = ui.onNode(hasText("New post") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
                    .fetchSemanticsNode().boundsInRoot
                for (target in listOf(hasContentDescription("Account"), hasText("Later"),
                    (hasText("Import an existing wallet") or hasContentDescription("Import an existing wallet")),
                    (hasText("Back up") or hasText("Back up your wallet")))) {
                    val bounds = ui.onNode(target and hasClickAction()).assertIsDisplayed().assertIsEnabled()
                        .fetchSemanticsNode().touchBoundsInRoot
                    assertTrue("First-use actions retain48dp at $name", bounds.width >= 47 && bounds.height >= 47)
                }
                ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
                ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                ui.onNode(hasText("Home") and isSelected()).assertIsDisplayed()
                var nativeCompose = requireNotNull(device.findObject(By.text("New post")))
                while (!nativeCompose.isClickable) nativeCompose = requireNotNull(nativeCompose.parent)
                assertTrue(nativeCompose.visibleBounds.width() >= 47 && nativeCompose.visibleBounds.height() >= 47)
                if (post.bottom > list.bottom) densityFailures.add("$name: first post body extends below reading viewport")
                if (context.resources.configuration.fontScale > 1.3f && compose.overlaps(list))
                    densityFailures.add("$name: compose action overlaps reading viewport")
                if (width == 320 && context.resources.configuration.fontScale > 1.3f) {
                    if (list.height < viewport.height * .32f) densityFailures.add("$name: reading list ${list.height} of ${viewport.height}")
                    if (compose.height > list.height * .25f) densityFailures.add("$name: compose ${compose.height} dominates reading list ${list.height}")
                }
                File(folder, "$name-density.json").writeText(JsonObject().apply {
                    addProperty("viewport", viewport.toString()); addProperty("list", list.toString())
                    addProperty("post", post.toString()); addProperty("compose", compose.toString())
                    addProperty("fontScale", context.resources.configuration.fontScale)
                    addProperty("widthDp", context.resources.configuration.screenWidthDp)
                }.toString())
                captureNativeScreen(File(folder, "$name.png"))
                if (context.resources.configuration.fontScale > 1.3f && theme == "Dark") {
                    val draft = "Unsent large-text post for $width"
                    val editor = hasSetTextAction() and isEnabled()
                    ui.onNode(hasText("New post") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                    ui.onNode(editor).performTextReplacement(draft)
                    ui.onNode(editor).performClick()
                    ui.waitUntil(10_000) { imeVisible() == true }
                    ui.onNode(editor).assertTextContains(draft)
                    captureNativeScreen(File(folder, "$name-writing-ime.png"))
                    device.pressBack()
                    ui.waitUntil(10_000) { imeVisible() == false }
                    ui.onNode(hasText("Save draft") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                    ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
                    ui.onNode(hasText("New post") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                    ui.onNode(editor).assertTextContains(draft)
                    device.pressBack()
                    ui.waitUntil(10_000) { imeVisible() == false }
                    ui.onNode(hasText("Save draft") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                }
            }
            // Keep all before/after render cells even when the new density goal fails.
            assertTrue(densityFailures.joinToString("\n"), densityFailures.isEmpty())
        }
    }
    private fun imeVisible(): Boolean? {
        var visible: Boolean? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            visible = root?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) }
        }
        return visible
    }

}

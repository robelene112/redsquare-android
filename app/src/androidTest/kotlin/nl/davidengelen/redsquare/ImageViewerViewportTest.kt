package nl.davidengelen.redsquare

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Native narrow/large-text/theme and real landscape viewer renders, with platform restoration. */
class ImageViewerViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun viewerChromeAndImageRemainReachableAcrossThemesTextSizesAndLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val folder = File(context.cacheDir, "image-viewer-viewport-evidence").apply { mkdirs() }
        check(device.isNaturalOrientation)
        val rotation = device.executeShellCommand("settings get system user_rotation").trim()
        val automatic = device.executeShellCommand("settings get system accelerometer_rotation").trim()
        check(rotation in listOf("null", "0", "1", "2", "3") && automatic in listOf("null", "0", "1"))
        val fixture = JsonParser.parseReader(instrumentation.context.assets.open("native-images-reference.json").reader()).asJsonObject
        var primary: Throwable? = null
        try {
            withAppFrameFixture("viewer-viewport", "image-viewer-viewport-evidence", "[${fixture.getAsJsonObject("ordinary")}]") { activity ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.withPostViewports(activity, folder, "viewer") { label, _ ->
                    exercise(folder, label)
                    if (context.resources.configuration.fontScale > 1.9f) {
                        device.setOrientationLeft()
                        ui.waitUntil(15_000) {
                            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                                ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed()
                        }
                        File(folder, "$label-landscape-configuration.json").writeText(JsonObject().apply {
                            addProperty("orientation", context.resources.configuration.orientation)
                            addProperty("widthDp", context.resources.configuration.screenWidthDp)
                            addProperty("fontScale", context.resources.configuration.fontScale)
                        }.toString())
                        exercise(folder, "$label-landscape")
                        device.setOrientationNatural()
                        ui.waitUntil(15_000) {
                            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                                ui.onNode(hasText("Home") and isSelected()).isDisplayed()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { device.setOrientationNatural() }, { device.unfreezeRotation() },
                { restore(device, "user_rotation", rotation) }, { restore(device, "accelerometer_rotation", automatic) })
        }
    }

    private fun exercise(folder: File, label: String) {
        val attachment = hasContentDescription("Image 1 of 2") and hasClickAction()
        ui.waitUntil(15_000) { ui.onNode(hasText("Refresh") and isEnabled()).isDisplayed() }
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(attachment)
        ui.onNode(attachment).performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("Close image").isDisplayed() }
        for (name in listOf("Close image", "Previous image", "Next image", "Zoom in", "Zoom out", "Reset zoom")) {
            ui.onNode(hasText(name) and hasClickAction()).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        ui.onNodeWithContentDescription("Full screen image 1 of 2").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        captureNativeScreen(File(folder, "$label-fit.png"), "Image 1 of 2")
        ui.onNodeWithText("Zoom in").performClick()
        ui.onNodeWithText("150%").assertIsDisplayed()
        captureNativeScreen(File(folder, "$label-zoom.png"), "150%")
        ui.onNodeWithText("Close image").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Close image").fetchSemanticsNodes().isEmpty() }
        ui.onNode(attachment).assertIsDisplayed()
    }

    private fun restore(device: UiDevice, key: String, value: String) {
        device.executeShellCommand(if (value == "null") "settings delete system $key" else "settings put system $key $value")
        assertEquals(value, device.executeShellCommand("settings get system $key").trim())
    }
}

package nl.davidengelen.redsquare

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

/** A visible unsent profile preview, real IME and local Save draft round-trip. */
class ProfileEditorPresentationTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun editedBioHasAVisibleProfilePreviewAndDraftRecoveryWithoutPublishingAcrossTextSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val folder = File(context.cacheDir, "profile-editor-presentation-evidence").apply { mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        withAppFrameFixture("profile-editor", "profile-editor-presentation-evidence", "[]",
            profileArchiveResponse = { criteria ->
                assertEquals(wallet.publicKey, criteria.get("field2").asString)
                "[]"
            }) { activity ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "profile-editor") { label, _ ->
                withFailureCapture({
                    File(folder, "$label-failure.semantics.txt").writeText(
                        ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
                }) {
                    val bio = "My locally edited biography for $label"
                    ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
                    openEditor()
                    writeBio(bio)
                    captureNativeScreen(File(folder, "$label-writing-ime.png"))
                    check(device.pressBack())
                    ui.waitUntil(10_000) { focusedWindowImeBottom() == 0 }
                    // The biography must appear in the profile, independently of its editable field.
                    val previewBio = hasText(bio) and !hasSetTextAction()
                    ui.onNode(previewBio).performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(folder, "$label-unsent-preview.png"))
                    ui.onNodeWithText("Change profile picture").performScrollTo().assertIsEnabled()
                    ui.onNodeWithText("Change banner").performScrollTo().assertIsEnabled()
                    writeBio(bio)
                    physicalSaveDraft(File(folder, "$label-save-draft-target.json"))
                    ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction()).isDisplayed() }
                    ui.onAllNodesWithText(bio).assertCountEquals(0)
                    openEditor()
                    ui.onNode(hasSetTextAction()).assert(hasText(bio))
                    ui.onNode(previewBio).performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(folder, "$label-recovered-preview.png"))
                    ui.onNode(hasText("Back") and hasClickAction()).performClick()
                    ui.waitUntil(15_000) { ui.onNode(hasText("Edit profile") and hasClickAction()).isDisplayed() }
                    ui.onNode(hasText("Back") and hasClickAction()).performClick()
                    ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
            }
        }
    }

    private fun openEditor() {
        val edit = hasText("Edit profile") and hasClickAction() and isEnabled()
        ui.waitUntil(15_000) { ui.onNode(edit).isDisplayed() }
        ui.onNode(edit).performClick()
        ui.waitUntil(15_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
    }

    private fun writeBio(bio: String) {
        ui.onNode(hasSetTextAction()).performScrollTo().performClick().performTextReplacement(bio)
        ui.waitUntil(10_000) { focusedWindowImeBottom() > 0 }
        ui.onNode(hasSetTextAction()).assert(hasText(bio))
    }

    private fun physicalSaveDraft(evidence: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val action = hasText("Save draft") and hasClickAction() and isEnabled()
        ui.waitUntil(15_000) { ui.onNode(action).isDisplayed() }
        var target = requireNotNull(device.findObject(By.text("Save draft")))
        while (!target.isClickable) target = requireNotNull(target.parent)
        val bounds = target.visibleBounds
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val minimum = kotlin.math.round(48 * density).toInt()
        val ime = focusedWindowImeBottom()
        evidence.writeText(JsonObject().apply {
            addProperty("targetBounds", bounds.toShortString())
            addProperty("minimumTargetPixels", minimum)
            addProperty("focusedWindowImeBottom", ime)
            addProperty("displayHeight", device.displayHeight)
        }.toString())
        check(ime > 0 && bounds.width() >= minimum && bounds.height() >= minimum && bounds.top >= 0 &&
            bounds.bottom <= device.displayHeight - ime)
        check(device.click(bounds.centerX(), bounds.centerY()))
    }
}

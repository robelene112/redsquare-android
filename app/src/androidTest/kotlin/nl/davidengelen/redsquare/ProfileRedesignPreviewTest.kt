package nl.davidengelen.redsquare

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real signed public profile, with a visible boundary between identity and its history. */
class ProfileRedesignPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun biographyHasAVisibleBoundaryBeforeTheHistoryTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val profile = instrumentation.context.assets.open("native-profile-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("snapshot")
        }
        val author = profile.get("field2").asString
        withAppFrameFixture("profile-boundary", "profile-redesign-evidence", profileArchiveResponse = {
            if (it.get("field2").asString == author) "[$profile]" else "[]"
        }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.clickVisiblePostProfile("Anon-${author.take(6)}")
            ui.waitUntil(15_000) { ui.onNodeWithText("Signed bio 🌱\nSecond line").isDisplayed() }
            ui.waitUntil(10_000) { ui.onNodeWithTag("profile-history-divider").isDisplayed() }
            val line = ui.onNodeWithTag("profile-history-divider")
            val bounds = line.getUnclippedBoundsInRoot()
            assertTrue("A real visual divider spans the profile", (bounds.right - bounds.left).value >= 240)
            val pixels = line.captureToImage().asAndroidBitmap()
            assertTrue(pixels.height >= 1)
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "profile-redesign-evidence/biography-history-boundary.png"))
        }
    }
}

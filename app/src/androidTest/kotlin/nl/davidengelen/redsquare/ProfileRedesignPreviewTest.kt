package nl.davidengelen.redsquare

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
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
            // Independent reference: #85 ProfileBlock, pinned c95ecaa01,
            // cover128, separate avatar64 row, fourteen dp between sections.
            val cover = ui.onNodeWithTag("profile-cover").assertIsDisplayed().getUnclippedBoundsInRoot()
            val avatar = ui.onNodeWithTag("profile-avatar").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertEquals("Use the approved prototype cover height", 128f, (cover.bottom - cover.top).value, 0.5f)
            assertEquals("Use the approved prototype avatar size", 64f, (avatar.right - avatar.left).value, 0.5f)
            assertTrue("The avatar belongs below the cover, without overlap", (avatar.top - cover.bottom).value >= 13.5f)
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("profile-history-divider").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("profile-history-divider").performScrollTo().assertIsDisplayed()
            val line = ui.onNodeWithTag("profile-history-divider")
            val bounds = line.getUnclippedBoundsInRoot()
            assertTrue("A real visual divider spans the profile", (bounds.right - bounds.left).value >= 240)
            val pixels = line.captureToImage().asAndroidBitmap()
            assertTrue(pixels.height >= 1)
            val selectedTab = ui.onNode(hasText("Posts") and isSelected()).assertHeightIsAtLeast(48.dp)
            val selected = selectedTab.captureToImage().asAndroidBitmap()
            val fill = selected.getPixel(8.coerceAtMost(selected.width - 1), selected.height / 2)
            assertTrue("Selected history uses the prototype's filled rounded chip, not an underline",
                fill == android.graphics.Color.rgb(42, 48, 61) || fill == android.graphics.Color.rgb(232, 237, 247))
            val chipBounds = selectedTab.getUnclippedBoundsInRoot()
            val chipHeight = (chipBounds.bottom - chipBounds.top).value
            val paintRows = (0 until selected.height).count { y -> selected.getPixel(8.coerceAtMost(selected.width - 1), y) == fill }
            val paintHeight = paintRows * chipHeight / selected.height
            assertTrue("The prototype chip paints32dp inside its48dp touch target", paintHeight in 27f..33.5f)
            captureNativeScreen(File(instrumentation.targetContext.cacheDir, "profile-redesign-evidence/biography-history-boundary.png"))
        }
    }
}

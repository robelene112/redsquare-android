package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend

/** Separate, explicitly enabled public read. The harness clears only the emulator's .dev app first. */
class OwnerLiveFeedTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun freshInstallationReadsTheSelectedApprovedPublicFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("publicFeedRead") == "1")
        val selected = InstrumentationRegistry.getArguments().getString("publicBackend") ?: "owner"
        require(selected == "owner" || selected == "official")
        val backend = if (selected == "official") SaitoBackend.OFFICIAL else SaitoBackend.OWNER
        // Setup through the real selection store before launch. The separate
        // controlled UI tests prove switching; this read must not see owner
        // content while a first official connection is still loading.
        AndroidBackendStore(instrumentation.targetContext).save(backend)
        val identity = Regex("[1-9A-HJ-NP-Za-km-z]{44,45}")
        val visibleAuthor = SemanticsMatcher("Visible Saito post author") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { identity.matches(it.text) } == true
        }
        val directory = File(instrumentation.targetContext.cacheDir, "live-feed-evidence").apply { mkdirs() }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use {
            try {
                ui.waitUntil(30_000) { ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Server: ${backend.domain}").assertIsDisplayed()
                ui.onAllNodes(visibleAuthor)[0].assertIsDisplayed()
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText("Refresh").assertIsEnabled()
                ui.waitForIdle()
                instrumentation.waitForIdleSync()
                val feedImage = ui.onRoot().captureToImage().asAndroidBitmap()
                File(directory, "feed.png").outputStream().use {
                    check(feedImage.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                val visible = ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().size
                File(directory, "result.txt").writeText("PASS: $visible visible author identities on the $selected fresh native feed.\nPublic read only; signature verification does not prove chain inclusion.\n")
            } finally {
                captureFeedEvidence("live-feed-evidence/screen.png")
            }
        }
    }
}

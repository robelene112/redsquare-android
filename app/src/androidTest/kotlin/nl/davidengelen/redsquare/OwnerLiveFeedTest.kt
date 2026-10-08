package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Separate, explicitly enabled public read. The harness clears only the emulator's .dev app first. */
class OwnerLiveFeedTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun freshInstallationReadsTheOwnersPublicFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(InstrumentationRegistry.getArguments().getString("publicFeedRead") == "1")
        val identity = Regex("[1-9A-HJ-NP-Za-km-z]{44,45}")
        val visibleAuthor = SemanticsMatcher("Visible Saito post author") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { identity.matches(it.text) } == true
        }
        val directory = File(instrumentation.targetContext.cacheDir, "live-feed-evidence").apply { mkdirs() }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use {
            try {
                ui.waitUntil(30_000) { ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().isNotEmpty() }
                val visible = ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().size
                File(directory, "result.txt").writeText("PASS: $visible visible author identities on the owner's fresh native feed.\nPublic read only; signature verification does not prove chain inclusion.\n")
            } finally {
                captureFeedEvidence("live-feed-evidence/screen.png")
            }
        }
    }
}

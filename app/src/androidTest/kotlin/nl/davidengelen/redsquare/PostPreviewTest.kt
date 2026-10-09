package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PostPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun savedDraftSurvivesReopeningAndRemainsUnsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("New post").performClick()
                    ui.onNode(hasSetTextAction()).performTextReplacement("Draft kept until Send")
                    ui.onNodeWithText("Save draft").performClick()
                    waitFor("Native Red Square reference post")
                    scenario.recreate()
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Draft kept until Send")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Draft kept until Send")
                    peer.assertHealthy()
                    assertTrue("Saving or reopening a draft must not publish it", peer.transactions.isEmpty())
                    captureFeedEvidence("posting-evidence/recovered-unsent-draft.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/draft-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

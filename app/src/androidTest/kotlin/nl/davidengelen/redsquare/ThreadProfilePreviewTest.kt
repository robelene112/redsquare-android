package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.junit.Rule
import org.junit.Test

class ThreadProfilePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun openingAFeedPostShowsAvailableVerifiedConversationReplies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        nl.davidengelen.redsquare.saito.AndroidBackendStore(context)
            .save(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER)
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(context.noBackupFilesDir, it).delete()
        }
        val fixture = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        val root = fixture.getAsJsonObject("root")
        val rootId = root.get("sig").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            check(criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field4") -> { check(criteria.get("field4").asString.isEmpty()); "[$root]" }
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == rootId)
                    "[$root,${fixture.get("firstReply")},${fixture.get("nestedReply")},${fixture.get("tamperedReply")}]"
                }
                criteria.has("sig") -> { check(criteria.get("sig").asString == rootId); "[$root]" }
                else -> error("Unexpected conversation archive criteria")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("Independent Saito reply")).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Nested Saito reply"))
                    ui.onNodeWithText("Nested Saito reply").assertIsDisplayed()
                    ui.onNodeWithText("Forged reply").assertDoesNotExist()
                    ui.onNodeWithText("Back").assertIsDisplayed()
                    peer.assertHealthy()
                    captureFeedEvidence("browse-evidence/verified-conversation.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("browse-evidence/conversation-first-failure.png")
                    throw failure
                }
            }
        }
    }
}

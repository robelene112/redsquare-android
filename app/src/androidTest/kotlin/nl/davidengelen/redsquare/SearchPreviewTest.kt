package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SearchPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun anArchivedReplyCanBeFoundAndExploredWithoutLosingTheSearchOrWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val reference = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val root = reference.getAsJsonObject("root")
        val reply = reference.getAsJsonObject("firstReply")
        val author = reply.get("field2").asString
        val searches = AtomicInteger()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field4") -> "[]" // General feed has no matching reply.
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == root.get("sig").asString)
                    "[$root,$reply]"
                }
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    "[$reply]"
                }
                else -> {
                    check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                    check(criteria.get("limit").asInt == 100 && criteria.get("created_earlier_than").asLong > 0)
                    searches.incrementAndGet()
                    "[$root,$reply]"
                }
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/reply-first-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val original = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onAllNodesWithText("Independent Saito reply").assertCountEquals(0)
                    ui.onNodeWithText("Search").assertIsDisplayed().performClick()
                    ui.onNode(hasSetTextAction()).performTextInput("Independent")
                    ui.onNodeWithText("Search history").assertIsEnabled().performClick()
                    waitForText("Independent Saito reply")
                    assertTrue("Search must read the selected backend beyond its root feed", searches.get() > 0)
                    ui.onNodeWithText("Only available history is searched. Older posts and people may be missing.").assertIsDisplayed()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed().performClick()
                    waitForEnabled("Reply")
                    ui.onNodeWithText("Conversation").assertIsDisplayed()
                    ui.onNode(hasText("Anon-${author.take(6)}") and hasParent(hasAnyChild(hasText("Independent Saito reply"))),
                        useUnmergedTree = true).performClick()
                    waitForText("Profile")
                    ui.onNodeWithText(author).assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitForText("Conversation")
                    ui.onNodeWithText("Back").performClick()
                    waitForText("Independent Saito reply")
                    ui.onNode(hasSetTextAction()).assertTextContains("Independent")
                    scenario.recreate()
                    waitForText("Independent Saito reply")
                    ui.onNode(hasSetTextAction()).assertTextContains("Independent")
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/reply-search-recreated.png"), "Independent Saito reply")
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == original })
                    assertTrue("Search and navigation must never send a transaction", peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    private fun waitForText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
}

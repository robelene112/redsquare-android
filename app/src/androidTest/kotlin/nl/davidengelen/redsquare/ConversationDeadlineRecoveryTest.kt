package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ConversationDeadlineRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun timedOutThreadStillRecoversItsIndependentlyCachedParent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val backends = AndroidBackendStore(context); val oldBackend = backends.load()
        val thread = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val root = thread.getAsJsonObject("root")
        val reply = thread.getAsJsonObject("firstReply")
        val parent = RedSquareFeed.readArchive("[$root]").posts.single()
        val home = instrumentation.context.assets.open("native-following-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        val retained = ChatFixtureState(context, listOf("feed.owner.", "search.owner.", "quoteoriginal.owner.",
            "conversation.owner.${parent.id}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val threadReads = AtomicInteger()
        lateinit var peer: ControlledFeedPeer
        peer = ControlledFeedPeer("[$home]", profileArchiveResponse = { "[]" }, archiveResponse = { criteria ->
            val isThread = criteria.has("field5") && criteria.get("field5").asString == parent.id
            peer.withholdArchiveReply = isThread
            if (isThread) threadReads.incrementAndGet()
            when {
                criteria.get("field1")?.asString != "RedSquare" -> "[]"
                isThread -> "[$reply]"
                !criteria.has("field4") && criteria.get("limit")?.asInt == 100 -> "[$reply]"
                else -> "[$home]"
            }
        })
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            PublicStringStore(context, "quoteoriginal.owner.v1", 100).save(listOf(parent.envelope))
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ capture(context, "conversation-failure") }) {
                        ui.waitUntil(20_000) { ui.onNode(hasText("Search") and hasClickAction()).isDisplayed() }
                        ui.onNode(hasText("Search") and hasClickAction()).performClick()
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextInput("Independent Saito reply")
                        ui.onNode(hasSetTextAction() and isEnabled()).performImeAction()
                        ui.waitUntil(20_000) { ui.onNodeWithText("Independent Saito reply").isDisplayed() }
                        ui.clickVisiblePostBody("Independent Saito reply")
                        ui.waitUntil(20_000) { threadReads.get() > 0 }
                        ui.waitUntil(20_000) {
                            ui.onAllNodesWithText(context.getString(R.string.conversation_refresh_error_summary)).fetchSemanticsNodes().isNotEmpty() &&
                                ui.onAllNodesWithText(parent.text).fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNodeWithText(parent.text).assertIsDisplayed()
                        ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                        capture(context, "conversation-deadline-parent-recovered")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Read recovery must preserve the wallet" }
                        check(backends.load() == SaitoBackend.OWNER && peer.transactions.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() },
            { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(oldBackend) }, { retained.restorePreferences() }) }
    }

    private fun capture(context: android.content.Context, name: String) =
        captureNativeScreen(File(context.cacheDir, "conversation-deadline-evidence/$name.png"))
}

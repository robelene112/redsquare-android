package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StackDeadlineRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun stackDeadlineKeepsCachedArticleAndRetryLoadsActualNewWriting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val backends = AndroidBackendStore(context); val oldBackend = backends.load()
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        fun article(title: String, timestamp: Long): JsonObject {
            val body = JsonObject().apply {
                addProperty("module", "Stack"); addProperty("request", "create stack post request")
                add("data", JsonObject().apply {
                    addProperty("type", "stack_post"); addProperty("title", title)
                    addProperty("content", "Public controlled writing for $title.")
                    addProperty("excerpt", "Owned native deadline fixture")
                })
            }
            return JsonObject().apply {
                addProperty("created_at", timestamp)
                addProperty("tx", SaitoTransaction.create(wallet, body, timestamp).envelope())
            }
        }
        val cachedTitle = "Cached Stack reading before deadline"
        val freshTitle = "Fresh Stack writing after Retry"
        val cached = SaitoStack.readArchive("[${article(cachedTitle, 1700001000000)}]").articles.single()
        val fresh = article(freshTitle, 1700001000001)
        val retained = ChatFixtureState(context, listOf("feed.owner.", "stack.owner.",
            "stackwindow.owner.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val holding = AtomicBoolean(true)
        val reads = AtomicInteger()
        lateinit var peer: ControlledFeedPeer
        peer = ControlledFeedPeer("[$root]", profileArchiveResponse = { "[]" }, archiveResponse = { criteria ->
            val stack = criteria.get("field1")?.asString == "Stack"
            peer.withholdArchiveReply = stack && holding.get()
            if(stack) { reads.incrementAndGet(); "[$fresh]" }
            else if(criteria.get("field1")?.asString == "RedSquare") "[$root]" else "[]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            PublicStringStore(context, "stack.owner.v1", 100).save(listOf(cached.envelope))
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ capture(context, "stack-failure") }) {
                        ui.waitUntil(20_000) { ui.onNodeWithContentDescription("Switch app: Red Square").isDisplayed() }
                        ui.switchStackModule("Red Square", "Stack")
                        ui.waitUntil(20_000) { reads.get() > 0 && ui.onAllNodesWithText(cachedTitle).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(cachedTitle).assertIsDisplayed()
                        val error = context.getString(R.string.stack_load_failed)
                        ui.waitUntil(20_000) {
                            runCatching {
                                ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(error))
                                ui.onNodeWithText(error).isDisplayed()
                            }.getOrDefault(false)
                        }
                        ui.onNodeWithText(error).assertIsDisplayed()
                        ui.onNodeWithText(cachedTitle).assertIsDisplayed()
                        capture(context, "stack-deadline-cached-reading")
                        holding.set(false); peer.withholdArchiveReply = false
                        val before = reads.get()
                        ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                        ui.waitUntil(20_000) { reads.get() > before && ui.onAllNodesWithText(freshTitle).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(freshTitle).assertIsDisplayed()
                        ui.onAllNodesWithText(error).assertCountEquals(0)
                        ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText(cachedTitle))
                        ui.onNodeWithText(cachedTitle).assertIsDisplayed()
                        capture(context, "stack-retry-new-writing")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Stack retry must preserve the wallet" }
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
        captureNativeScreen(File(context.cacheDir, "stack-deadline-evidence/$name.png"))
}

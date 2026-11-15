package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.feed.AndroidFeedCache
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** An authenticated online peer withholds replies, without changing the real request deadline. */
class ReadDeadlineRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun firstFeedReadDeadlineShowsRetryAndPollingRecoversWithoutNavigation() = exercise(false)
    @Test fun cachedFeedStaysReadableDuringDeadlineAndPollingRecoversWithoutNavigation() = exercise(true)

    private fun exercise(cached: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val backends = AndroidBackendStore(context)
        val oldBackend = backends.load()
        val fixture = instrumentation.context.assets.open("native-following-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        val post = RedSquareFeed.readArchive("[$fixture]").posts.single()
        val mode = if(cached) "cached-feed" else "first-feed"
        val retained = ChatFixtureState(context, listOf("feed.${SaitoBackend.OWNER.id}.",
            "personal.${wallet.publicKey}.", "authorposts.${SaitoBackend.OWNER.id}.${post.id}."), wallet.publicKey)
        val reads = AtomicInteger()
        val withheldAt = AtomicLong()
        val peer = ControlledFeedPeer("[$fixture]", profileArchiveResponse = { "[]" },
            archiveResponse = { criteria -> if(criteria.get("field1")?.asString == "RedSquare") "[$fixture]" else "[]" },
            onArchiveResponse = { criteria, _, queued ->
                if(criteria.get("field1")?.asString == "RedSquare" && criteria.get("field4")?.asString == "") {
                    reads.incrementAndGet()
                    if(!queued) withheldAt.compareAndSet(0, SystemClock.elapsedRealtime())
                }
            }).apply { withholdArchiveReply = true }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            if(cached) AndroidFeedCache(context, SaitoBackend.OWNER).save(listOf(post))
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ capture(context, "$mode-failure") }) {
                        ui.waitUntil(20_000) { withheldAt.get() > 0 }
                        if(cached) ui.onNodeWithText(post.text, useUnmergedTree = true).assertIsDisplayed()
                        ui.waitUntil(20_000) { ui.onAllNodesWithText("Node unavailable").fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Node unavailable").assertIsDisplayed()
                        ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled()
                        val failedAt = SystemClock.elapsedRealtime()
                        if(cached) ui.onNodeWithText(post.text, useUnmergedTree = true).assertIsDisplayed()
                        capture(context, "$mode-deadline")
                        val before = reads.get()
                        peer.withholdArchiveReply = false
                        // No tap, Activity replacement or lifecycle restart triggers this read.
                        ui.waitUntil(20_000) { reads.get() > before &&
                            ui.onAllNodesWithText(post.text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodesWithText("Node unavailable").fetchSemanticsNodes().isEmpty() }
                        ui.onNodeWithText(post.text, useUnmergedTree = true).assertIsDisplayed()
                        ui.onAllNodesWithText("Node unavailable").assertCountEquals(0)
                        capture(context, "$mode-poll-recovered")
                        val record = JsonObject().apply {
                            addProperty("cachedReading", cached)
                            addProperty("withheldAt", withheldAt.get()); addProperty("failedAt", failedAt)
                            addProperty("recoveredAt", SystemClock.elapsedRealtime()); addProperty("archiveReads", reads.get())
                            addProperty("activityRecreated", false); addProperty("retryTapped", false)
                            addProperty("lifecycleRestarted", false); addProperty("publications", peer.transactions.size)
                        }
                        File(context.cacheDir, "read-deadline-evidence/$mode.json").writeText(record.toString())
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Read recovery must preserve the wallet" }
                        check(backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), previous) },
                { backends.save(oldBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun capture(context: android.content.Context, name: String) =
        captureNativeScreen(File(context.cacheDir, "read-deadline-evidence/$name.png"))
}

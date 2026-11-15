package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.AndroidProfileCache
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ProfileDeadlineRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun profileHistoryDeadlineKeepsCachedReadingAndExplicitRefreshRecovers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val backends = AndroidBackendStore(context); val oldBackend = backends.load()
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        val post = RedSquareFeed.readArchive("[$root]").posts.single()
        val retained = ChatFixtureState(context, listOf("feed.owner.", "profile.owner.", "profilemeta.owner.",
            "profiledetails.owner.", "personal.${wallet.publicKey}.", "authorposts.owner.${post.id}."), wallet.publicKey)
        val holding = AtomicBoolean(true)
        val historyReads = AtomicInteger()
        lateinit var peer: ControlledFeedPeer
        peer = ControlledFeedPeer("[$root]", profileArchiveResponse = { "[]" }, archiveResponse = { criteria ->
            val history = criteria.get("field1")?.asString == "RedSquare" && criteria.get("field2")?.asString == post.author
            peer.withholdArchiveReply = history && holding.get()
            if(history) historyReads.incrementAndGet()
            if(criteria.get("field1")?.asString == "RedSquare") "[$root]" else "[]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            AndroidProfileCache(context, SaitoBackend.OWNER).save(listOf(post), emptyList())
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({ capture(context, "profile-failure") }) {
                        ui.waitUntil(20_000) { ui.onAllNodesWithText(post.text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                        ui.clickVisiblePostProfile("Anon-${post.author.take(6)}")
                        ui.waitUntil(15_000) { ui.onNodeWithText("Profile").isDisplayed() }
                        ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText("Posts"))
                        ui.waitUntil(20_000) { historyReads.get() > 0 }
                        val error = context.getString(R.string.profile_error)
                        ui.waitUntil(20_000) {
                            runCatching {
                                ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(error))
                                ui.onNodeWithText(error).isDisplayed()
                            }.getOrDefault(false)
                        }
                        ui.onNodeWithText(error).assertIsDisplayed()
                        ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(post.text))
                        ui.onNodeWithText(post.text).assertIsDisplayed()
                        capture(context, "profile-deadline-cached-reading")
                        holding.set(false); peer.withholdArchiveReply = false
                        val before = historyReads.get()
                        ui.revealAppNavigation()
                        ui.refreshVisibleView()
                        ui.waitUntil(20_000) { historyReads.get() > before &&
                            ui.onAllNodesWithText(error).fetchSemanticsNodes().isEmpty() && ui.refreshableContentReady() }
                        ui.onNode(hasVerticalScrollAction()).performScrollToNode(hasText(post.text))
                        ui.onNodeWithText(post.text).assertIsDisplayed()
                        capture(context, "profile-refresh-recovered")
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "History retry must preserve the wallet" }
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
        captureNativeScreen(File(context.cacheDir, "profile-deadline-evidence/$name.png"))
}

package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real first-use layout against a read-only compatible peer, with independent restoration. */
class AppFramePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun firstUsePrioritizesVisiblePostsWithAnIdentifiableHomeDestination() = withFirstUseFeed("first-use") {
        ui.onNode(hasText("Home") and isSelected()).assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        ui.onNodeWithText("Import an existing wallet").assertIsDisplayed()
        val post = ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val viewport = ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("First-use reading must begin in the upper half of the viewport", post.top < viewport.height / 2)
        capture("first-use-content-first.png")
    }

    @Test fun homeReturnsFromADirectThreadWithoutDismissingTheBackupReminder() = withFirstUseFeed("home-return") {
        ui.onNodeWithText("Native Red Square reference post").performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
        ui.onNodeWithText("Back up your wallet").assertDoesNotExist()
        ui.onNode(hasText("Home") and isSelected()).performClick()
        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        ui.onNodeWithText("Back up your wallet").assertIsDisplayed()
        capture("home-return-preserved-reminder.png")
    }

    private fun withFirstUseFeed(name: String, block: () -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed."), wallet.publicKey)
        val reminder = context.getSharedPreferences("wallet-backup-reminders", 0)
        val root = JsonParser.parseReader(instrumentation.context.assets.open("native-feed-reference.json").reader())
            .asJsonObject.get("archiveRow")
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            "[$root]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            check(reminder.edit().putBoolean("hidden:${wallet.publicKey}", false).commit())
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("$name-failure.png") }) {
                        ui.waitUntil(15_000) {
                            ui.onAllNodesWithText("Native Red Square reference post").fetchSemanticsNodes().isNotEmpty()
                        }
                        block()
                        assertTrue("Navigation must retain the complete wallet and portable options",
                            wallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty()); peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { check(wallets.loadExisting().exportJson() == wallet.exportJson()) })
        }
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "app-frame-evidence/$name"))
}

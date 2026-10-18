package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalPolicy
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedReadinessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun fullyDrawnWaitsForVisiblePostsAndUnmutingMakesTheFeedInteractive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = JsonParser.parseReader(InstrumentationRegistry.getInstrumentation().context.assets
            .open("native-feed-reference.json").reader()).asJsonObject
        val author = reference.getAsJsonObject("archiveRow").get("field2").asString
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("feed.", "personal.${originalWallet.publicKey}."), originalWallet.publicKey)
        val peer = ControlledFeedPeer("[${reference.get("archiveRow")}]")
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            AndroidPersonalControls(context, originalWallet.publicKey).save(PersonalPolicy(), PersonalPolicy(muted = setOf(author)))
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use { activity ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "feed-readiness-evidence/failure.png")) }) {
                        ui.waitUntil(15_000) {
                            peer.requests.get() >= 2 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.waitForIdle()
                        peer.assertHealthy()
                        ui.onNodeWithText("Native Red Square reference post").assertDoesNotExist()
                        captureNativeScreen(File(context.cacheDir, "feed-readiness-evidence/muted.png"))
                        activity.onActivity { assertFalse("A feed containing no visible posts must not report fully drawn",
                            it.fullyDrawnReporter.isFullyDrawnReported) }

                        ui.onNodeWithText("Settings").performClick()
                        waitFor("Muted and blocked accounts")
                        ui.onNodeWithText("Muted and blocked accounts").performClick()
                        waitFor("Unmute account")
                        ui.onNodeWithText("Unmute account").performClick()
                        waitFor("No muted or blocked accounts.")
                        captureNativeScreen(File(context.cacheDir, "feed-readiness-evidence/ready-behind-dialog.png"), "No muted or blocked accounts.")
                        val quietUntil = SystemClock.elapsedRealtime() + 1_000
                        while (SystemClock.elapsedRealtime() < quietUntil) {
                            activity.onActivity { assertFalse("A dialog still prevents interaction with the loaded feed",
                                it.fullyDrawnReporter.isFullyDrawnReported) }
                            SystemClock.sleep(25)
                        }
                        ui.onNodeWithText("Done").performClick()
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText("Refresh").assertIsDisplayed().assertIsEnabled()
                        ui.onNode(hasScrollAction()).assertIsDisplayed().performScrollToIndex(0)
                        ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                        ui.waitUntil(5_000) {
                            var reported = false
                            activity.onActivity { reported = it.fullyDrawnReporter.isFullyDrawnReported }
                            reported
                        }
                        captureNativeScreen(File(context.cacheDir, "feed-readiness-evidence/interactive.png"), "Native Red Square reference post")
                        peer.assertHealthy()
                        assertEquals(originalWallet.exportJson(), wallets.loadExisting().exportJson())
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson()) })
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }
}

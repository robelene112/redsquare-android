package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real native navigation and Android intent interception; no email or public write. */
class PublisherRoutesPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unconfiguredPublisherSupportExplainsAvailabilityWithoutHandingOffOrLosingDrafts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "publishing-policy-fixture.json"), originalWallet.publicKey)
        val text = "Keep this unsent draft while checking publisher support 😀"
        var primary: Throwable? = null
        Intents.init()
        try {
            for (action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)) {
                Intents.intending(hasAction(action)).respondWith(
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            }
            retained.prepare()
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("unconfigured-support-first-failure.png") }) {
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).performTextReplacement(text)
                        ui.onNodeWithText("Save draft").performClick()
                        waitFor("New post", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        waitFor("Draft policies")
                        ui.waitUntil(20_000) {
                            ui.onAllNodes(hasText("Contact publisher") and hasClickAction())
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNodeWithText("Contact publisher").performScrollTo().performClick()
                        waitFor("Publisher support")
                        ui.onNodeWithText("Publisher support is not available in this preview. Contact details are still being prepared.")
                            .assertIsDisplayed()
                        ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                        ui.onAllNodesWithText("Compose email").assertCountEquals(0)
                        assertTrue("Unconfigured contact must not hand off to another app",
                            Intents.getIntents().none { it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW) })
                        capture("unconfigured-support-unavailable.png")
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithContentDescription("Close privacy and safety").performClick()
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).assertTextEquals(text)
                        assertTrue("Publisher navigation must preserve complete wallet options",
                            originalWallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                        capture("unconfigured-support-keeps-draft.png")
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun waitFor(text: String, action: Boolean = false) = ui.waitUntil(20_000) {
        ui.onNode(if (action) hasText(text) and hasClickAction() and isEnabled() else hasText(text)).isDisplayed()
    }

    private fun waitForEditor() = ui.waitUntil(20_000) {
        ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "publisher-routes-evidence/$name"))
}

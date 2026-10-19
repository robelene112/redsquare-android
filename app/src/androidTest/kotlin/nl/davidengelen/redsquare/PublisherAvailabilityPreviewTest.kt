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

/** Missing, unready and invalid public configuration observed through the actual native routes. */
class PublisherAvailabilityPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unavailablePublisherRoutesExplainAvailabilityBeforeCollectingTextOrLaunchingAnotherApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("publisher-routes-fixture.json",
            "publishing-policy-fixture.json"), originalWallet.publicKey)
        val configurations = listOf(
            "absent" to null,
            "unready" to """{"ready":false,"supportEmail":"support@publisher.example","safetyEmail":"safety@publisher.example","privacyEmail":"privacy@publisher.example","requestUrl":"https://publisher.example/requests"}""",
            "missing-destinations" to """{"ready":true}""",
            "invalid-destinations" to """{"ready":true,"supportEmail":"invalid address","safetyEmail":42,"privacyEmail":null,"requestUrl":"http://publisher.example/requests"}""",
            "invalid-readiness" to """{"ready":"true","supportEmail":"support@publisher.example","safetyEmail":"safety@publisher.example","privacyEmail":"privacy@publisher.example","requestUrl":"https://publisher.example/requests"}"""
        )
        val entries = listOf(
            "Contact publisher" to "Publisher support is not available in this preview. Contact details are still being prepared.",
            "Safety concerns" to "Publisher safety contact is not available in this preview. Contact details are still being prepared.",
            "Privacy inquiries" to "Publisher privacy contact is not available in this preview. Contact details are still being prepared.",
            "Data requests" to "Data requests are not available in this preview. The publisher request page and handling process are still being prepared."
        )
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
                for ((label, configuration) in configurations) {
                    val file = File(context.noBackupFilesDir, "publisher-routes-fixture.json")
                    if (configuration == null) check(!file.exists() || file.delete())
                    else file.writeText(configuration)
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                        withFailureCapture({ capture("$label-first-failure.png") }) {
                            waitFor("Settings", action = true)
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                            for ((entry, explanation) in entries) {
                                ui.waitUntil(20_000) {
                                    ui.onAllNodes(hasText(entry) and hasClickAction() and isEnabled())
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                ui.onNodeWithText(entry).performScrollTo().assertIsDisplayed().performClick()
                                waitFor(explanation)
                                ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                                ui.onAllNodesWithText("Compose email").assertCountEquals(0)
                                ui.onAllNodesWithText("Open request page").assertCountEquals(0)
                                assertTrue(Intents.getIntents().none {
                                    it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                                })
                                if (entry == "Data requests") capture("$label-request-unavailable.png")
                                ui.onNodeWithText("Done").performClick()
                            }
                            assertTrue(originalWallet.exportJson() == wallets.loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, backends.load())
                            assertTrue(peer.transactions.isEmpty())
                            assertTrue(peer.reports.isEmpty())
                            peer.assertHealthy()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { Intents.release() }, { retained.restoreFiles() },
                { wallets.replace(wallets.loadExisting(), originalWallet)
                    check(wallets.loadExisting().exportJson() == originalWallet.exportJson()) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun waitFor(text: String, action: Boolean = false) = ui.waitUntil(20_000) {
        ui.onNode(if (action) hasText(text) and hasClickAction() and isEnabled() else hasText(text)).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "publisher-availability-evidence/$name"))
}

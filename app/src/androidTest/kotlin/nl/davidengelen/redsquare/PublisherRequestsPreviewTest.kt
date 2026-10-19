package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
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
import java.util.concurrent.atomic.AtomicBoolean

/** An owned public HTTPS destination and controlled browser handoff, never a deletion request. */
class PublisherRequestsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun requestPagesRequireExplicitOpeningAndKeepTheWalletAndUnsentDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "publisher-routes-fixture.json",
            "publishing-policy-fixture.json"), originalWallet.publicKey)
        val unavailable = AtomicBoolean(false)
        val destination = "https://publisher.example/requests?source=android"
        val draft = "Keep this unsent draft while checking data requests 😀"
        var primary: Throwable? = null
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWithFunction {
                if (unavailable.get()) throw ActivityNotFoundException("Owned unavailable browser handler")
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
            Intents.intending(hasAction(Intent.ACTION_SENDTO)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            retained.prepare()
            File(context.noBackupFilesDir, "publisher-routes-fixture.json").writeText(
                """{"ready":true,"requestUrl":"https://publisher.example/requests?source=android"}""")
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            backends.save(SaitoBackend.OWNER)
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("request-page-first-failure.png") }) {
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).performTextReplacement(draft)
                        ui.onNodeWithText("Save draft").performClick()
                        waitFor("Settings", action = true)
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
                        fun openPage() {
                            ui.waitUntil(20_000) {
                                ui.onAllNodes(hasText("Data requests") and hasClickAction() and isEnabled())
                                    .fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.onNodeWithText("Data requests").performScrollTo().assertIsDisplayed().performClick()
                            waitFor("Publisher data requests")
                            ui.onNodeWithText("Publisher: Angels’ Foundry").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(destination).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Opening this page does not delete your wallet, local messages or server data. The publisher must verify and handle requests through its own process. Independent network or recipient copies may remain.")
                                .performScrollTo().assertIsDisplayed()
                            ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                        }
                        fun external() = Intents.getIntents().filter {
                            it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                        }
                        openPage()
                        assertTrue(external().isEmpty())
                        ui.onNodeWithText("Cancel").performClick()
                        assertTrue(external().isEmpty())
                        openPage()
                        ui.onNodeWithText("Open request page").performClick()
                        waitFor("Continue in your browser. Opening the page does not submit a request or delete data.")
                        val handoff = external().single()
                        assertEquals(Intent.ACTION_VIEW, handoff.action)
                        assertEquals(destination, handoff.dataString)
                        assertTrue(handoff.extras == null || handoff.extras!!.isEmpty)
                        assertNull(handoff.clipData)
                        assertEquals(0, handoff.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                        capture("request-page-explicit-open.png")
                        ui.onNodeWithText("Done").performClick()
                        openPage()
                        unavailable.set(true)
                        ui.onNodeWithText("Open request page").performClick()
                        waitFor("No browser could be opened. The request address is still available above.")
                        ui.onNodeWithText(destination).performScrollTo().assertIsDisplayed()
                        assertEquals(2, external().size)
                        capture("request-page-no-browser.png")
                        ui.onNodeWithText("Cancel").performClick()
                        ui.onNodeWithContentDescription("Close privacy and safety").performClick()
                        waitFor("New post", action = true)
                        ui.onNodeWithText("New post").performClick()
                        waitForEditor()
                        ui.onNode(hasSetTextAction()).assertTextEquals(draft)
                        assertTrue("Request navigation must preserve complete wallet options",
                            originalWallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                        peer.assertHealthy()
                        capture("request-page-keeps-unsent-draft.png")
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

    private fun waitForEditor() = ui.waitUntil(20_000) {
        ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed()
    }

    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "publisher-requests-evidence/$name"))
}

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
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Profile navigation, exact public identity and controlled Android email draft on both nodes. */
class PublisherUserReportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun userReportsKeepTheExactPublicAccountAndSelectedServerThroughConfirmation() = exercise(ready = true)
    @Test fun unconfiguredUserReportsKeepPublicContextWithoutCollectingTextOrUsingAnotherChannel() = exercise(ready = false)

    private fun exercise(ready: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Public unsafe fixture only.
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("publisher-routes-fixture.json",
            "publishing-policy-fixture.json", "feed.", "conversation.", "profile.",
            "profilemeta.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val root = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val author = root.get("field2").asString
        assertNotEquals(wallet.publicKey, author)
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            when (criteria.get("field1").asString) {
                "RedSquare" -> {
                    if (criteria.has("field2")) check(criteria.get("field2").asString == author)
                    "[$root]"
                }
                "RedSquareLike" -> "[]"
                else -> error("Unexpected owned profile query")
            }
        })
        var primary: Throwable? = null
        Intents.init()
        try {
            for (action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)) {
                Intents.intending(hasAction(action)).respondWith(
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            }
            retained.prepare()
            File(context.noBackupFilesDir, "publisher-routes-fixture.json").writeText(
                if (ready) """{"ready":true,"safetyEmail":"safety@publisher.example"}"""
                else """{"ready":true,"supportEmail":"support@publisher.example"}""")
            File(context.noBackupFilesDir, "publishing-policy-fixture.json")
                .writeText("""{"enforce":true,"ready":false}""")
            wallets.replace(originalWallet, wallet)
            peer.start().use { server ->
                val servers = listOf(SaitoBackend.OWNER to "https://saito.davidengelen.nl",
                    SaitoBackend.OFFICIAL to "https://saito.io")
                for ((backend, serverUrl) in servers) {
                    backends.save(backend)
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                        withFailureCapture({ capture("${backend.id}-${if (ready) "ready" else "unconfigured"}-report-first-failure.png") }) {
                            ui.waitUntil(20_000) {
                                ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes().isNotEmpty()
                            }
                            ui.clickVisiblePostProfile("Anon-${author.take(6)}")
                            waitFor("Posts", action = true)
                            fun shownContext() {
                                ui.waitUntil(20_000) {
                                    ui.onAllNodesWithText(if (ready) "Email: safety@publisher.example" else
                                        "User reporting to the publisher is not available in this preview. Contact details are still being prepared.")
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                for (text in listOfNotNull(if (ready) "Email: safety@publisher.example" else null,
                                    "Publisher: Angels’ Foundry", "Public account: $author", "Selected node: $serverUrl")) {
                                    ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                                }
                            }
                            fun openReport() {
                                ui.waitUntil(20_000) {
                                    ui.onAllNodes(hasText("Report user to publisher") and hasClickAction() and isEnabled())
                                        .fetchSemanticsNodes().isNotEmpty()
                                }
                                ui.onNode(hasScrollAction()).performScrollToNode(hasText("Report user to publisher"))
                                ui.onNodeWithText("Report user to publisher").assertIsDisplayed().performClick()
                                shownContext()
                                if (ready) {
                                    ui.onNode(hasSetTextAction()).performScrollTo()
                                    ui.waitUntil(20_000) { ui.onNode(hasSetTextAction() and isEnabled()).isDisplayed() }
                                }
                            }
                            fun external() = Intents.getIntents().filter {
                                it.action in listOf(Intent.ACTION_SENDTO, Intent.ACTION_VIEW)
                            }
                            val before = external().size
                            openReport()
                            if (!ready) {
                                ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                                ui.onAllNodesWithText("Compose email").assertCountEquals(0)
                                assertEquals(before, external().size)
                                capture("${backend.id}-unconfigured-report-context.png")
                                ui.onNodeWithText("Done").performClick()
                            } else {
                                ui.onNode(hasSetTextAction()).performTextReplacement("Cancel this user report")
                                ui.onNodeWithText("Cancel").performClick()
                                assertEquals(before, external().size)
                                openReport()
                                val description = "Please review this public account on $serverUrl"
                                ui.onNode(hasSetTextAction()).performTextReplacement(description)
                                activity.recreate()
                                shownContext()
                                ui.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
                                ui.onNode(hasSetTextAction()).assertTextContains(description)
                                ui.onNodeWithText("Compose email").performClick()
                                waitFor("Email draft requested. Review and send it in your email app. No message is sent automatically.")
                                assertEquals(before + 1, external().size)
                                assertPublisherEmailDraft(external().last(), "safety@publisher.example", "Red Square user report",
                                    "App: Red Square 0.1.0\nPublic account: $author\nSelected node: $serverUrl\n\nDescription:\n$description")
                                capture("${backend.id}-exact-report-context.png")
                                ui.onNodeWithText("Done").performClick()
                            }
                            assertTrue("Report preparation must preserve complete wallet options",
                                wallet.exportJson() == wallets.loadExisting().exportJson())
                            assertEquals(backend, backends.load())
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
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "publisher-user-report-evidence/$name"))
}

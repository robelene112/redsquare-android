package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

/** Controlled policy input through the real app, persistent state and owned peer. */
class PublishingAgreementPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Before fun prepareSyntheticDrafts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun explicitAgreementSurvivesActivityReopeningButStillRequiresDeliberateSend() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        fixture.writeText("""{"revision":"owned-accept-policy-one","ready":true,"enforce":true,
            "terms":"Controlled acceptance terms. No real publisher approval.",
            "community":"Controlled community rules. Keep the explicit send choice."}""")
        try {
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
            val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
                val row = indexed.get()
                if (row != null && (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString))
                    "[$row]" else "[]"
            }, onTransaction = { frame -> indexed.set(postingArchiveRow(postingTransaction(frame))) })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture("accept-first-failure.png") }) {
                        waitFor("New post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        ui.onNodeWithText("New post").performClick()
                        ui.onNode(hasSetTextAction()).performTextReplacement("Send only after my deliberate agreement")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.onNodeWithText("Send").performClick()
                        waitFor("Review publishing terms")
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Accept").performClick()
                        waitFor("Agreement saved. Tap Send again when you are ready.")
                        assertTrue("Agreement must not send the draft", peer.transactions.isEmpty())
                        capture("accepted-without-publication.png")
                        scenario.recreate()
                        ui.waitUntil(20_000) {
                            ui.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
                        }
                        ui.onNode(hasSetTextAction()).assertTextEquals("Send only after my deliberate agreement")
                        assertTrue("Reopening must not publish", peer.transactions.isEmpty())
                        ui.onNodeWithText("Send").performClick()
                        waitFor("Available on this server.")
                        assertEquals(1, peer.transactions.size)
                        val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(
                            postingTransaction(peer.transactions.single()).envelope())
                        assertEquals("Send only after my deliberate agreement", post.text)
                        assertTrue("Agreement preserves the complete wallet",
                            originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        capture("accepted-reopened-deliberately-published.png")
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    @Test fun requiredTermsCanBeReadAndCancelledWithoutPublishingOrLosingThePost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previous = fixture.takeIf { it.exists() }?.readBytes()
        fixture.writeText("""{"revision":"owned-cancel-policy-one","ready":true,"enforce":true,
            "terms":"Controlled terms for native verification only. No real publisher approval.",
            "community":"Controlled community policy for native verification only. Preserve your unsent work."}""")
        try {
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
            }
            val peer = ControlledFeedPeer("[$root]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("cancel-first-failure.png") }) {
                        waitFor("New post")
                        val originalWallet = AndroidWalletStore(context).loadExisting().exportJson()
                        ui.onNodeWithText("New post").performClick()
                        ui.onNode(hasSetTextAction()).performTextReplacement("Keep my unsent publishing agreement draft")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        repeat(2) { attempt ->
                            ui.onNodeWithText("Send").performClick()
                            waitFor("Review publishing terms")
                            ui.onNodeWithText("Controlled terms for native verification only.", substring = true)
                                .performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Controlled community policy for native verification only.", substring = true)
                                .performScrollTo().assertIsDisplayed()
                            assertTrue("No publication before deliberate agreement", peer.transactions.isEmpty())
                            capture("cancel-$attempt-review.png")
                            ui.onNodeWithText("Cancel").performClick()
                            ui.onNode(hasSetTextAction()).assertTextEquals("Keep my unsent publishing agreement draft")
                            assertTrue("Cancelling must not publish", peer.transactions.isEmpty())
                            assertTrue("Cancelling must preserve the complete wallet",
                                originalWallet == AndroidWalletStore(context).loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        }
                        capture("cancel-keeps-draft.png")
                        ui.onNode(hasSetTextAction()).performTextReplacement("")
                        ui.onNodeWithText("Save draft").performClick()
                        peer.assertHealthy()
                    }
                }
            }
        } finally {
            if (previous == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previous)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "agreement-evidence/$name"))
}

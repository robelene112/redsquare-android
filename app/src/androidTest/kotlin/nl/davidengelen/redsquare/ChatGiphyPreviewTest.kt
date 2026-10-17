package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Native Chat and owned provider/Saito endpoints only; no live provider credentials. */
class ChatGiphyPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun privateGifFailureCanRetryAndCancelWithoutLosingTheDraft() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-private-chat-image-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val contact = reference.get("sender").asString
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact) // Public unsafe fixture.
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.") } +
            "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == reference.get("groupId").asString) reads.incrementAndGet()
            "[]"
        })
        val draft = "Keep my Chat draft 😀 <&> \"quoted\"\nA second line"
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            MockWebServer().use { provider ->
                repeat(2) { provider.enqueue(MockResponse.Builder().code(503).build()) }
                provider.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { server ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("giphyChatFixtureUrl", "http://127.0.0.1:${provider.port}")).use { scenario ->
                        withFailureCapture({ capture("private-failure") }) {
                            fun open() {
                                val chat = hasText("Chat") and hasClickAction() and isEnabled()
                                ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                                if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                                val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact))
                                ui.waitUntil(20_000) { displayed(row, unmerged = true) }
                                val before = reads.get()
                                ui.onNode(row, useUnmergedTree = true).performClick()
                                ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > before }
                            }
                            fun assertDraft() = ui.onNode(hasSetTextAction() and isEnabled()).assert(
                                SemanticsMatcher("Exact authored Chat text") {
                                    it.config.getOrNull(SemanticsProperties.EditableText)?.text == draft
                                })
                            open()
                            ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                            androidx.test.espresso.Espresso.closeSoftKeyboard()
                            assertEquals(0, provider.requestCount)
                            val add = hasText("Add GIF") and hasClickAction() and isEnabled()
                            ui.waitUntil(10_000) { displayed(add) }
                            ui.onNode(add).performClick()
                            val error = hasText("GIF search is unavailable. Try again.")
                            ui.waitUntil(10_000) { provider.requestCount == 1 && displayed(error) }
                            ui.onNodeWithText("Powered by GIPHY").assertIsDisplayed()
                            capture("private-provider-failure")
                            ui.onNodeWithText("Retry").performClick()
                            ui.waitUntil(10_000) { provider.requestCount == 2 && displayed(error) }
                            repeat(2) {
                                val request = requireNotNull(provider.takeRequest(2, TimeUnit.SECONDS))
                                assertEquals("GET", request.method)
                                assertEquals("/v1/gifs/trending", request.url.encodedPath)
                                assertEquals("owned-test-only", request.url.queryParameter("api_key"))
                            }
                            ui.onNodeWithText("Close GIF search").performClick()
                            assertDraft()
                            ui.onNode(hasText("Back") and hasClickAction()).performClick()
                            scenario.recreate(); open(); assertDraft()
                            assertEquals(2, provider.requestCount)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                            check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                            peer.assertHealthy(); capture("private-draft-restored")
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun displayed(target: SemanticsMatcher, unmerged: Boolean = false) =
        try { ui.onNode(target, useUnmergedTree = unmerged).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-giphy-evidence/$name.png"))
}

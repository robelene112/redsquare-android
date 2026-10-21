package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Conversation reading through the real UI and protected cache, without a scroll-to operator. */
class ConversationViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun openingACachedPrivateConversationExposesItsLatestMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val bodies = (0..69).map { "Cached private message ${it.toString().padStart(2, '0')}. Keep my latest message visible." }
        val originals = bodies.mapIndexed { index, body ->
            PrivateChat.create(contact, wallet.publicKey, body, 1700000123400L + index).envelope()
        }
        val folder = File(context.cacheDir, "conversation-viewport-evidence").apply { mkdirs() }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            val cache = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
            check(cache.save(originals).entries.map { it.message.text } == bodies)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "conversation-private-latest", scales = listOf(1.3f)) { label, _ ->
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
                        ui.onNode(chat).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact.publicKey))
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        ui.waitUntil(15_000) {
                            ui.onNodeWithText(bodies.first()).isDisplayed() || ui.onNodeWithText(bodies.last()).isDisplayed()
                        }
                        ui.onNodeWithContentDescription("Private messages").assertIsDisplayed()
                        captureNativeScreen(File(folder, "$label-opened.png"))
                        // Readiness above allows either current layout. This acceptance assertion
                        // independently requires the newest original, without moving the viewport.
                        ui.onNodeWithText(bodies.last()).assertIsDisplayed()
                        check(cache.load().entries.map { it.envelope } == originals)
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Groups").isDisplayed() }
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                    }
                }
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), originalWallet) },
                { backends.save(originalBackend) }, { retained.restorePreferences() },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) })
        }
    }
}

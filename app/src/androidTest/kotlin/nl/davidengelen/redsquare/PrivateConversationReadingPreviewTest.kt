package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import com.google.gson.JsonObject
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Live reading through authenticated sockets and actual touch/accessibility scroll actions. */
class PrivateConversationReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun newPrivateMessagesFollowAtTheEndAndPreserveAnOlderReader() {
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
        val folder = File(context.cacheDir, "private-conversation-reading-evidence").apply { mkdirs() }
        val expectedBodies = bodies.toMutableList()
        val expectedEnvelopes = originals.toMutableList()
        val historyReads = java.util.concurrent.atomic.AtomicInteger()
        val conversation = PrivateChat.conversationId(wallet.publicKey, contact.publicKey)
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == conversation) historyReads.incrementAndGet()
            "[]"
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            // Synthetic reading fixture: silence only its alerts before channel creation.
            // Incoming receipt, ACK, protected history and rendering remain unchanged.
            val controls = nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey)
            val policy = controls.load()
            val quiet = policy.copy(mutedConversations = policy.mutedConversations + conversation)
            check(controls.save(policy, quiet) == quiet && controls.load() == quiet)
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            val cache = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
            check(cache.save(originals).entries.map { it.message.text } == bodies)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "private-conversation-reading", scales = listOf(1.3f)) { label, _ ->
                        val previousReads = historyReads.get()
                        val newestBeforeOpening = expectedBodies.last()
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
                        ui.onNode(chat).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(contact.publicKey))
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        ui.waitUntil(15_000) {
                            ui.onNodeWithText(bodies.first()).isDisplayed() || ui.onNodeWithText(newestBeforeOpening).isDisplayed()
                        }
                        val reader = NativeConversationReader(ui, folder, label, "Private messages") { expectedBodies.size }
                        reader.assertOpensAtNewest(newestBeforeOpening)
                        ui.waitUntil(15_000) { historyReads.get() > previousReads }
                        fun deliver(body: String, timestamp: Long, requestId: Int) {
                            val transaction = PrivateChat.create(contact, wallet.publicKey, body, timestamp)
                            val relay = JsonObject().apply {
                                addProperty("module", "Relay"); addProperty("request", "chat relay")
                                add("data", transaction.signedJson())
                            }
                            val frame = SaitoWire.applicationRequest(contact.publicKey,
                                relay.toString().toByteArray(Charsets.UTF_8), 1, timestamp)
                            peer.sendIncoming(frame.copyOfRange(5, frame.size), requestId)
                            ui.waitUntil(15_000) {
                                peer.incomingAcknowledgements.any { it.size >= 5 && it[0] == 13.toByte() &&
                                    java.nio.ByteBuffer.wrap(it, 1, 4).int == requestId }
                            }
                            ui.waitUntil(15_000) { cache.containsOriginal(transaction.id) }
                            expectedBodies += body
                            expectedEnvelopes += transaction.envelope()
                        }
                        val offset = if (label.endsWith("light")) 0 else 100
                        val followed = "Incoming private message at the end ($label)."
                        reader.followIncoming(followed) { deliver(followed, 1700000223400L + offset, 801 + offset) }
                        val waiting = "Incoming private message while reading older history ($label)."
                        reader.receiveWhileReadingOlder(bodies[20], waiting) {
                            deliver(waiting, 1700000223401L + offset, 802 + offset)
                        }
                        check(cache.load().entries.map { it.envelope } == expectedEnvelopes)
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

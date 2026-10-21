package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Live reading through authenticated sockets and actual touch/accessibility scroll actions. */
class GroupConversationReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun cachedGroupMessagesOpenAtTheEndAndPreserveLaterReading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val groupName = "Group reading fixture"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withNamedChatGroup(groupId, groupName)
        val creation = GroupChat.create(contact, groupId, groupName, listOf(wallet.publicKey), 1700000123300L)
        val group = GroupChat.readCreation(creation.envelope())
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val bodies = (0..69).map { "Cached group message ${it.toString().padStart(2, '0')}. Keep my latest message visible." }
        val originals = bodies.mapIndexed { index, body ->
            GroupChat.message(contact, group, body, 1700000123400L + index).envelope()
        }
        val folder = File(context.cacheDir, "group-conversation-reading-evidence").apply { mkdirs() }
        val expectedBodies = bodies.toMutableList()
        val expectedEnvelopes = originals.toMutableList()
        val historyReads = java.util.concurrent.atomic.AtomicInteger()
        val conversation = groupId
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
            val cache = GroupChatStore(context, wallet, SaitoBackend.OWNER)
            cache.receive(creation.envelope())
            originals.chunked(50).forEach { check(!cache.receiveAvailable(groupId, it)) }
            check(requireNotNull(cache.load(groupId)).messages.map { it.text } == bodies)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "group-conversation-reading", scales = listOf(1.3f)) { label, _ ->
                        val newestBeforeOpening = expectedBodies.last()
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
                        ui.onNode(chat).performClick()
                        val groups = hasText("Groups") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(groups).isDisplayed() }
                        ui.onNode(groups).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(groupName))
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        val previousReads = historyReads.get()
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        ui.waitUntil(15_000) {
                            ui.onNodeWithText(bodies.first()).isDisplayed() || ui.onNodeWithText(newestBeforeOpening).isDisplayed()
                        }
                        val reader = NativeConversationReader(ui, folder, label, "Group messages") { expectedBodies.size }
                        reader.assertOpensAtNewest(newestBeforeOpening)
                        ui.waitUntil(15_000) { historyReads.get() > previousReads }
                        fun deliver(body: String, timestamp: Long, requestId: Int) {
                            val transaction = GroupChat.message(contact, group, body, timestamp)
                            val bytes = transaction.applicationRequest(1).drop(5).toByteArray()
                            var sentAt = 0L
                            ui.waitUntil(15_000) {
                                val acknowledged = peer.incomingAcknowledgements.any { it.size >= 5 && it[0] == 13.toByte() &&
                                    java.nio.ByteBuffer.wrap(it, 1, 4).int == requestId }
                                // The passive receiver has its own authenticated socket. Repeat
                                // this exact original while it connects, never bypassing its ACK.
                                if (!acknowledged && System.nanoTime() - sentAt >= 500_000_000L) {
                                    peer.sendIncoming(bytes, requestId)
                                    sentAt = System.nanoTime()
                                }
                                acknowledged
                            }
                            ui.waitUntil(15_000) { cache.retainedOriginal(groupId, transaction.id) == transaction.envelope() }
                            expectedBodies += body
                            expectedEnvelopes += transaction.envelope()
                        }
                        val offset = if (label.endsWith("light")) 0 else 100
                        val followed = "Incoming group message at the end ($label)."
                        reader.followIncoming(followed) { deliver(followed, 1700000223400L + offset, 1001 + offset) }
                        val waiting = "Incoming group message while reading older history ($label)."
                        reader.receiveWhileReadingOlder(bodies[20], waiting) {
                            deliver(waiting, 1700000223401L + offset, 1002 + offset)
                        }
                        val retainedGroup = requireNotNull(cache.load(groupId))
                        check(retainedGroup.originals == expectedEnvelopes && retainedGroup.group.creationEnvelope == creation.envelope())
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        val back = hasText("Back") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(back).isDisplayed() }
                        ui.onNode(back).performClick()
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        ui.waitUntil(15_000) { ui.onNode(back).isDisplayed() }
                        ui.onNode(back).performClick()
                        ui.waitUntil(15_000) { ui.onNode(groups).isDisplayed() }
                        ui.waitUntil(15_000) { ui.onNode(back).isDisplayed() }
                        ui.onNode(back).performClick()
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

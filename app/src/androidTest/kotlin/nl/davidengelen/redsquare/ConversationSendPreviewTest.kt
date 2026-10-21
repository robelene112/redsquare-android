package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import nl.davidengelen.redsquare.chat.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** An explicit own send exposes its exact original even while reading older cached history. */
class ConversationSendPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun sendingInPrivateChatExposesTheOutgoingMessageFromOlderHistory() = exercise(false)
    @Test fun sendingInAGroupExposesTheOutgoingMessageFromOlderHistory() = exercise(true)

    private fun exercise(group: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val groupName = "Own send reading fixture"
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val wallet = if (group) identity.withNamedChatGroup(groupId, groupName) else identity.withChatContact(contact.publicKey)
        val creation = GroupChat.create(contact, groupId, groupName, listOf(wallet.publicKey), 1700000123300L)
        val signedGroup = GroupChat.readCreation(creation.envelope())
        val conversation = if (group) groupId else PrivateChat.conversationId(wallet.publicKey, contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val originalWallet = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf(
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val bodies = (0..69).map { "Earlier message ${it.toString().padStart(2, '0')} before my explicit send." }
        val originals = bodies.mapIndexed { index, body ->
            if (group) GroupChat.message(contact, signedGroup, body, 1700000123400L + index).envelope()
            else PrivateChat.create(contact, wallet.publicKey, body, 1700000123400L + index).envelope()
        }
        val expected = originals.toMutableList()
        var newest = bodies.last()
        val candidates = mutableSetOf<String>()
        val groups = GroupChatStore(context, wallet, SaitoBackend.OWNER)
        val history = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
        val privateWork = ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact.publicKey)
        val folder = File(context.cacheDir, "conversation-send-evidence").apply { mkdirs() }
        val kind = if (group) "group" else "private"
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            retained.prepare()
            val controls = nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey)
            val policy = controls.load()
            val quiet = policy.copy(mutedConversations = policy.mutedConversations + conversation)
            check(controls.save(policy, quiet) == quiet)
            wallets.replace(originalWallet, wallet)
            backends.save(SaitoBackend.OWNER)
            if (group) {
                groups.receive(creation.envelope())
                originals.chunked(50).forEach { check(!groups.receiveAvailable(groupId, it)) }
            } else check(history.save(originals).entries.map { it.message.text } == bodies)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                    ui.withPostViewports(activity, folder, "conversation-$kind-send", scales = listOf(1.3f)) { label, _ ->
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
                        ui.onNode(chat).performClick()
                        if (group) ui.onNodeWithText("Groups").performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(if (group) groupName else contact.publicKey))
                        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        val reader = NativeConversationReader(ui, folder, label, if (group) "Group messages" else "Private messages") { expected.size }
                        val editor = hasText("Message") and isEnabled()
                        ui.waitUntil(15_000) {
                            ui.onNodeWithText(newest).isDisplayed() && ui.onNode(editor).isDisplayed()
                        }
                        val body = "My deliberate message ($label)."
                        reader.sendWhileReadingOlder(bodies[20], body) {
                            ui.onNodeWithText("Message").performTextInput(body)
                            androidx.test.espresso.Espresso.closeSoftKeyboard()
                            val send = hasText("Send") and hasClickAction() and isEnabled()
                            ui.waitUntil(15_000) { ui.onNode(send).isDisplayed() }
                            ui.onNode(send).performClick()
                            val count = expected.size - 70 + 1
                            ui.waitUntil(15_000) { peer.transactions.size == count && peer.relayedMessages.size == count }
                            val original = postingTransaction(peer.transactions.last())
                            candidates += original.id
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.last()) == original.envelope())
                            val actual = if (group) GroupChat.readMessage(signedGroup, original.envelope()).text
                                else PrivateChat.read(wallet, original.envelope(), contact.publicKey).text
                            check(actual == body && SaitoEnvelope.decodePayload(original.envelope()).author == wallet.publicKey)
                            expected += original.envelope()
                            newest = body
                        }
                        val saved = if (group) requireNotNull(groups.load(groupId)).originals else history.load().entries.map { it.envelope }
                        check(saved == expected && peer.reports.isEmpty())
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        repeat(if (group) 3 else 2) {
                            val back = hasText("Back") and hasClickAction() and isEnabled()
                            ui.waitUntil(15_000) { ui.onNode(back).isDisplayed() }
                            ui.onNode(back).performClick()
                        }
                        ui.waitUntil(15_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                    }
                }
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                // Stop only jobs belonging to this declared synthetic wallet before restoring its files.
                cleanupAfterTest(null,
                    { candidates += ChatQueueScheduling.pendingIds(context, wallet, SaitoBackend.OWNER) },
                    { if (group) groups.load(groupId)?.pendingMessage?.id?.let(candidates::add)
                        else privateWork.load().transaction?.id?.let(candidates::add) },
                    {
                        val manager = WorkManager.getInstance(context)
                        val cleanup = candidates.flatMap { id ->
                            val name = "chat:${SaitoBackend.OWNER.id}:${wallet.publicKey}:$id"
                            listOf<() -> Unit>(
                                { manager.cancelUniqueWork(name).result.get(5, TimeUnit.SECONDS) },
                                { check(manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).all { it.state.isFinished }) })
                        }
                        cleanupAfterTest(null, *cleanup.toTypedArray())
                    })
            }, { retained.restoreFiles() }, { wallets.replace(wallets.loadOrCreate(), originalWallet) },
                { backends.save(originalBackend) }, { retained.restorePreferences() },
                { check(wallets.loadExisting().exportJson() == originalWallet.exportJson() && backends.load() == originalBackend) })
        }
    }
}

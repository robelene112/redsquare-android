package nl.davidengelen.redsquare

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Signed controlled receipt and actual Android shade taps, without external push transport. */
class ChatAlertPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun aPrivateAlertOpensItsConversationAndPreservesTheLatestOtherConversationDraft() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        // Published unsafe fixture identities only.
        val editingContact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
            .withChatContact(editingContact).withChatContact(sender.publicKey)
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val originalJson = original.exportJson()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, SaitoBackend.entries.flatMap { backend ->
            listOf("chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
                "chatreactions.${backend.id}.${wallet.publicKey}.")
        } + "personal.${wallet.publicKey}.", wallet.publicKey)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val existingAlerts = notifications.activeNotifications.map { it.tag to it.id }.toSet()
        val device = UiDevice.getInstance(instrumentation)
        val historyReads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == PrivateChat.conversationId(wallet.publicKey, editingContact)) {
                historyReads.incrementAndGet()
            }
            "[]"
        })
        val latestDraft = "Keep my latest unsent draft 🌱\nA different conversation"
        val incomingText = "Open this exact private conversation"
        val incoming = PrivateChat.create(sender, wallet.publicKey, incomingText, 1700000140000)
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet)
            backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { _ ->
                    withFailureCapture({ capture("private-warm-tap-failure") }) {
                        click(hasText("Chat"))
                        click(hasText(editingContact))
                        ui.waitUntil(20_000) { historyReads.get() > 0 && displayed(hasSetTextAction() and isEnabled()) }
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(latestDraft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        peer.sendIncoming(incoming.applicationRequest(1).drop(5).toByteArray(), requestId = 801)
                        ui.waitUntil(20_000) {
                            peer.incomingAcknowledgements.any {
                                it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 801
                            } && notifications.activeNotifications.any { (it.tag to it.id) !in existingAlerts }
                        }
                        val alert = notifications.activeNotifications.single { (it.tag to it.id) !in existingAlerts }
                        assertEquals("private-chats", alert.notification.channelId)
                        assertEquals("New message", alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                        assertTrue(device.openNotification())
                        val visible = requireNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                        capture("private-warm-alert")
                        visible.click()
                        ui.waitUntil(20_000) { displayed(hasText(incomingText)) && displayed(hasText(sender.publicKey)) }
                        ui.onNodeWithText("Private messages are encrypted.").assertIsDisplayed()
                        capture("private-warm-opened")
                        click(hasText("Back"))
                        click(hasText(editingContact))
                        val restoredDraft = hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(latestDraft))
                        ui.waitUntil(20_000) { displayed(restoredDraft) }
                        ui.onNode(restoredDraft).assertIsDisplayed()
                        capture("private-warm-draft-restored")
                        assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { device.pressBack(); finishFixtureActivities() },
                { notifications.activeNotifications.filter { (it.tag to it.id) !in existingAlerts }
                    .forEach { notifications.cancel(it.tag, it.id) } },
                { retained.restoreFiles() },
                { retained.restorePreferences() },
                { wallets.replace(wallets.loadExisting(), original); check(wallets.loadExisting().exportJson() == originalJson) },
                { backends.save(originalBackend); check(backends.load() == originalBackend) })
        }
    }

    private fun finishFixtureActivities() {
        instrumentation.runOnMainSync {
            Stage.values().filter { it != Stage.DESTROYED }.flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)
            }.filterIsInstance<MainActivity>().distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun click(target: SemanticsMatcher) {
        val action = target and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { displayed(action) }
        ui.onNode(action).performClick()
    }

    private fun displayed(target: SemanticsMatcher) = try {
        ui.onNode(target).assertIsDisplayed(); true
    } catch (_: AssertionError) { false }

    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/$name.png"))
}

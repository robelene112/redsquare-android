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
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.chat.receiveChat
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

    @Test fun aPrivateAlertOpensItsConversationAndPreservesTheLatestOtherConversationDraft() = chatAlertJourney()

    @Test fun privatePreviewsRequireOptInPersistAndReturnToGenericWhenDisabled() = chatAlertJourney(previews = true)

    @Test fun aGroupAlertOpensItsGroupAndPreservesTheLatestPrivateDraft() = chatAlertJourney(groupAlert = true)

    @Test fun aColdPrivateAlertOnOfficialKeepsItsDestinationAcrossRecreation() = chatAlertJourney(cold = true)

    @Test fun aColdGroupAlertUsesCachedHistoryDuringAnOutageAndRechecksMembershipOnOfficial() =
        chatAlertJourney(groupAlert = true, cold = true)

    private fun chatAlertJourney(previews: Boolean = false, groupAlert: Boolean = false, cold: Boolean = false) {
        val backend = if (cold) SaitoBackend.OFFICIAL else SaitoBackend.OWNER
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        // Published unsafe fixture identities only.
        val editingContact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val groupName = "Exact notification group"
        val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "9").publicKey
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
            .withChatContact(editingContact).withChatContact(sender.publicKey).let {
                if (groupAlert) it.withNamedChatGroup(groupKey, groupName) else it
            }
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val originalJson = original.exportJson()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val retained = ChatFixtureState(context, SaitoBackend.entries.flatMap { backend ->
            listOf("chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
                "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
        } + listOf("personal.${wallet.publicKey}.", "chat-alerts.${wallet.publicKey}."), wallet.publicKey)
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
        val groupCreation = GroupChat.create(sender, groupKey,
            groupName, listOf(wallet.publicKey), 1700000139900)
        val incomingText = if (groupAlert) "Open this exact group conversation" else "Open this exact private conversation"
        val incoming = if (groupAlert) GroupChat.message(sender, GroupChat.readCreation(groupCreation.envelope()),
            incomingText, 1700000140000) else PrivateChat.create(sender, wallet.publicKey, incomingText, 1700000140000)
        val targetIdentity = if (groupAlert) groupName else sender.publicKey
        val capturePrefix = (if (groupAlert) "group" else "private") + if (cold) "-cold-official" else "-warm"
        var primary: Throwable? = null
        var activitiesStopped = false
        fun requireStopped() { check(activitiesStopped && activeMainActivities().isEmpty()) }
        try {
            retained.prepare()
            wallets.replace(original, wallet)
            backends.save(backend)
            if (groupAlert) GroupChatStore(context, wallet, backend).receive(groupCreation.envelope())
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { scenario ->
                    fun observeDestination(name: String) {
                        val readyBy = android.os.SystemClock.elapsedRealtime() + 20_000
                        ui.waitUntil(20_000) { displayed(hasText(incomingText)) }
                        ui.withConversationDetails {
                            ui.onNode(hasText(targetIdentity) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(if (groupAlert) "Group messages are unencrypted." else "Private messages are encrypted.")
                                .performScrollTo().assertIsDisplayed()
                            check(android.os.SystemClock.elapsedRealtime() <= readyBy) {
                                "Exact route identity and privacy must be visible within 20 seconds"
                            }
                            capture("$name-details")
                        }
                        ui.onNodeWithText(incomingText).assertIsDisplayed()
                        capture(name)
                    }
                    withFailureCapture({
                        try {
                            capture(if (previews) "private-previews-failure" else "$capturePrefix-tap-failure")
                            if (cold && displayed(hasText(context.getString(R.string.social_push_open_failed)))) {
                                // Diagnose Retry without changing the original first-open failure outcome.
                                click(hasText("Retry"))
                                observeDestination("$capturePrefix-diagnostic-retry")
                                instrumentation.sendStatus(2, android.os.Bundle().apply {
                                    putString("stream", "Diagnostic Retry reached the target; original first-open failure retained.")
                                })
                            }
                        }
                        finally { if (cold) instrumentation.sendStatus(2, android.os.Bundle().apply {
                            putString("stream", "Chat route diagnosis:\n" +
                                device.executeShellCommand("logcat -d -v brief -s ChatAlertRoute:D").takeLast(8000))
                        }) }
                    }) {
                        click(hasText("Chat"))
                        click(hasText(editingContact))
                        ui.waitUntil(20_000) { historyReads.get() > 0 && displayed(hasSetTextAction() and isEnabled()) }
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(latestDraft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        val bytes = incoming.applicationRequest(1).drop(5).toByteArray()
                        if (cold) {
                            click(hasText("Back")) // Save the mounted editor before closing its Activity.
                            scenario.close()
                            ui.waitUntil(20_000) { activeMainActivities().isEmpty() }
                            receiveChat(context, wallet, backend, bytes, endpoint)
                            assertTrue(activeMainActivities().isEmpty())
                            assertEquals(wallet.publicKey, wallets.loadExisting().publicKey)
                            assertEquals(backend, backends.load())
                            val savedMessage = if (groupAlert) {
                                requireNotNull(GroupChatStore(context, wallet, backend).load(groupKey)).messages
                                    .single { it.id == incoming.id }.let { it.author to it.text }
                            } else nl.davidengelen.redsquare.chat.ChatHistoryStore(context, wallet, backend,
                                sender.publicKey).load().entries.single { it.message.id == incoming.id }
                                .message.let { it.author to it.text }
                            assertEquals(sender.publicKey to incomingText, savedMessage)
                            val policy = nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey).load()
                            assertFalse(policy.hidesSocial(sender.publicKey))
                            assertFalse((if (groupAlert) groupKey else PrivateChat.conversationId(wallet.publicKey,
                                sender.publicKey)) in policy.mutedConversations)
                            if (groupAlert) peer.rejectNewConnections = true // The selected backend becomes unavailable.
                        } else peer.sendIncoming(bytes, requestId = 801)
                        ui.waitUntil(20_000) {
                            (cold || peer.incomingAcknowledgements.any {
                                it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 801
                            }) && notifications.activeNotifications.any { (it.tag to it.id) !in existingAlerts }
                        }
                        val alert = notifications.activeNotifications.single { (it.tag to it.id) !in existingAlerts }
                        assertEquals(if (groupAlert) "group-chats" else "private-chats", alert.notification.channelId)
                        assertEquals("New message", alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                        assertTrue(device.openNotification())
                        val visible = requireNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                        capture(if (previews) "private-previews-default-alert" else "$capturePrefix-alert")
                        if (previews) {
                            device.pressBack()
                            // Keep later preview rows individually visible after observing the generic alert.
                            notifications.cancel(alert.tag, alert.id)
                            previewChoice(scenario, peer, sender, wallet, notifications, existingAlerts)
                        } else {
                            visible.click()
                            observeDestination("$capturePrefix-opened")
                            if (cold) {
                                recreateCurrentActivity()
                                observeDestination("$capturePrefix-recreated")
                            }
                            val savedDraft = ChatWorkStore(context, wallet, backend, editingContact).load()
                            assertEquals(latestDraft, savedDraft.draft)
                            assertNull(savedDraft.transaction)
                            assertEquals(ChatWorkStore.Status.NONE, savedDraft.status)
                            assertFalse(savedDraft.automatic)
                            click(hasText("Back"))
                            if (groupAlert) {
                                ui.waitUntil(20_000) { displayed(hasText("Create group") and hasClickAction()) }
                                click(hasText("Back"))
                            }
                            click(hasText(editingContact))
                            val restoredDraft = hasSetTextAction() and isEnabled() and
                                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(latestDraft))
                            ui.waitUntil(20_000) { displayed(restoredDraft) }
                            ui.onNode(restoredDraft).assertIsDisplayed()
                            capture("$capturePrefix-draft-restored")
                            if (cold && groupAlert) {
                                val group = GroupChat.readCreation(groupCreation.envelope())
                                val staleText = "Available group alert before membership removal"
                                val stale = GroupChat.message(sender, group, staleText, 1700000140400)
                                receiveChat(context, wallet, backend, stale.applicationRequest(1).drop(5).toByteArray(), endpoint)
                                ui.waitUntil(20_000) { notifications.activeNotifications.any { it.tag?.endsWith(":" + stale.id) == true } }
                                val staleAlert = notifications.activeNotifications.single { it.tag?.endsWith(":" + stale.id) == true }
                                assertEquals("group-chats", staleAlert.notification.channelId)
                                assertEquals("New message", staleAlert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                                assertTrue(device.openNotification())
                                val staleRow = requireNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                                capture("group-cold-official-before-removal")
                                val removal = GroupChat.removeMember(sender, group, wallet.publicKey, 1700000140500)
                                receiveChat(context, wallet, backend, removal.applicationRequest(1).drop(5).toByteArray(), endpoint)
                                val removed = requireNotNull(GroupChatStore(context, wallet, backend).load(groupKey))
                                assertEquals(ChatGroupRole.REMOVED, removed.group.memberRoles[wallet.publicKey])
                                assertTrue(removed.messages.any { it.id == stale.id })
                                staleRow.click()
                                val openFailed = context.getString(R.string.social_push_open_failed)
                                ui.waitUntil(20_000) { displayed(hasText(openFailed)) }
                                ui.onAllNodesWithText(staleText).assertCountEquals(0)
                                ui.onNode(restoredDraft).assertExists()
                                val retainedDraft = ChatWorkStore(context, wallet, backend, editingContact).load()
                                assertEquals(latestDraft, retainedDraft.draft)
                                assertNull(retainedDraft.transaction)
                                assertEquals(ChatWorkStore.Status.NONE, retainedDraft.status)
                                assertFalse(retainedDraft.automatic)
                                capture("group-cold-official-membership-refused")
                                click(hasText("Dismiss notification link"))
                                ui.waitUntil(20_000) { ui.onAllNodesWithText(openFailed).fetchSemanticsNodes().isEmpty() }
                                ui.onNode(restoredDraft).assertIsDisplayed()
                                capture("group-cold-official-work-retained")
                            }
                            if (cold) {
                                click(hasText("Back"))
                                recreateCurrentActivity()
                                ui.waitUntil(20_000) { displayed(hasText("Groups") and hasClickAction()) }
                                ui.onAllNodesWithText(incomingText).assertCountEquals(0)
                                capture("$capturePrefix-consumed")
                            }
                            if (groupAlert && !cold) {
                                click(hasText("Back"))
                                click(hasText("Groups"))
                                ui.waitUntil(20_000) { displayed(hasText("Create group") and hasClickAction()) }
                                ui.onAllNodesWithText(incomingText).assertCountEquals(0)
                                capture("group-warm-normal-list")
                            }
                        }
                        assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                        assertEquals(backend, backends.load())
                        assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { device.pressBack(); finishFixtureActivities(); activitiesStopped = true },
                { notifications.activeNotifications.filter { (it.tag to it.id) !in existingAlerts }
                    .forEach { notifications.cancel(it.tag, it.id) } },
                { requireStopped(); retained.restoreFiles() },
                { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), original); check(wallets.loadExisting().exportJson() == originalJson) },
                { requireStopped(); backends.save(originalBackend); check(backends.load() == originalBackend) })
        }
    }

    private fun previewChoice(scenario: ActivityScenario<MainActivity>, peer: ControlledFeedPeer,
        sender: SaitoWallet, wallet: SaitoWallet, notifications: NotificationManager,
        existingAlerts: Set<Pair<String?, Int>>) {
        val device = UiDevice.getInstance(instrumentation)
        val control = hasText("Private message previews") and isToggleable()
        fun choice(): SemanticsNodeInteraction {
            click(hasText("Settings"))
            ui.waitUntil(20_000) { ui.onAllNodes(control and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            return ui.onNode(control).performScrollTo().assertIsDisplayed()
        }
        fun published(text: String, timestamp: Long, request: Int, expected: String, name: String) {
            val transaction = PrivateChat.create(sender, wallet.publicKey, text, timestamp)
            peer.sendIncoming(transaction.applicationRequest(1).drop(5).toByteArray(), requestId = request)
            ui.waitUntil(20_000) { notifications.activeNotifications.any { it.tag?.endsWith(":" + transaction.id) == true } }
            val alert = notifications.activeNotifications.single { it.tag?.endsWith(":" + transaction.id) == true }
            assertEquals("private-chats", alert.notification.channelId)
            assertEquals(expected, alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertTrue(device.openNotification())
            assertTrue(device.wait(Until.hasObject(By.text(expected)), 10_000))
            capture(name)
            device.pressBack()
        }
        choice().assertIsOff().performClick()
        ui.waitUntil(20_000) {
            try {
                ui.onNode(control).assertIsOn()
                true
            } catch (_: AssertionError) { false }
        }
        capture("private-previews-enabled")
        device.pressBack()
        scenario.recreate()
        choice().assertIsOn()
        capture("private-previews-reopened")
        device.pressBack()
        published("A readable private preview", 1700000140100, 802,
            "A readable private preview", "private-previews-text-alert")
        val photo = instrumentation.context.assets.open("native-images-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString
        }
        published(composeChatPhoto(photo, "A caption without image markup"), 1700000140200, 803,
            "A caption without image markup", "private-previews-caption-alert")
        published("<video src=\"https://example.invalid/clip.mp4\"></video>", 1700000140250, 805,
            "New message", "private-previews-unsupported-generic")
        choice().assertIsOn().performClick()
        ui.waitUntil(20_000) {
            try {
                ui.onNode(control).assertIsOff()
                true
            } catch (_: AssertionError) { false }
        }
        ui.waitUntil(20_000) { notifications.activeNotifications.none {
            (it.tag to it.id) !in existingAlerts && it.notification.channelId == "private-chats"
        } }
        capture("private-previews-disabled")
        device.pressBack()
        published("Private text after disabling previews", 1700000140300, 804,
            "New message", "private-previews-disabled-generic")
    }

    private fun recreateCurrentActivity() {
        val previous = activeMainActivities().single()
        instrumentation.runOnMainSync { previous.recreate() }
        ui.waitUntil(20_000) { activeMainActivities().singleOrNull()?.let { it !== previous } == true }
    }

    private fun activeMainActivities(): List<MainActivity> {
        var result = emptyList<MainActivity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            result = Stage.values().filter { it != Stage.DESTROYED }.flatMap {
                monitor.getActivitiesInStage(it)
            }.filterIsInstance<MainActivity>().distinct()
        }
        return result
    }

    private fun finishFixtureActivities() {
        val activities = activeMainActivities()
        instrumentation.runOnMainSync { activities.forEach { it.finish() } }
        instrumentation.waitForIdleSync()
        ui.waitUntil(5_000) { activeMainActivities().isEmpty() }
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

package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChatMutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun privateConversationMuteKeepsIncomingMessagesAndTheDraftAfterReopening() = exercisePrivateMute()

    @Test fun anUnmutedPrivateMessageShowsAGenericAndroidNotification() = exercisePrivateMute(ConversationCase.ALERT)

    @Test fun aFailedPrivateUnmuteKeepsReadableMessagesSilentAndAllowsRepair() = exercisePrivateMute(ConversationCase.FAILED_UNMUTE)

    private enum class ConversationCase { MUTED, ALERT, FAILED_UNMUTE, UNREADABLE_POLICY }

    @Test fun unreadablePersonalChoicesRetainIncomingWithoutAlertsAndRecoverWithoutRetrospectiveAlerts() =
        exercisePrivateMute(ConversationCase.UNREADABLE_POLICY)

    private fun exercisePrivateMute(case: ConversationCase = ConversationCase.MUTED) {
        val alert = case == ConversationCase.ALERT || case == ConversationCase.UNREADABLE_POLICY
        val prefix = when (case) {
            ConversationCase.FAILED_UNMUTE -> "private-failed-unmute"
            ConversationCase.UNREADABLE_POLICY -> "private-unreadable-policy"
            else -> "private"
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-private-chat-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val contact = reference.get("sender").asString
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(contact) // Public unsafe fixture only.
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.") } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val alerts = FixtureAlerts(context)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == reference.get("groupId").asString) reads.incrementAndGet()
            "[]"
        })
        val relay = JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", "chat relay"); add("data", reference.getAsJsonObject("signedInner"))
        }
        val incoming = SaitoWire.applicationRequest(wallet.publicKey, relay.toString().toByteArray(Charsets.UTF_8),
            1, reference.get("timestamp").asLong).drop(5).toByteArray()
        val text = reference.getAsJsonObject("plaintext").get("message").asString
        val draft = "Keep this unsent draft while muting 😀"
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture(if (alert) "$prefix-alert-failure" else "$prefix-mute-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                            click(hasText(contact))
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > 0 }
                        }
                        open()
                        if (alert) {
                            peer.sendIncoming(incoming)
                            ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            verifyGenericNotification(alerts, "Private chats", "$prefix-generic-system-notification")
                            assertRedeliveryStaysSilent(peer, incoming, text, alerts, "$prefix-duplicate-stays-silent")
                            if (case == ConversationCase.UNREADABLE_POLICY)
                                assertUnreadablePolicyRetainsMessages(context, wallet, peer, alerts)
                            assertUnchangedIdentityAndNoOutgoingMessages(context, wallet, peer)
                            return@withFailureCapture
                        }
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        click(hasContentDescription("Mute conversation"))
                        ui.onNodeWithText("Conversation notifications").assertIsDisplayed()
                        ui.onNodeWithText("Messages stay visible.", substring = true).assertIsDisplayed()
                        ui.onNodeWithText("Account", substring = true).assertIsDisplayed()
                        click(hasText("Mute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation")) }
                        peer.sendIncoming(incoming)
                        ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        alerts.assertSilence()
                        capture("$prefix-muted-incoming-readable")
                        scenario.recreate(); open()
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        showMessage(text)
                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))) }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        check(alerts.newAlerts().isEmpty())
                        capture("$prefix-muted-reopened")
                        if (case == ConversationCase.FAILED_UNMUTE) {
                            val freshText = "Private mute remains after a failed save"
                            val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
                            val fresh = PrivateChat.create(sender, wallet.publicKey, freshText, 1700000080100)
                            assertFailedUnmuteRecovers(scenario, { open() }, alerts, peer,
                                FixtureIncoming(fresh.applicationRequest(1).drop(5).toByteArray(), freshText), prefix)
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        }
                        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Mute conversation")) }
                        showMessage(text)
                        alerts.assertSilence()
                        capture("$prefix-unmuted-history-retained")
                        assertRedeliveryStaysSilent(peer, incoming, text, alerts, "$prefix-muted-original-stays-silent-after-unmute")
                        assertUnchangedIdentityAndNoOutgoingMessages(context, wallet, peer)
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                alerts.restore()
            }, {
                retained.restoreFiles()
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                retained.restorePreferences()
            })
        }
    }

    @Test fun groupConversationMuteKeepsCompatibleMessagesAndTheDraftAfterReopening() = exerciseGroupMute()

    @Test fun anUnmutedGroupMessageShowsAGenericAndroidNotification() = exerciseGroupMute(ConversationCase.ALERT)

    @Test fun aFailedGroupUnmuteKeepsReadableMessagesSilentAndAllowsRepair() = exerciseGroupMute(ConversationCase.FAILED_UNMUTE)

    private fun exerciseGroupMute(case: ConversationCase = ConversationCase.MUTED) {
        val alert = case == ConversationCase.ALERT
        val prefix = if (case == ConversationCase.FAILED_UNMUTE) "group-failed-unmute" else "group"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-group-chat-message-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val id = reference.get("groupId").asString
        val name = "Conversation mute group"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4").withNamedChatGroup(id, name) // Public unsafe fixture only.
        val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val creation = GroupChat.create(admin, id, name, listOf(wallet.publicKey), 1700000123400)
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.map { "groupchat.${it.id}.${wallet.publicKey}." } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val alerts = FixtureAlerts(context)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == id) reads.incrementAndGet()
            "[]"
        })
        val detached = JsonParser.parseString(reference.get("envelope").asString).asJsonObject
        val messageBytes = java.util.Base64.getDecoder().decode(detached.get("m").asString)
        val incoming = java.util.Base64.getDecoder().decode(detached.get("t").asString) + messageBytes
        java.nio.ByteBuffer.wrap(incoming).putInt(8, messageBytes.size)
        val text = reference.get("text").asString
        val draft = "Keep this group draft while muting 😀"
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet); backends.save(SaitoBackend.OWNER)
            nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, SaitoBackend.OWNER).receive(creation.envelope())
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ capture(if (alert) "group-alert-failure" else "$prefix-mute-failure") }) {
                        fun open() {
                            val chat = hasText("Chat") and hasClickAction() and isEnabled()
                            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                            if (!displayed(hasText("Groups"))) click(chat)
                            click(hasText("Groups")); click(hasText(name))
                            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > 0 }
                        }
                        open()
                        if (alert) {
                            peer.sendIncoming(incoming)
                            ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            verifyGenericNotification(alerts, "Group chats", "group-generic-system-notification")
                            assertRedeliveryStaysSilent(peer, incoming, text, alerts, "group-duplicate-stays-silent")
                            assertUnchangedIdentityAndNoOutgoingMessages(context, wallet, peer)
                            return@withFailureCapture
                        }
                        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        click(hasContentDescription("Mute conversation"))
                        ui.onNodeWithText("Messages stay visible.", substring = true).assertIsDisplayed()
                        click(hasText("Mute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation")) }
                        peer.sendIncoming(incoming)
                        ui.waitUntil(20_000) { displayed(hasText(text)) && peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        alerts.assertSilence()
                        capture("$prefix-muted-incoming-readable")
                        scenario.recreate(); open()
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        showMessage(text)
                        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled() and
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft))) }
                        check(alerts.newAlerts().isEmpty())
                        capture("$prefix-muted-reopened")
                        if (case == ConversationCase.FAILED_UNMUTE) {
                            val freshText = "Group mute remains after a failed save"
                            val fresh = GroupChat.message(admin, GroupChat.readCreation(creation.envelope()), freshText, 1700000123500)
                            assertFailedUnmuteRecovers(scenario, { open() }, alerts, peer,
                                FixtureIncoming(fresh.applicationRequest(1).drop(5).toByteArray(), freshText), prefix)
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft)))
                        }
                        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
                        ui.waitUntil(20_000) { displayed(hasContentDescription("Mute conversation")) }
                        showMessage(text)
                        alerts.assertSilence()
                        capture("$prefix-unmuted-history-retained")
                        assertRedeliveryStaysSilent(peer, incoming, text, alerts, "$prefix-muted-original-stays-silent-after-unmute")
                        assertUnchangedIdentityAndNoOutgoingMessages(context, wallet, peer)
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { alerts.restore() }, {
                retained.restoreFiles()
            }, { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) }, {
                retained.restorePreferences()
            })
        }
    }

    @Test fun conversationChoicesFollowTheBackendAndRemainIsolatedWhenTheWalletChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val name = "Wallet-scoped conversation"
        fun fixtureWallet(scalar: String) = SaitoWallet.fromPrivateKey("0".repeat(63) + scalar)
            .withChatContact(admin.publicKey).withNamedChatGroup(groupId, name)
        val first = fixtureWallet("2"); val second = fixtureWallet("4")
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val retained = listOf(first, second).map { wallet ->
            val prefixes = SaitoBackend.entries.flatMap { backend ->
                listOf("chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
                    "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.")
            } + "personal.${wallet.publicKey}."
            ChatFixtureState(context, prefixes, wallet.publicKey)
        }
        val alerts = FixtureAlerts(context)
        val reads = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            criteria.get("field3")?.asString?.let { reads.computeIfAbsent(it) { AtomicInteger() }.incrementAndGet() }
            "[]"
        })
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun chatList() {
            val chat = hasText("Chat") and hasClickAction() and isEnabled()
            ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
            if (!displayed(hasText("Groups"))) click(chat)
            ui.waitUntil(20_000) { displayed(hasText("Groups")) }
        }
        fun open(wallet: SaitoWallet, group: Boolean = false) {
            chatList()
            val id = if (group) groupId else PrivateChat.conversationId(wallet.publicKey, admin.publicKey)
            val before = reads[id]?.get() ?: 0
            if (group) { click(hasText("Groups")); click(hasText(name)) }
            else click(hasText(admin.publicKey))
            ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && (reads[id]?.get() ?: 0) > before }
        }
        fun returnToChats(group: Boolean = false) {
            device.pressBack()
            if (group) {
                ui.waitUntil(20_000) { displayed(hasText("Create group")) }
                device.pressBack()
            }
            ui.waitUntil(20_000) { displayed(hasText("Groups")) }
        }
        fun mute() {
            click(hasContentDescription("Mute conversation")); click(hasText("Mute"))
            ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation") and isEnabled()) }
        }
        fun receive(wallet: SaitoWallet, text: String, requestId: Int) {
            val incoming = PrivateChat.create(admin, wallet.publicKey, text, 1700000080400L + requestId)
            peer.sendIncoming(incoming.applicationRequest(1).drop(5).toByteArray(), requestId = requestId)
            ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
                it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == requestId
            } }
            showMessage(text)
        }
        var primary: Throwable? = null
        try {
            retained.forEach { it.prepare() }
            wallets.replace(original, first); backends.save(SaitoBackend.OWNER)
            val creation = GroupChat.create(admin, groupId, name, listOf(first.publicKey, second.publicKey), 1700000123400)
            listOf(first, second).forEach { wallet -> SaitoBackend.entries.forEach { backend ->
                nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, backend).receive(creation.envelope())
            } }
            peer.start().use { server ->
                fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen"))
                launch().use {
                    withFailureCapture({ capture("scope-backend-failure") }) {
                        open(first); mute(); returnToChats()
                        open(first, group = true); mute(); returnToChats(group = true)
                        device.pressBack()
                        click(hasText("Settings")); click(hasText("saito.io"))
                        ui.waitUntil(20_000) { displayed(hasText("Server: saito.io")) }
                        open(first)
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        capture("scope-private-muted-on-official")
                        returnToChats(); open(first, group = true)
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        capture("scope-group-muted-on-official")
                        check(wallets.loadExisting().exportJson() == first.exportJson() && backends.load() == SaitoBackend.OFFICIAL)
                    }
                }
                // The protected identity seam avoids repeating the already accepted SAF replacement flow.
                wallets.replace(wallets.loadOrCreate(), second)
                val secondText = "Only the second wallet receives this private message"
                launch().use {
                    withFailureCapture({ capture("scope-second-wallet-failure") }) {
                        open(second)
                        ui.onNodeWithContentDescription("Mute conversation").assertIsDisplayed()
                        receive(second, secondText, 900)
                        verifyGenericNotification(alerts, "Private chats", "scope-second-wallet-generic-alert")
                        mute(); returnToChats(); open(second, group = true)
                        ui.onNodeWithContentDescription("Mute conversation").assertIsDisplayed()
                        capture("scope-same-group-unmuted-for-second-wallet")
                        check(wallets.loadExisting().exportJson() == second.exportJson() && backends.load() == SaitoBackend.OFFICIAL)
                    }
                }
                alerts.restore()
                wallets.replace(wallets.loadOrCreate(), first)
                launch().use {
                    withFailureCapture({ capture("scope-restored-wallet-failure") }) {
                        open(first)
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        ui.onNodeWithText(secondText).assertDoesNotExist()
                        receive(first, "The first wallet still silences its own conversation", 901)
                        alerts.assertSilence(); capture("scope-restored-private-muted-incoming-readable")
                        returnToChats(); open(first, group = true)
                        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                        capture("scope-restored-group-choice-retained")
                        check(wallets.loadExisting().exportJson() == first.exportJson() && backends.load() == SaitoBackend.OFFICIAL)
                        check(peer.clientKeys.toSet() == setOf(first.publicKey, second.publicKey))
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { alerts.restore() }, { retained[0].restoreFiles() }, { retained[1].restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { retained[0].restorePreferences() }, { retained[1].restorePreferences() })
        }
    }

    private fun showMessage(text: String) {
        ui.waitUntil(20_000) {
            if (!displayed(hasText(text))) {
                try { ui.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }
                catch (_: AssertionError) { /* Incoming UI publication may still be pending. */ }
            }
            displayed(hasText(text))
        }
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private data class FixtureIncoming(val bytes: ByteArray, val text: String)

    private fun assertUnreadablePolicyRetainsMessages(context: android.content.Context, wallet: SaitoWallet,
        peer: ControlledFeedPeer, alerts: FixtureAlerts) {
        val file = File(context.noBackupFilesDir, "personal.${wallet.publicKey}.v1")
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        fun packet(text: String, timestamp: Long) = FixtureIncoming(
            PrivateChat.create(sender, wallet.publicKey, text, timestamp).applicationRequest(1).drop(5).toByteArray(), text)
        val retained = packet("Incoming remains readable while alert preferences cannot be read", 1700000080200)
        alerts.restore()
        ui.waitUntil(5_000) { alerts.newAlerts().isEmpty() }
        synchronized(SaitoLocalState.lock) {
            check(listOf(file, File(file.path + ".bak"), File(file.path + ".new")).none { it.exists() })
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        var primary: Throwable? = null
        try {
            peer.sendIncoming(retained.bytes, requestId = 779)
            ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
                it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 779
            } }
            showMessage(retained.text)
            alerts.assertSilence()
            capture("private-unreadable-policy-incoming-retained")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { synchronized(SaitoLocalState.lock) { check(file.delete()) } })
        }
        assertRedeliveryStaysSilent(peer, retained.bytes, retained.text, alerts,
            "private-repaired-policy-original-stays-silent", requestId = 780)
        val fresh = packet("New private alerts resume after preference storage repair", 1700000080300)
        peer.sendIncoming(fresh.bytes, requestId = 781)
        ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
            it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 781
        } }
        showMessage(fresh.text)
        verifyGenericNotification(alerts, "Private chats", "private-repaired-policy-new-alert")
    }

    private fun assertFailedUnmuteRecovers(scenario: ActivityScenario<MainActivity>, reopen: () -> Unit,
        alerts: FixtureAlerts, peer: ControlledFeedPeer, fresh: FixtureIncoming, prefix: String) {
        scenario.onActivity { it.intent.putExtra("personalCommitSyncUnavailable", true) }
        click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
        ui.waitUntil(20_000) { displayed(hasText("Your change could not be saved. Try again.")) &&
            displayed(hasContentDescription("Unmute conversation") and isEnabled()) }
        peer.sendIncoming(fresh.bytes, requestId = 779)
        ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
            it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == 779
        } }
        showMessage(fresh.text)
        alerts.assertSilence()
        capture("$prefix-save-failed-choice-retained")
        scenario.recreate(); reopen()
        ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
        showMessage(fresh.text)
        alerts.assertSilence()
        capture("$prefix-failed-choice-reopened")
        scenario.onActivity { it.intent.removeExtra("personalCommitSyncUnavailable") }
    }

    private fun assertRedeliveryStaysSilent(peer: ControlledFeedPeer, incoming: ByteArray, text: String,
        alerts: FixtureAlerts, captureName: String, requestId: Int = 778) {
        alerts.restore()
        ui.waitUntil(5_000) { alerts.newAlerts().isEmpty() }
        peer.sendIncoming(incoming, requestId = requestId)
        ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
            it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == requestId
        } }
        showMessage(text)
        ui.onAllNodesWithText(text).assertCountEquals(1)
        alerts.assertSilence()
        capture(captureName)
    }

    private fun assertUnchangedIdentityAndNoOutgoingMessages(context: android.content.Context,
        wallet: SaitoWallet, peer: ControlledFeedPeer) {
        check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson() &&
            AndroidBackendStore(context).load() == SaitoBackend.OWNER)
        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
        peer.assertHealthy()
    }

    private fun verifyGenericNotification(alerts: FixtureAlerts, category: String, captureName: String) {
        ui.waitUntil(20_000) { alerts.newAlerts().size == 1 }
        val notification = alerts.newAlerts().single().notification
        check(notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() == "New message")
        check(notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "Red Square")
        check(alerts.manager.getNotificationChannel(notification.channelId).name.toString() == category)
        check(notification.contentIntent != null)
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        check(device.openNotification())
        check(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("New message")), 20_000))
        capture(captureName)
        device.pressBack()
    }

    private class FixtureAlerts(context: android.content.Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        private val existing = manager.activeNotifications.map { it.tag to it.id }.toSet()
        fun newAlerts() = manager.activeNotifications.filter { (it.tag to it.id) !in existing }
        fun assertSilence() {
            val until = android.os.SystemClock.elapsedRealtime() + 2_000
            do {
                check(newAlerts().isEmpty())
                android.os.SystemClock.sleep(50)
            } while (android.os.SystemClock.elapsedRealtime() < until)
        }
        fun restore() { newAlerts().forEach { manager.cancel(it.tag, it.id) } }
    }

    private fun click(target: SemanticsMatcher) {
        val action = target and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { displayed(action) }
        ui.onNode(action).performClick()
    }
    private fun displayed(target: SemanticsMatcher) = try { ui.onNode(target).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun capture(name: String) = captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "chat-mute-evidence/$name.png"))
}

package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/** A real interrupted policy commit with both conversation ID forms and device-only snapshots. */
class ChatMuteProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "chat-mute-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "chat-mute-process-originals")
    private val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixtures only. Never fund.
    private val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "9").publicKey
    private val groupName = "Mute process recovery"
    private val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        .withChatContact(other.publicKey).withNamedChatGroup(groupId, groupName)
    private val reads = AtomicInteger()
    private fun cached(group: Boolean) = if (group) "Group history before interrupted unmute" else "Private history before interrupted unmute"
    private fun draft(group: Boolean) = if (group) "Retain my unsent group draft 😀" else "Retain my unsent private draft 😀"

    @Test fun terminateDuringUnmuteWithBothConversationChoicesSaved() {
        check(!checkpoint.exists() && !originals.exists())
        val captures = File(context.cacheDir, "chat-mute-process-evidence")
        check(!captures.exists() || captures.deleteRecursively())
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "prepared"); addProperty("owner", fixture.publicKey)
            addProperty("backend", backend.id); addProperty("pid", Process.myPid())
            addProperty("walletDigest", digest(fixture.exportJson())); addProperty("originalWalletDigest", digest(original.exportJson()))
            add("savedFiles", snapshotProcessFixtureFiles(context, originals, fileNames(backend)))
            add("preferences", JsonArray().apply { preferenceKeys().forEach { (name, key) ->
                val prefs = context.getSharedPreferences(name, 0)
                add(JsonObject().apply {
                    addProperty("name", name); addProperty("key", key)
                    if (prefs.contains(key)) addProperty("value", prefs.getBoolean(key, false))
                })
            } })
        }
        writeCheckpoint(row)
        synchronized(SaitoLocalState.lock) { processFixtureFiles(context,fileNames(backend)).filterNot { it.startsWith("wallet.") }.forEach { name ->
            val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
        } }
        wallets.replace(original, fixture)
        preferenceKeys().forEach { (name, key) -> check(context.getSharedPreferences(name, 0).edit().putBoolean(key, true).commit()) }
        val creation = GroupChat.create(other, groupId, groupName, listOf(fixture.publicKey), 1700000123400)
        val privateMessage = PrivateChat.create(other, fixture.publicKey, cached(false), 1700000123401)
        val groupMessage = GroupChat.message(other, GroupChat.readCreation(creation.envelope()), cached(true), 1700000123402)
        ChatHistoryStore(context, fixture, backend, other.publicKey).save(listOf(privateMessage.envelope()))
        GroupChatStore(context, fixture, backend).apply { receive(creation.envelope()); receive(groupMessage.envelope()) }
        val peer = peer()
        peer.start().use { server -> launch(server.port) { scenario ->
            open()
            muteAndType(false); backToChat()
            open(group = true); muteAndType(true); backToChat(group = true)
            open()
            assertDraft(false); ui.onNodeWithText(cached(false)).assertIsDisplayed()
            ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
            capture("both-choices-before-death.png")
            assertSelection(row, backend)
            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
            peer.assertHealthy()
            row.addProperty("phase", "pending"); writeCheckpoint(row)
            scenario.onActivity { it.intent.putExtra("personalCommitTerminate", true) }
            click(hasContentDescription("Unmute conversation")); click(hasText("Unmute"))
            // DEBUG sync seam terminates after active write while the previous-policy guard remains.
            ui.waitUntil(15_000) { false }
            error("Expected actual process termination")
        } }
    }

    @Test fun coldRecoveryRetainsBothChoicesDraftsAndReadableSilentIncoming() {
        val row = readCheckpoint()
        assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        assertSelection(row, backend)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val existing = manager.activeNotifications.map { it.tag to it.id }.toSet()
        fun newAlerts() = manager.activeNotifications.filter { (it.tag to it.id) !in existing }
        var primary: Throwable? = null
        try {
            val peer = peer()
            peer.start().use { server -> launch(server.port) {
                for (group in listOf(false, true)) {
                    open(group)
                    ui.onNodeWithContentDescription("Unmute conversation").assertIsDisplayed()
                    assertDraft(group); ui.onNodeWithText(cached(group)).assertIsDisplayed()
                    val freshText = if (group) "Group incoming after interrupted unmute" else "Private incoming after interrupted unmute"
                    val message = if (group) GroupChat.message(other,
                        requireNotNull(GroupChatStore(context, fixture, backend).load(groupId)).group, freshText, 1700000123500)
                        else PrivateChat.create(other, fixture.publicKey, freshText, 1700000123500)
                    val request = if (group) 951 else 950
                    peer.sendIncoming(message.applicationRequest(1).drop(5).toByteArray(), requestId = request)
                    ui.waitUntil(20_000) { peer.incomingAcknowledgements.any {
                        it[0] == 13.toByte() && java.nio.ByteBuffer.wrap(it, 1, 4).int == request
                    } }
                    ui.waitUntil(20_000) {
                        if (!displayed(hasText(freshText))) {
                            try { ui.onNode(hasContentDescription(if (group) "Group messages" else "Private messages")).performScrollToNode(hasText(freshText)) }
                            catch (_: AssertionError) { /* Receipt may precede UI publication. */ }
                        }
                        displayed(hasText(freshText))
                    }
                    ui.onNodeWithText(freshText).assertIsDisplayed()
                    val until = android.os.SystemClock.elapsedRealtime() + 2_000
                    do { check(newAlerts().isEmpty()); android.os.SystemClock.sleep(50) }
                    while (android.os.SystemClock.elapsedRealtime() < until)
                    assertDraft(group); capture(if (group) "group-recovered-muted-readable.png" else "private-recovered-muted-readable.png")
                    backToChat(group)
                }
                assertSelection(row, backend)
                check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == fixture.publicKey })
                check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                peer.assertHealthy()
                row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid()); writeCheckpoint(row)
            } }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { newAlerts().forEach { manager.cancel(it.tag, it.id) } }) }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) return
        val row = readCheckpoint()
        val backend = SaitoBackend.entries.single { it.id == row.get("backend").asString }
        cleanupAfterTest(null, {
            restoreProcessFixtureFiles(context, originals, fileNames(backend), row.getAsJsonArray("savedFiles"))
        }, {
            row.getAsJsonArray("preferences").forEach { value ->
                val pref = value.asJsonObject
                val pair = pref.get("name").asString to pref.get("key").asString
                check(pair in preferenceKeys())
                check(context.getSharedPreferences(pair.first, 0).edit().apply {
                    if (pref.has("value")) putBoolean(pair.second, pref.get("value").asBoolean) else remove(pair.second)
                }.commit())
            }
        })
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend, AndroidBackendStore(context).load())
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }

    private fun peer() = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = {
        reads.incrementAndGet(); "[]"
    })
    private fun fileNames(backend: SaitoBackend): Set<String> {
        val conversation = PrivateChat.conversationId(fixture.publicKey, other.publicKey)
        return listOf("wallet.v1", "wallet.recovery.v1", "personal.${fixture.publicKey}.v1",
            "personal.${fixture.publicKey}.guard.v1", "personal.${fixture.publicKey}.recovery.v1",
            "chat.${backend.id}.${fixture.publicKey}.$conversation.v1", "chatwork.${backend.id}.${fixture.publicKey}.$conversation.v1",
            "groupchat.${backend.id}.${fixture.publicKey}.$groupId.v1",
            "chatpush.${fixture.publicKey}.${backend.id}.ledger.v1")
            .flatMap { listOf(it, "$it.bak", "$it.new") }.toSet()
    }
    private fun preferenceKeys() = listOf("notification-permission" to "offered", "wallet-backup-reminders" to "hidden:${fixture.publicKey}")
    private fun assertSelection(row: JsonObject, backend: SaitoBackend) {
        assertEquals(fixture.publicKey, row.get("owner").asString)
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend, AndroidBackendStore(context).load())
    }
    private fun muteAndType(group: Boolean) {
        click(hasContentDescription("Mute conversation")); click(hasText("Mute"))
        ui.waitUntil(20_000) { displayed(hasContentDescription("Unmute conversation") and isEnabled()) }
        ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft(group))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    private fun assertDraft(group: Boolean) {
        val expected = hasSetTextAction() and isEnabled() and
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(draft(group)))
        ui.waitUntil(20_000) { displayed(expected) }; ui.onNode(expected).assertIsDisplayed()
    }
    private fun open(group: Boolean = false) {
        val chat = hasText("Chat") and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
        if (!displayed(hasText("Groups"))) click(chat)
        val before = reads.get()
        if (group) { click(hasText("Groups")); click(hasText(groupName)) } else click(hasText(other.publicKey))
        ui.waitUntil(20_000) { displayed(hasSetTextAction() and isEnabled()) && reads.get() > before }
    }
    private fun backToChat(group: Boolean = false) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
        if (group) { ui.waitUntil(20_000) { displayed(hasText("Create group")) }; device.pressBack() }
        ui.waitUntil(20_000) { displayed(hasText("Groups")) }
    }
    private fun launch(port: Int, journey: (ActivityScenario<MainActivity>) -> Unit) =
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$port/wsopen")).use { scenario ->
            withFailureCapture({ capture("failure.png") }) { journey(scenario) }
        }
    private fun click(target: SemanticsMatcher) {
        val action = target and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { displayed(action) }; ui.onNode(action).performClick()
    }
    private fun displayed(target: SemanticsMatcher) = try { ui.onNode(target).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun readCheckpoint() = JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun writeCheckpoint(row: JsonObject) = checkpoint.outputStream().use { it.write(row.toString().toByteArray()); it.fd.sync() }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-mute-process-evidence/$name"))
}

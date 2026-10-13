package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrivateChatPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun firstChatOffersOptionalAlertsAndSkippingKeepsChatAvailable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        if (android.os.Build.VERSION.SDK_INT >= 33) check(context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_DENIED)
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val original = wallet.exportJson()
        val preferences = listOf("wallet-backup-reminders" to "hidden:${wallet.publicKey}",
            "notification-permission" to "offered").map { (name, key) ->
            val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null)
        }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, key != "offered").commit()) }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "private-chat-evidence/first-chat-permission-failure.png")) }) {
                        waitFor("Chat")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        waitFor("Android notification alerts")
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-offer.png"),
                            "Android notification alerts")
                        ui.onNodeWithText("Not now").assertIsDisplayed().performClick()
                        ui.waitUntil(15_000) { context.getSharedPreferences("notification-permission", 0).getBoolean("offered", false) }
                        waitFor("Private messages are encrypted.")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-skipped.png"),
                            "Private messages are encrypted.")
                        scenario.recreate()
                        waitFor("Private messages are encrypted.")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-recreated.png"),
                            "Private messages are encrypted.")
                        check(AndroidWalletStore(context).loadOrCreate().exportJson() == original)
                        check(peer.transactions.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            val restoration = preferences.map { (prefs, key, previous) -> {
                check(prefs.edit().apply {
                    if (previous == null) remove(key) else putBoolean(key, previous)
                }.commit())
            } } + listOf<() -> Unit>({ check(AndroidWalletStore(context).loadOrCreate().exportJson() == original) })
            cleanupAfterTest(primary, *restoration.toTypedArray())
        }
    }

    private enum class HistoryCase { AVAILABLE, STORAGE_FAILURE, LIVE, SEND, SEND_INTERRUPTED, NEW_SEND, IMPORTED_ID, IMPORTED_PARTIAL, LIST_LIVE, PARTIAL_KEYS }

    @Test fun anImportedContactOpensItsAvailableAuthenticatedPrivateHistory() = exerciseHistory(HistoryCase.AVAILABLE)

    @Test fun failedLocalSavingDoesNotLoseReadableHistoryOnRefresh() = exerciseHistory(HistoryCase.STORAGE_FAILURE)

    @Test fun aLivePrivateRelayMessageRemainsReadableAfterReopening() = exerciseHistory(HistoryCase.LIVE)

    @Test fun aNativePrivateSendPropagatesAndRelaysTheSameEncryptedOriginal() = exerciseHistory(HistoryCase.SEND)

    @Test fun anInterruptedPrivateSendRemainsSavedWithoutAutomaticRebroadcast() = exerciseHistory(HistoryCase.SEND_INTERRUPTED)

    @Test fun aFreshWalletCanOpenAndSendToANewPrivateContact() = exerciseHistory(HistoryCase.NEW_SEND)

    @Test fun anImportedConversationIdRecoversAvailablePrivateHistoryWithoutAContactKeyEntry() = exerciseHistory(HistoryCase.IMPORTED_ID)

    @Test fun oneUnavailableImportedConversationDoesNotHideAnotherRecoveredPrivateConversation() = exerciseHistory(HistoryCase.IMPORTED_PARTIAL)

    @Test fun aNewIncomingPrivateConversationAppearsWhileTheChatListIsOpen() = exerciseHistory(HistoryCase.LIST_LIVE)

    @Test fun aKeyOnlyImportKeepsReadableCachedMessagesAlongsideMissingExchangedKeys() = exerciseHistory(HistoryCase.PARTIAL_KEYS)

    private fun exerciseHistory(case: HistoryCase) {
        val storageFailure = case == HistoryCase.STORAGE_FAILURE
        val listLive = case == HistoryCase.LIST_LIVE
        val partialKeys = case == HistoryCase.PARTIAL_KEYS
        val live = case == HistoryCase.LIVE || listLive
        val interruptedSend = case == HistoryCase.SEND_INTERRUPTED
        val newContact = case == HistoryCase.NEW_SEND
        val partialDiscovery = case == HistoryCase.IMPORTED_PARTIAL
        val importedId = case == HistoryCase.IMPORTED_ID || partialDiscovery
        val sending = case == HistoryCase.SEND || interruptedSend || newContact || partialKeys
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val vectors = instrumentation.context.assets.open("native-private-chat-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors") }
        val reference = vectors[0].asJsonObject
        val counterpart = reference.get("sender").asString
        val publicWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val options = JsonParser.parseString(publicWallet.exportJson()).asJsonObject.apply {
            if (!newContact && !importedId && !listLive && !partialKeys) add("keys", JsonArray().apply { add(JsonObject().apply {
                addProperty("publicKey", counterpart); addProperty("added", true)
            }) })
            if (importedId) add("chat", JsonObject().apply {
                add("groups", JsonArray().apply {
                    add(reference.get("groupId"))
                    if (partialDiscovery) add("e".repeat(64))
                })
            }) // Existing Saito backup stores group IDs, not LocalForage messages.
        }
        val wallet = SaitoWallet.fromBackup(options.toString())
        val completeKeyWallet = if (partialKeys) SaitoWallet.fromBackup(JsonParser.parseString(wallet.exportJson()).asJsonObject.apply {
            add("keys", JsonArray().apply { add(JsonObject().apply {
                addProperty("publicKey", counterpart)
                addProperty("aes_secret", vectors[2].asJsonObject.get("sharedSecretHex").asString)
            }) })
        }.toString()) else null
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate().exportJson()
        val priorBackend = AndroidBackendStore(context).load()
        val preferences = listOf("wallet-backup-reminders" to "hidden:${wallet.publicKey}",
            "notification-permission" to "offered").map { (name, key) ->
            val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null)
        }
        val prefixes = listOf("chat.${SaitoBackend.OWNER.id}.${wallet.publicKey}.",
            "chatwork.${SaitoBackend.OWNER.id}.${wallet.publicKey}.", "personal.${wallet.publicKey}.")
        val savedFiles = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }.associate { file ->
                check(file.isFile); file.name to file.readBytes()
            }
        }
        val historyReads = java.util.concurrent.atomic.AtomicInteger()
        val historyUnavailable = java.util.concurrent.atomic.AtomicBoolean()
        val historyPruned = java.util.concurrent.atomic.AtomicBoolean()
        val cacheFile = File(context.noBackupFilesDir, "chat.${SaitoBackend.OWNER.id}.${wallet.publicKey}.${reference.get("groupId").asString}.v1")
        val outgoingText = "Native private message 😀 <&>\nsecond line"
        val recipientWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { original ->
            check(sending)
            val received = PrivateChat.read(recipientWallet, original, wallet.publicKey)
            check(received.author == wallet.publicKey && received.text == outgoingText)
            "{\"err\":\"\",\"success\":1}"
        }, archiveResponse = { criteria ->
            if (partialDiscovery && !criteria.has("field1") && criteria.get("field3")?.asString == "e".repeat(64)) {
                "invalid archive response"
            } else if (!criteria.has("field1") && criteria.get("field3")?.asString == reference.get("groupId").asString) {
                check(criteria.get("limit").asInt == 50)
                historyReads.incrementAndGet()
                if (historyUnavailable.get()) "invalid archive response"
                else if (historyPruned.get() || live) "[]"
                else JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.get("archiveEnvelope").asString) }) }.toString()
            } else "[]"
        })
        peer.disconnectAfterTransaction = interruptedSend
        val liveTransaction = if (live) {
            val relay = JsonObject().apply {
                addProperty("module", "Relay"); addProperty("request", "chat relay")
                add("data", reference.getAsJsonObject("signedInner"))
            }
            SaitoWire.applicationRequest(wallet.publicKey,
                relay.toString().toByteArray(Charsets.UTF_8), 1, reference.get("timestamp").asLong).drop(5).toByteArray()
        } else null
        var primaryFailure: Throwable? = null
        try {
            synchronized(SaitoLocalState.lock) { savedFiles.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) } }
            if (storageFailure) check(cacheFile.mkdir())
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, true).commit()) }
            store.replace(store.loadOrCreate(), wallet)
            if (partialKeys) {
                val old = vectors[2].asJsonObject // Independently pinned exchanged-key message in the same DM.
                nl.davidengelen.redsquare.chat.ChatHistoryStore(context, requireNotNull(completeKeyWallet),
                    SaitoBackend.OWNER, counterpart).save(listOf(reference.get("archiveEnvelope").asString, old.get("archiveEnvelope").asString))
                historyUnavailable.set(true) // Imported active wallet has only its identity keys; cache stays on device.
            }
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "private-chat-evidence/history-first-failure.png")) }) {
                        waitFor("Chat")
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        if (newContact) {
                            waitFor("Public key")
                            ui.onNodeWithText("Public key").performTextInput(counterpart)
                            ui.onNodeWithText("Open chat").assertIsEnabled().performClick()
                            waitFor("Message")
                            check(AndroidWalletStore(context).loadOrCreate().chatContacts().contains(counterpart))
                        } else {
                            if (listLive) {
                                waitFor("Public key")
                                ui.onNodeWithText("Refresh").assertIsEnabled()
                                var offeredAt = 0L
                                ui.waitUntil(15_000) {
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - offeredAt >= 200) {
                                        peer.sendIncoming(requireNotNull(liveTransaction))
                                        offeredAt = now
                                    }
                                    ui.onAllNodesWithText(counterpart).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                                } // The same original may be redelivered while the selected socket authenticates.
                            }
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                        }
                        val text = reference.getAsJsonObject("plaintext").get("message").asString
                        if (live && !listLive) {
                            waitFor("No available messages in this conversation.")
                            ui.onNodeWithText("Refresh").assertIsEnabled()
                            peer.sendIncoming(requireNotNull(liveTransaction))
                        }
                        waitFor(text)
                        ui.onNodeWithText(text).assertIsDisplayed()
                        if (partialKeys) {
                            waitFor(context.getString(R.string.chat_incomplete))
                            ui.onNodeWithText(context.getString(R.string.chat_incomplete)).assertIsDisplayed()
                        }
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/imported-history.png"), text)
                        if (sending) {
                            waitFor("Message")
                            ui.onNodeWithText("Message").performTextInput(outgoingText)
                            ui.onNodeWithText("Send").assertIsEnabled().performClick()
                            if (interruptedSend) {
                                waitFor(context.getString(R.string.chat_send_error))
                                waitFor("Submitted. Delivery is not confirmed.")
                                ui.onAllNodesWithText("Forwarded. Delivery is not confirmed.").assertCountEquals(0)
                            } else waitFor("Forwarded. Delivery is not confirmed.")
                            ui.onNode(hasText(outgoingText) and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
                            ui.waitUntil(5_000) { peer.transactions.size == 1 && peer.relayedMessages.size == if (interruptedSend) 0 else 1 }
                            val received = PrivateChat.receive(recipientWallet, peer.transactions.single().drop(1).toByteArray())
                            check(received.message.text == outgoingText && received.message.author == wallet.publicKey)
                            if (!interruptedSend) check(received.message.id == peer.relayedMessages.single().get("signature").asString)
                            File(context.cacheDir, "private-chat-evidence/native-sent-envelope.json").writeText(received.envelope)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/${if (interruptedSend) "private-send-interrupted" else "private-send-forwarded"}.png"), outgoingText)
                        }
                        if (live) {
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/live-relay-history.png"), text)
                            // No second socket delivery: recovery must use retained history.
                        }
                        check(historyReads.get() > 0)
                        if (storageFailure) {
                            val warning = "Could not restore or save private history on this device. Messages fetched now may be unavailable after closing Chat."
                            waitFor(warning)
                            ui.onNodeWithText(warning).assertIsDisplayed()
                            synchronized(SaitoLocalState.lock) { check(cacheFile.isDirectory && cacheFile.list()!!.isEmpty() && cacheFile.delete()) }
                            historyUnavailable.set(true)
                            ui.onNodeWithText("Refresh").assertIsDisplayed().performClick()
                            waitFor("Could not load chats. Check your connection and try Refresh.")
                            waitFor(text)
                            ui.onNodeWithText(text).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/memory-after-save-failure.png"), text)
                            historyUnavailable.set(false)
                            historyPruned.set(true)
                            ui.onNodeWithText("Refresh").assertIsDisplayed().performClick()
                            ui.waitUntil(15_000) {
                                ui.onAllNodesWithText(warning).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() &&
                                ui.onAllNodesWithText("Could not load chats. Check your connection and try Refresh.").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                            }
                            ui.onNodeWithText(text).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/retained-after-pruned-page.png"), text)
                        }
                        historyUnavailable.set(true)
                        scenario.recreate()
                        waitFor(counterpart)
                        ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                        waitFor("Could not load chats. Check your connection and try Refresh.")
                        waitFor(text)
                        ui.onNodeWithText(text).assertIsDisplayed()
                        ui.onNodeWithText("Could not load chats. Check your connection and try Refresh.").assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/cached-history-unavailable.png"), text)
                        if (sending) {
                            waitFor(outgoingText)
                            ui.onNodeWithText(outgoingText).performScrollTo().assertIsDisplayed()
                            if (interruptedSend) {
                                waitFor("Submitted. Delivery is not confirmed.")
                                ui.onAllNodesWithText("Forwarded. Delivery is not confirmed.").assertCountEquals(0)
                            }
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/${if (interruptedSend) "private-send-interrupted-recreated" else "private-send-recreated"}.png"), outgoingText)
                            check(peer.transactions.size == 1 && peer.relayedMessages.size == if (interruptedSend) 0 else 1)
                        } else check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        check(peer.reports.isEmpty())
                        if (partialKeys) {
                            // Restore the matching imported secret through protected storage, then verify the old ciphertext survived saving.
                            store.replace(store.loadOrCreate(), requireNotNull(completeKeyWallet))
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                            val oldText = vectors[2].asJsonObject.getAsJsonObject("plaintext").get("message").asString
                            waitFor(oldText)
                            ui.onNodeWithText(oldText).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/exchanged-key-restored.png"), oldText)
                            check(peer.transactions.size == 1 && peer.relayedMessages.size == 1)
                        }
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            // Activity is closed before restoring the original opaque wallet and each exact fixture file.
            var cleanupFailure: Throwable? = null
            fun restore(step: () -> Unit) {
                try { step() } catch (failure: Throwable) {
                    if (primaryFailure != null) primaryFailure!!.addSuppressed(failure)
                    else if (cleanupFailure == null) cleanupFailure = failure
                    else cleanupFailure!!.addSuppressed(failure)
                }
            }
            restore {
                val current = store.loadOrCreate()
                check(current.publicKey == wallet.publicKey || current.publicKey == SaitoWallet.fromBackup(original).publicKey)
                if (current.exportJson() != original) store.replace(current, SaitoWallet.fromBackup(original))
                check(store.loadOrCreate().exportJson() == original)
            }
            restore { AndroidBackendStore(context).save(priorBackend) }
            preferences.forEach { (prefs, key, previous) -> restore { check(prefs.edit().apply {
                if (previous == null) remove(key) else putBoolean(key, previous)
            }.commit()) } }
            restore { synchronized(SaitoLocalState.lock) {
                context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                    .forEach { file ->
                        if (file == cacheFile && file.isDirectory) check(file.list()!!.isEmpty() && file.delete())
                        else check(file.isFile && file.delete())
                    }
                savedFiles.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
            } }
            cleanupFailure?.let { throw it }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

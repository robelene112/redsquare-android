package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
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

    private enum class HistoryCase { AVAILABLE, STORAGE_FAILURE, LIVE, SEND, SEND_INTERRUPTED, NEW_SEND, IMPORTED_ID, IMPORTED_PARTIAL, LIST_LIVE, PARTIAL_KEYS, STORAGE_PARTIAL, OTHER_LIVE, MENTIONED, MENTIONED_OUTGOING, MUTED, BLOCKED, UNKNOWN_RETRY, PENDING_IMPORT, DRAFT_NAVIGATION, OLDER_HISTORY, QUOTED_REPLY, LITERAL_MARKUP, UNREADABLE_RECENT_HISTORY }

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

    @Test fun unreadableAvailableHistoryIsExplainedEvenWhenLocalSavingFails() = exerciseHistory(HistoryCase.STORAGE_PARTIAL)

    @Test fun anotherPrivateContactIsRetainedWhileADifferentConversationIsOpen() = exerciseHistory(HistoryCase.OTHER_LIVE)

    @Test fun anExistingClientMentionedPrivateMessageRemainsReadableAfterReopening() = exerciseHistory(HistoryCase.MENTIONED)

    @Test fun importedOwnMentionedMessagesRecoverTheOriginalPrivateCounterparty() = exerciseHistory(HistoryCase.MENTIONED_OUTGOING)

    @Test fun accountMutingKeepsAvailablePrivateMessagesReadable() = exerciseHistory(HistoryCase.MUTED)

    @Test fun accountBlockingHidesIncomingMessagesWithoutDeletingRecoverableHistory() = exerciseHistory(HistoryCase.BLOCKED)

    @Test fun anUncertainPrivateMessageRetriesTheSavedOriginalOnlyAfterAnExplicitTap() = exerciseHistory(HistoryCase.UNKNOWN_RETRY)

    @Test fun walletReplacementExplainsUncertainChatWorkBeforeConfirmation() = exerciseHistory(HistoryCase.PENDING_IMPORT)

    @Test fun leavingAConversationPreservesItsUnsentDraftAndDoesNotRestoreErasedText() = exerciseHistory(HistoryCase.DRAFT_NAVIGATION)

    @Test fun olderAvailablePrivateMessagesCanBeLoadedAndReadAfterServerHistoryDisappears() = exerciseHistory(HistoryCase.OLDER_HISTORY)

    @Test fun anExistingWebPrivateReplyCanBeReadAndCopiedWithoutChangingItsTypedBody() = exerciseHistory(HistoryCase.QUOTED_REPLY)

    @Test fun ordinaryTypedPrivateMarkupRemainsLiteralAfterReopening() = exerciseHistory(HistoryCase.LITERAL_MARKUP)

    @Test fun unreadableRecentPrivateMessagesDoNotPreventLoadingReadableOlderHistory() = exerciseHistory(HistoryCase.UNREADABLE_RECENT_HISTORY)

    private fun exerciseHistory(case: HistoryCase) {
        val storagePartial = case == HistoryCase.STORAGE_PARTIAL
        val storageFailure = case == HistoryCase.STORAGE_FAILURE || storagePartial
        val listLive = case == HistoryCase.LIST_LIVE
        val partialKeys = case == HistoryCase.PARTIAL_KEYS
        val otherLive = case == HistoryCase.OTHER_LIVE
        val live = case == HistoryCase.LIVE || listLive
        val interruptedSend = case == HistoryCase.SEND_INTERRUPTED
        val newContact = case == HistoryCase.NEW_SEND
        val partialDiscovery = case == HistoryCase.IMPORTED_PARTIAL
        val importedId = case == HistoryCase.IMPORTED_ID || partialDiscovery || case == HistoryCase.MENTIONED_OUTGOING
        val retrying = case == HistoryCase.UNKNOWN_RETRY
        val pendingImport = case == HistoryCase.PENDING_IMPORT
        val unreadableRecent = case == HistoryCase.UNREADABLE_RECENT_HISTORY
        val paginating = case == HistoryCase.OLDER_HISTORY || unreadableRecent
        val sending = case == HistoryCase.SEND || interruptedSend || newContact || partialKeys || retrying
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val mentioned = case == HistoryCase.MENTIONED || case == HistoryCase.MENTIONED_OUTGOING
        val replyCase = case == HistoryCase.QUOTED_REPLY || case == HistoryCase.LITERAL_MARKUP
        val fixture = instrumentation.context.assets.open(when {
            replyCase -> "native-private-chat-reply-reference.json"
            mentioned -> "native-private-chat-mentioned-reference.json"
            else -> "native-private-chat-reference.json"
        }).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val vectors = fixture.getAsJsonArray("vectors")
        val reference = vectors[when {
            case == HistoryCase.QUOTED_REPLY || case == HistoryCase.MENTIONED -> 4
            case == HistoryCase.LITERAL_MARKUP || case == HistoryCase.MENTIONED_OUTGOING -> 5
            else -> 0
        }].asJsonObject
        val counterpart = reference.get(if (case == HistoryCase.MENTIONED_OUTGOING) "recipient" else "sender").asString
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
        val outgoingText = if (retrying) vectors[1].asJsonObject.getAsJsonObject("plaintext").get("message").asString
            else "Native private message 😀 <&>\nsecond line"
        val recipientWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val otherWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Public unsafe fixture; crypto interop is covered independently.
        val otherText = "Message from another private contact"
        val olderText = "Available older private message"
        val cachedOlderText = "Previously cached private message"
        val oldestRecent = reference.get("timestamp").asLong
        val olderReads = java.util.concurrent.atomic.AtomicInteger()
        val requestedOlderCutoff = java.util.concurrent.atomic.AtomicLong(-1)
        val recentSender = if (unreadableRecent) SaitoWallet.fromBackup(JsonParser.parseString(recipientWallet.exportJson()).asJsonObject.apply {
            add("keys", JsonArray().apply { add(JsonObject().apply {
                addProperty("publicKey", wallet.publicKey)
                addProperty("aes_secret", vectors[2].asJsonObject.get("sharedSecretHex").asString)
            }) })
        }.toString()) else recipientWallet
        val recentPage = if (paginating) JsonArray().apply {
            if (!unreadableRecent) add(JsonObject().apply { addProperty("tx", reference.get("archiveEnvelope").asString) })
            for (index in (if (unreadableRecent) 0 else 1)..49) add(JsonObject().apply {
                addProperty("tx", PrivateChat.create(recentSender, wallet.publicKey,
                    "Recent private message $index", oldestRecent + index).envelope())
            })
        }.toString() else null
        val olderPage = if (recentPage != null) JsonArray().apply {
            add(JsonObject().apply { addProperty("tx", PrivateChat.create(recipientWallet, wallet.publicKey,
                olderText, oldestRecent - 1).envelope()) })
        }.toString() else null
        val otherPacket = if (otherLive) {
            val original = PrivateChat.create(otherWallet, wallet.publicKey, otherText, 1700000000999L)
            val relay = JsonObject().apply {
                addProperty("module", "Relay"); addProperty("request", "chat relay"); add("data", original.signedJson())
            }
            SaitoWire.applicationRequest(otherWallet.publicKey, relay.toString().toByteArray(Charsets.UTF_8),
                1, 1700000000999L).drop(5).toByteArray()
        } else null
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
                else if (recentPage != null) {
                    val cutoff = criteria.get("created_earlier_than").asLong
                    if (cutoff > oldestRecent) recentPage else {
                        requestedOlderCutoff.set(cutoff)
                        olderReads.incrementAndGet()
                        if (cutoff == oldestRecent) requireNotNull(olderPage) else "[]"
                    }
                }
                else if (historyPruned.get() || live) "[]"
                else JsonArray().apply {
                    add(JsonObject().apply { addProperty("tx", reference.get("archiveEnvelope").asString) })
                    if (storagePartial) add(JsonObject().apply { addProperty("tx", vectors[2].asJsonObject.get("archiveEnvelope").asString) })
                }.toString()
            } else "[]"
        })
        peer.disconnectAfterTransaction = interruptedSend || retrying
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
            if (recentPage != null) {
                val cached = PrivateChat.create(recipientWallet, wallet.publicKey, cachedOlderText, oldestRecent - 100)
                nl.davidengelen.redsquare.chat.ChatHistoryStore(context, wallet, SaitoBackend.OWNER, counterpart)
                    .save(listOf(cached.envelope()))
            }
            if (partialKeys) {
                val old = vectors[2].asJsonObject // Independently pinned exchanged-key message in the same DM.
                nl.davidengelen.redsquare.chat.ChatHistoryStore(context, requireNotNull(completeKeyWallet),
                    SaitoBackend.OWNER, counterpart).save(listOf(reference.get("archiveEnvelope").asString, old.get("archiveEnvelope").asString))
                historyUnavailable.set(true) // Imported active wallet has only its identity keys; cache stays on device.
            }
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            if (retrying || pendingImport) {
                // A known independent original models an interrupted, already durable attempt; opening must not transmit it.
                val original = SaitoTransaction.restore(vectors[1].asJsonObject.get("networkBase64").asString)
                nl.davidengelen.redsquare.chat.ChatWorkStore(context, wallet, SaitoBackend.OWNER, counterpart).prepare(original)
            }
            val policy = when (case) {
                HistoryCase.MUTED -> nl.davidengelen.redsquare.moderation.PersonalPolicy(muted = setOf(counterpart))
                HistoryCase.BLOCKED -> nl.davidengelen.redsquare.moderation.PersonalPolicy(blocked = setOf(counterpart))
                else -> null
            }
            if (policy != null) nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey)
                .save(nl.davidengelen.redsquare.moderation.PersonalPolicy(), policy)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen").apply {
                        if (pendingImport) putExtra("balanceApiUrl", "http://127.0.0.1:9/balance/")
                    }).use { scenario ->
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
                        val text = if (unreadableRecent) cachedOlderText
                            else if (case == HistoryCase.QUOTED_REPLY) fixture.getAsJsonObject("replyFormat").get("body").asString
                            else reference.getAsJsonObject("plaintext").get("message").asString
                        if (case == HistoryCase.BLOCKED) {
                            waitFor(context.getString(R.string.chat_no_messages))
                            ui.onAllNodesWithText(text).assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/blocked-private-history.png"),
                                context.getString(R.string.chat_no_messages))
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            ui.onAllNodesWithText(text).assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/blocked-private-history-recreated.png"),
                                context.getString(R.string.chat_load_error))
                            nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context, wallet.publicKey)
                                .save(requireNotNull(policy), nl.davidengelen.redsquare.moderation.PersonalPolicy())
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(text)
                            ui.onNodeWithText(text).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/unblocked-retained-private-history.png"), text)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (live && !listLive) {
                            waitFor("No available messages in this conversation.")
                            ui.onNodeWithText("Refresh").assertIsEnabled()
                            peer.sendIncoming(requireNotNull(liveTransaction))
                        }
                        waitFor(text)
                        ui.onNodeWithText(text).assertIsDisplayed()
                        if (replyCase) {
                            waitForEnabled("Refresh")
                            if (case == HistoryCase.QUOTED_REPLY) ui.onNodeWithText(
                                fixture.getAsJsonObject("replyFormat").get("quotedText").asString).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir,
                                "private-chat-evidence/${if (case == HistoryCase.QUOTED_REPLY) "web-quoted-private-reply" else "literal-private-markup"}.png"), text)
                            if (case == HistoryCase.QUOTED_REPLY) {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                var originalClip: android.content.ClipData? = null
                                var copyFailure: Throwable? = null
                                ui.runOnIdle { originalClip = clipboard.primaryClip }
                                try {
                                    ui.onNodeWithText("Copy").assertIsDisplayed().performClick()
                                    ui.runOnIdle {
                                        val copied = checkNotNull(clipboard.primaryClip)
                                        check(copied.itemCount == 1 && copied.getItemAt(0).text.toString() == text)
                                        check(copied.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
                                    }
                                    captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reply-body-copied.png"), text)
                                } catch (failure: Throwable) { copyFailure = failure; throw failure }
                                finally {
                                    cleanupAfterTest(copyFailure, {
                                        ui.runOnIdle {
                                            originalClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
                                        }
                                    })
                                }
                            }
                        }
                        if (paginating) {
                            check(olderReads.get() == 0)
                            if (unreadableRecent) {
                                waitFor(context.getString(R.string.chat_incomplete))
                                check(nl.davidengelen.redsquare.chat.ChatHistoryStore(context, wallet, SaitoBackend.OWNER, counterpart)
                                    .load().entries.any { it.message.text == cachedOlderText })
                            }
                            waitForEnabled("Load older messages")
                            ui.onNodeWithText("Load older messages").assertIsEnabled().performClick()
                            waitFor(olderText)
                            ui.onNodeWithText(olderText).performScrollTo().assertIsDisplayed()
                            ui.waitUntil(15_000) {
                                ui.onAllNodesWithText("Load older messages").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                            }
                            check(olderReads.get() == 1)
                            check(requestedOlderCutoff.get() == oldestRecent)
                            val capturePrefix = if (unreadableRecent) "unreadable-recent-private-history" else "older-private-history"
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-loaded.png"), olderText)
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(olderText)
                            ui.onNodeWithText(olderText).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(cachedOlderText).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-cached.png"), olderText)
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (case == HistoryCase.DRAFT_NAVIGATION) {
                            val draft = "Unsent private draft 😀 <&>\nsecond line"
                            waitForEnabled("Message")
                            ui.onNodeWithText("Message").performTextInput(draft)
                            ui.onNodeWithText("Back").performClick()
                            waitFor("Public key")
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitForEnabled("Message")
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString(draft)))
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/unsent-private-draft-reopened.png"), draft)
                            ui.onNode(hasSetTextAction()).performTextClearance()
                            ui.onNodeWithText("Back").performClick()
                            waitFor("Public key")
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitForEnabled("Message")
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString("")))
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitForEnabled("Message")
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString("")))
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/erased-private-draft-recreated.png"),
                                context.getString(R.string.chat_load_error))
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (pendingImport) {
                            waitFor(context.getString(R.string.chat_send_unknown))
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            ui.onNodeWithText("Wallet").performClick()
                            waitFor("Import wallet")
                            ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                            chooseWalletImport(ui, "00-chat-work-import-fixture.json", counterpart)
                            waitFor(context.getString(R.string.wallet_import_pending_work))
                            ui.onNodeWithText(context.getString(R.string.wallet_import_pending_work)).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/chat-work-wallet-replacement.png"),
                                context.getString(R.string.wallet_import_pending_work))
                            ui.onNodeWithText("Done").performClick()
                            waitFor(context.getString(R.string.chat_send_unknown))
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            val retained = nl.davidengelen.redsquare.chat.ChatWorkStore(context, wallet, SaitoBackend.OWNER, counterpart).load()
                            check(retained.status == nl.davidengelen.redsquare.chat.ChatWorkStore.Status.UNKNOWN)
                            check(retained.transaction?.encoded() == vectors[1].asJsonObject.get("networkBase64").asString)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (retrying) {
                            waitFor(context.getString(R.string.chat_send_unknown))
                            waitFor(outgoingText)
                            ui.onNodeWithText(outgoingText).performScrollTo().assertIsDisplayed()
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/unknown-original-restored.png"),
                                context.getString(R.string.chat_send_unknown))
                            waitForEnabled("Retry")
                            ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                            waitFor(context.getString(R.string.chat_send_error))
                            waitFor(context.getString(R.string.chat_send_submitted))
                            check(peer.transactions.size == 1 && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/explicit-retry-interrupted.png"),
                                context.getString(R.string.chat_send_submitted))
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(context.getString(R.string.chat_send_submitted))
                            check(peer.transactions.size == 1 && peer.relayedMessages.isEmpty())
                            peer.disconnectAfterTransaction = false
                            waitForEnabled("Retry")
                            ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                            waitFor(context.getString(R.string.chat_send_forwarded))
                            ui.waitUntil(5_000) { peer.transactions.size == 2 && peer.relayedMessages.size == 1 }
                            val expected = vectors[1].asJsonObject
                            // Only the newly added routing hop is removed. Every original signed byte must match.
                            peer.transactions.forEach { frame ->
                                check(postingTransaction(frame).encoded() == expected.get("networkBase64").asString)
                            }
                            check(peer.relayedMessages.single().get("signature").asString == expected.get("signature").asString)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(context.getString(R.string.chat_send_forwarded))
                            ui.onNodeWithText(outgoingText).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/explicit-original-retry-recreated.png"), outgoingText)
                            check(peer.transactions.size == 2 && peer.relayedMessages.size == 1)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (partialKeys || storagePartial) {
                            waitFor(context.getString(R.string.chat_incomplete))
                            ui.onNodeWithText(context.getString(R.string.chat_incomplete)).assertIsDisplayed()
                            if (partialKeys) ui.onAllNodesWithText(text).assertCountEquals(1)
                        }
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/imported-history.png"), text)
                        if (mentioned || case == HistoryCase.MUTED) captureNativeScreen(File(context.cacheDir,
                            "private-chat-evidence/${if (case == HistoryCase.MUTED) "muted" else if (case == HistoryCase.MENTIONED) "mentioned" else "own-mentioned"}-private-history.png"), text)
                        if (otherLive) {
                            ui.onNodeWithText("Refresh").assertIsEnabled()
                            peer.sendIncoming(requireNotNull(otherPacket))
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            ui.onAllNodesWithText(otherText).assertCountEquals(0)
                            ui.onNodeWithText("Back").performClick()
                            waitFor(otherWallet.publicKey)
                            ui.onNodeWithText(otherWallet.publicKey).performClick()
                            waitFor(otherText)
                            ui.onNodeWithText(otherText).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/other-private-contact.png"), otherText)
                            ui.onNodeWithText("Back").performClick()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(text)
                        }
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
                            if (storagePartial) ui.onNodeWithText(context.getString(R.string.chat_incomplete)).assertIsDisplayed()
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
                        if (replyCase) {
                            if (case == HistoryCase.QUOTED_REPLY) ui.onNodeWithText(
                                fixture.getAsJsonObject("replyFormat").get("quotedText").asString).assertIsDisplayed()
                            val retained = nl.davidengelen.redsquare.chat.ChatHistoryStore(context, wallet, SaitoBackend.OWNER, counterpart)
                                .load().entries.single { it.message.id == reference.get("signature").asString }
                            check(retained.envelope == reference.get("archiveEnvelope").asString)
                            check(retained.message.text == reference.getAsJsonObject("plaintext").get("message").asString)
                            captureNativeScreen(File(context.cacheDir,
                                "private-chat-evidence/${if (case == HistoryCase.QUOTED_REPLY) "web-quoted-private-reply" else "literal-private-markup"}-recreated.png"), text)
                        }
                        if (mentioned || case == HistoryCase.MUTED) captureNativeScreen(File(context.cacheDir,
                            "private-chat-evidence/${if (case == HistoryCase.MUTED) "muted" else if (case == HistoryCase.MENTIONED) "mentioned" else "own-mentioned"}-private-history-recreated.png"), text)
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
                        if (otherLive) {
                            ui.onNodeWithText("Back").performClick()
                            waitFor(otherWallet.publicKey)
                            ui.onNodeWithText(otherWallet.publicKey).performClick()
                            waitFor(otherText)
                            ui.onNodeWithText(otherText).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/other-private-contact-recreated.png"), otherText)
                        }
                        if (partialKeys) {
                            // Restore the matching imported secret through protected storage, then verify the old ciphertext survived saving.
                            store.replace(store.loadOrCreate(), requireNotNull(completeKeyWallet))
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                            val oldText = vectors[2].asJsonObject.getAsJsonObject("plaintext").get("message").asString
                            waitFor(oldText)
                            ui.onAllNodesWithText(oldText).assertCountEquals(2) // Independent messages intentionally share their plaintext, with different signed ciphertexts.
                            ui.onAllNodesWithText(oldText)[1].performScrollTo().assertIsDisplayed()
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

    private fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

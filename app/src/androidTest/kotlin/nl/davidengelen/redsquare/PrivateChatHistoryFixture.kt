package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.File

internal class PrivateChatHistoryFixture(private val ui: ComposeTestRule) {
    enum class HistoryCase { AVAILABLE, STORAGE_FAILURE, LIVE, SEND, SEND_INTERRUPTED, NEW_SEND, IMPORTED_ID, IMPORTED_PARTIAL, LIST_LIVE, PARTIAL_KEYS, STORAGE_PARTIAL, OTHER_LIVE, MENTIONED, MENTIONED_OUTGOING, MUTED, BLOCKED, UNKNOWN_RETRY, PENDING_IMPORT, DRAFT_NAVIGATION, OLDER_HISTORY, QUOTED_REPLY, LITERAL_MARKUP, UNREADABLE_RECENT_HISTORY, NATIVE_REPLY, NATIVE_REPLY_ZERO, LIKE, INCOMING_LIKE, CONCURRENT_LIKE, REACTION_RETRY, REACTION_BACKENDS, REACTION_WALLETS, ARCHIVED_LIKE, ARCHIVED_LIKE_STORAGE }

    fun exerciseHistory(case: HistoryCase) {
        fun captureViewport(stage: String) {
            if (InstrumentationRegistry.getArguments().getString("privateHistoryViewport") != "1") return
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            val prefix = "private-chat-evidence/history-${case.name.lowercase()}-$stage"
            captureNativeScreen(File(target.cacheDir, "$prefix.png"))
            File(target.cacheDir, "$prefix.semantics.txt").writeText(
                ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
        }
        val concurrentLike = case == HistoryCase.CONCURRENT_LIKE
        val reactionStorage = case == HistoryCase.ARCHIVED_LIKE_STORAGE
        val archivedLike = case == HistoryCase.ARCHIVED_LIKE || reactionStorage
        val reactionWallets = case == HistoryCase.REACTION_WALLETS
        val reactionBackends = case == HistoryCase.REACTION_BACKENDS
        val reactionRetry = case == HistoryCase.REACTION_RETRY
        val liking = case == HistoryCase.LIKE || concurrentLike || reactionRetry || reactionBackends || reactionWallets
        val incomingLike = case == HistoryCase.INCOMING_LIKE
        val zeroReply = case == HistoryCase.NATIVE_REPLY_ZERO
        val nativeReply = case == HistoryCase.NATIVE_REPLY || zeroReply
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
        val sending = case == HistoryCase.SEND || interruptedSend || newContact || partialKeys || retrying || nativeReply
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val mentioned = case == HistoryCase.MENTIONED || case == HistoryCase.MENTIONED_OUTGOING
        val replyCase = case == HistoryCase.QUOTED_REPLY || case == HistoryCase.LITERAL_MARKUP
        val fixture = instrumentation.context.assets.open(when {
            nativeReply -> "native-private-chat-reply-compose-reference.json"
            replyCase -> "native-private-chat-reply-reference.json"
            mentioned -> "native-private-chat-mentioned-reference.json"
            else -> "native-private-chat-reference.json"
        }).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val likeReference = if (liking) instrumentation.context.assets.open("native-private-chat-like-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject } else null
        val incomingLikeReference = if (incomingLike || concurrentLike || archivedLike) instrumentation.context.assets.open("incoming-private-like-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject } else null
        val vectors = fixture.getAsJsonArray("vectors")
        val reference = vectors[when {
            zeroReply -> 6
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
        val alternateWallet = if (reactionWallets) SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
            .withChatContact(counterpart) else null // Public unsafe identity replacement fixture only.
        val fixtureWallets = listOfNotNull(wallet, alternateWallet)
        val prefixes = fixtureWallets.flatMap { identity -> SaitoBackend.entries.flatMap { backend ->
            listOf("chat.${backend.id}.${identity.publicKey}.", "chatwork.${backend.id}.${identity.publicKey}.",
                "chatreactions.${backend.id}.${identity.publicKey}.")
        } + listOf("personal.${identity.publicKey}.", "chatpush.${identity.publicKey}.") }
        val savedFiles = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }.associate { file ->
                check(file.isFile); file.name to file.readBytes()
            }
        }
        val historyReads = java.util.concurrent.atomic.AtomicInteger()
        val historyUnavailable = java.util.concurrent.atomic.AtomicBoolean()
        val historyPruned = java.util.concurrent.atomic.AtomicBoolean()
        val reactionRowsAvailable = java.util.concurrent.atomic.AtomicBoolean(true)
        val cacheFile = File(context.noBackupFilesDir, "chat.${SaitoBackend.OWNER.id}.${wallet.publicKey}.${reference.get("groupId").asString}.v1")
        val reactionCacheFile = File(context.noBackupFilesDir,
            "chatreactions.${SaitoBackend.OWNER.id}.${wallet.publicKey}.${reference.get("groupId").asString}.v1")
        val composition = if (nativeReply) fixture.getAsJsonObject("replyFormat").getAsJsonObject(if (zeroReply) "compositionZero" else "composition") else null
        val outgoingText = if (nativeReply) requireNotNull(composition).get("prefix").asString + composition.get("body").asString
            else if (retrying) vectors[1].asJsonObject.getAsJsonObject("plaintext").get("message").asString
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
        val alternateText = "Private history for the replacement identity"
        val alternateMessage = alternateWallet?.let { PrivateChat.create(recipientWallet, it.publicKey,
            alternateText, 1700000090000L) }
        val reactionRelayEntered = java.util.concurrent.CountDownLatch(1)
        val reactionRelayRelease = java.util.concurrent.CountDownLatch(1)
        val delayedReaction = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = { original ->
            if (liking) {
                val signed = SaitoEnvelope.decodePayload(SaitoEnvelope.fromSignedJson(original))
                check(signed.author == wallet.publicKey)
                check(signed.recipients.containsAll(listOf(wallet.publicKey, counterpart)))
                check(signed.body == requireNotNull(likeReference).getAsJsonArray("vectors")[0].asJsonObject.get("body"))
                if (concurrentLike && delayedReaction.compareAndSet(false, true)) {
                    reactionRelayEntered.countDown()
                    check(reactionRelayRelease.await(15, java.util.concurrent.TimeUnit.SECONDS))
                }
            } else {
                check(sending)
                val received = PrivateChat.read(recipientWallet, original, wallet.publicKey)
                check(received.author == wallet.publicKey && received.text == outgoingText)
            }
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
                    if (archivedLike && reactionRowsAvailable.get()) repeat(2) {
                        add(JsonObject().apply { addProperty("tx", requireNotNull(incomingLikeReference)
                            .getAsJsonArray("vectors")[0].asJsonObject.get("archiveEnvelope").asString) })
                    } // An available source can return the same original more than once.
                    if (storagePartial) add(JsonObject().apply { addProperty("tx", vectors[2].asJsonObject.get("archiveEnvelope").asString) })
                }.toString()
            } else if (alternateMessage != null && criteria.get("field3")?.asString ==
                PrivateChat.conversationId(requireNotNull(alternateWallet).publicKey, counterpart)) {
                JsonArray().apply { add(JsonObject().apply { addProperty("tx", alternateMessage.envelope()) }) }.toString()
            } else "[]"
        })
        peer.disconnectAfterTransaction = interruptedSend || retrying || reactionRetry
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
            if (reactionStorage) check(reactionCacheFile.mkdir())
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
            if (reactionRetry || reactionBackends || reactionWallets) {
                val original = SaitoTransaction.restore(requireNotNull(likeReference).getAsJsonArray("vectors")[0]
                    .asJsonObject.get("networkBase64").asString)
                nl.davidengelen.redsquare.chat.ChatReactionStore(context, wallet, SaitoBackend.OWNER, counterpart).prepare(original)
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
                        if (reactionBackends) putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    }).use { scenario ->
                    withFailureCapture({
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/history-${case.name.lowercase()}-failure.png"))
                        captureViewport("failure-layout")
                    }) {
                        waitForEnabled("Chat")
                        ui.onNodeWithText("Chat").assertIsDisplayed().assertIsEnabled().performClick()
                        if (newContact) {
                            waitFor("Public key")
                            ui.onNodeWithText("Public key").performTextInput(counterpart)
                            clickAfterTyping("Open chat")
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
                        captureViewport("before-initial-history-scroll")
                        waitForMessage(text)
                        if (storagePartial) ui.waitUntil(15_000) {
                            runCatching { ui.onNodeWithContentDescription("Private messages").assertHeightIsAtLeast(48.dp) }.isSuccess
                        }
                        ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                        if (storagePartial) captureViewport("after-initial-history-scroll")
                        if (reactionStorage) {
                            val warning = "Could not restore or save reactions on this device. Messages remain readable."
                            waitFor(warning)
                            waitForEnabled("Refresh")
                            ui.onNodeWithText(text).assertIsDisplayed()
                            ui.onNodeWithText(warning).assertIsDisplayed()
                            ui.onAllNodesWithText(context.getString(R.string.chat_load_error)).assertCountEquals(0)
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/readable-message-reaction-storage-failure.png"), text)
                            check(reactionCacheFile.isDirectory && reactionCacheFile.list()!!.isEmpty() && reactionCacheFile.delete())
                            val readsBeforeRepair = historyReads.get()
                            ui.onNodeWithText("Refresh").performClick()
                            ui.waitUntil(15_000) { historyReads.get() > readsBeforeRepair &&
                                ui.onAllNodesWithText(warning).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                            waitFor("1 like")
                            waitForEnabled("Like")
                            ui.onNodeWithText("1 like").assertIsDisplayed()
                            ui.onAllNodesWithText(context.getString(R.string.chat_reaction_error)).assertCountEquals(0)
                            ui.onAllNodesWithText(context.getString(R.string.chat_load_error)).assertCountEquals(0)
                            check(store.loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/readable-message-reaction-storage-repaired.png"), text)
                            // Keep opaque encrypted fixture bytes on device while the available source returns messages only.
                            val savedReaction = reactionCacheFile.readBytes()
                            check(reactionCacheFile.delete() && reactionCacheFile.mkdir())
                            reactionRowsAvailable.set(false)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(text)
                            waitFor(context.getString(R.string.chat_reaction_error))
                            ui.onNodeWithText(text).assertIsDisplayed()
                            ui.onNodeWithText("Like").assertIsNotEnabled()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/message-only-reaction-restore-failure.png"), text)
                            check(reactionCacheFile.list()!!.isEmpty() && reactionCacheFile.delete())
                            reactionCacheFile.writeBytes(savedReaction)
                            val messageOnlyReads = historyReads.get()
                            waitForEnabled("Refresh")
                            ui.onNodeWithText("Refresh").performClick()
                            ui.waitUntil(15_000) { historyReads.get() > messageOnlyReads &&
                                ui.onAllNodesWithText(context.getString(R.string.chat_reaction_error))
                                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                            waitForMessage("1 like")
                            waitForEnabled("Like")
                            ui.onNodeWithText("1 like").assertIsDisplayed()
                            ui.onAllNodesWithText(warning).assertCountEquals(0)
                            ui.onAllNodesWithText(context.getString(R.string.chat_load_error)).assertCountEquals(0)
                            check(store.loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/message-only-reaction-restore-repaired.png"), text)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (archivedLike) {
                            waitFor("1 like")
                            waitForEnabled("Like")
                            ui.onNodeWithText("1 like").assertIsDisplayed()
                            ui.onAllNodesWithText(context.getString(R.string.chat_incomplete)).assertCountEquals(0)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/available-private-reaction.png"), text)
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor("1 like")
                            waitForEnabled("Like")
                            ui.onNodeWithText(text).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/available-private-reaction-cached.png"), text)
                            historyUnavailable.set(false)
                            val readsBeforeRefresh = historyReads.get()
                            ui.onNodeWithText("Refresh").assertIsEnabled().performClick()
                            ui.waitUntil(15_000) { historyReads.get() > readsBeforeRefresh &&
                                ui.onAllNodesWithText(context.getString(R.string.chat_load_error))
                                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                            waitForEnabled("Refresh")
                            ui.onAllNodesWithText(context.getString(R.string.chat_load_error)).assertCountEquals(0)
                            ui.onNodeWithText("1 like").assertIsDisplayed()
                            ui.onAllNodesWithText(context.getString(R.string.chat_incomplete)).assertCountEquals(0)
                            check(store.loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/available-private-reaction-refreshed.png"), text)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (reactionWallets) {
                            val expected = requireNotNull(likeReference).getAsJsonArray("vectors")[0].asJsonObject
                            waitFor(context.getString(R.string.chat_reaction_unknown))
                            waitFor("1 like")
                            waitForEnabled("Retry reaction")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-original-wallet.png"), text)
                            fun returnToFeed() {
                                ui.onNodeWithText("Back").performClick()
                                waitFor("Public key")
                                ui.onNodeWithText("Back").performClick()
                                waitForEnabled("Chat")
                            }
                            // Identity replacement is the protected precondition; the existing SAF tests cover approval safeguards.
                            returnToFeed()
                            store.replace(store.loadOrCreate(), requireNotNull(alternateWallet))
                            scenario.recreate()
                            waitForEnabled("Chat")
                            ui.onNodeWithText("Chat").performClick()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(alternateText)
                            waitForEnabled("Like")
                            ui.onNodeWithText(alternateText).assertIsDisplayed()
                            ui.onAllNodesWithText(text).assertCountEquals(0)
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            ui.onAllNodesWithText("Retry reaction").assertCountEquals(0)
                            ui.onAllNodesWithText(context.getString(R.string.chat_reaction_unknown)).assertCountEquals(0)
                            check(store.loadOrCreate().exportJson() == alternateWallet.exportJson())
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-replacement-wallet.png"), alternateText)
                            returnToFeed()
                            store.replace(store.loadOrCreate(), wallet)
                            scenario.recreate()
                            waitForEnabled("Chat")
                            ui.onNodeWithText("Chat").performClick()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(text)
                            waitFor(context.getString(R.string.chat_reaction_unknown))
                            waitFor("1 like")
                            waitForEnabled("Retry reaction")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            ui.onAllNodesWithText(alternateText).assertCountEquals(0)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            ui.onNodeWithText("Retry reaction").performClick()
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            ui.waitUntil(5_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                            val original = postingTransaction(peer.transactions.single())
                            check(original.encoded() == expected.get("networkBase64").asString)
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == original.envelope())
                            check(store.loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.reports.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-original-wallet-returned.png"), text)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (reactionBackends) {
                            // Both logical servers expose the same independent original. Only the selected namespace differs.
                            val expected = requireNotNull(likeReference).getAsJsonArray("vectors")[0].asJsonObject
                            fun chooseBackend(backend: SaitoBackend) {
                                ui.onNodeWithText("Back").performClick()
                                waitFor("Public key")
                                ui.onNodeWithText("Back").performClick()
                                waitFor("Settings")
                                ui.onNodeWithText("Settings").performClick()
                                waitFor(backend.domain)
                                ui.onNodeWithText(backend.domain).performScrollTo().performClick()
                                waitFor("Node: ${backend.domain}")
                                check(AndroidBackendStore(context).load() == backend)
                                ui.onNodeWithText("Chat").performClick()
                                waitFor(counterpart)
                                ui.onNodeWithText(counterpart).performClick()
                                waitFor(text)
                                ui.onNodeWithText(text).assertIsDisplayed()
                            }
                            waitFor(context.getString(R.string.chat_reaction_unknown))
                            waitFor("1 like")
                            waitForEnabled("Retry reaction")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-owner-uncertain.png"), text)
                            chooseBackend(SaitoBackend.OFFICIAL)
                            waitForEnabled("Like")
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            ui.onAllNodesWithText("Retry reaction").assertCountEquals(0)
                            ui.onAllNodesWithText(context.getString(R.string.chat_reaction_unknown)).assertCountEquals(0)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-official-separate.png"), text)
                            ui.onNodeWithText("Like").performScrollTo().assertIsDisplayed().performClick()
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            waitFor("1 like")
                            ui.waitUntil(5_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                            val official = postingTransaction(peer.transactions.single())
                            check(official.id != expected.get("signature").asString)
                            check(SaitoEnvelope.decodePayload(official.envelope()).body == expected.get("body"))
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == official.envelope())
                            chooseBackend(SaitoBackend.OWNER)
                            waitFor(context.getString(R.string.chat_reaction_unknown))
                            waitFor("1 like")
                            waitForEnabled("Retry reaction")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            check(peer.transactions.size == 1 && peer.relayedMessages.size == 1)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-owner-returned.png"), text)
                            ui.onNodeWithText("Retry reaction").performClick()
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            ui.waitUntil(5_000) { peer.transactions.size == 2 && peer.relayedMessages.size == 2 }
                            val owner = postingTransaction(peer.transactions.last())
                            check(owner.encoded() == expected.get("networkBase64").asString)
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.last()) == owner.envelope())
                            chooseBackend(SaitoBackend.OFFICIAL)
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            ui.onAllNodesWithText("Retry reaction").assertCountEquals(0)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(text)
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/private-reaction-official-recreated.png"), text)
                            check(store.loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OFFICIAL)
                            check(peer.transactions.size == 2 && peer.relayedMessages.size == 2 && peer.reports.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (reactionRetry) {
                            val expected = requireNotNull(likeReference).getAsJsonArray("vectors")[0].asJsonObject
                            waitFor(context.getString(R.string.chat_reaction_unknown))
                            waitFor("1 like")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/unknown-private-reaction-restored.png"), text)
                            ui.onNodeWithText("Retry reaction").assertIsEnabled().performClick()
                            waitFor(context.getString(R.string.chat_reaction_error))
                            waitFor(context.getString(R.string.chat_reaction_submitted))
                            ui.waitUntil(5_000) { peer.transactions.size == 1 }
                            check(peer.relayedMessages.isEmpty())
                            check(postingTransaction(peer.transactions.single()).encoded() == expected.get("networkBase64").asString)
                            ui.onNodeWithContentDescription("Private messages").assertHeightIsAtLeast(48.dp)
                            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/explicit-private-reaction-retry-interrupted.png"), text)
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(context.getString(R.string.chat_reaction_submitted))
                            waitFor("1 like")
                            ui.onNodeWithText("Unlike").assertIsNotEnabled()
                            check(peer.transactions.size == 1 && peer.relayedMessages.isEmpty())
                            peer.disconnectAfterTransaction = false
                            waitForEnabled("Retry reaction")
                            ui.onNodeWithText("Retry reaction").assertIsEnabled().performClick()
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            ui.waitUntil(5_000) { peer.transactions.size == 2 && peer.relayedMessages.size == 1 }
                            peer.transactions.forEach { frame ->
                                check(postingTransaction(frame).encoded() == expected.get("networkBase64").asString)
                            }
                            val original = postingTransaction(peer.transactions.last())
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == original.envelope())
                            File(context.cacheDir, "private-chat-evidence/native-sent-retried-like-envelope.json").writeText(original.envelope())
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(context.getString(R.string.chat_reaction_forwarded))
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/explicit-private-reaction-retry-recreated.png"), text)
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.size == 2 && peer.relayedMessages.size == 1 && peer.reports.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (incomingLike) {
                            val operations = requireNotNull(incomingLikeReference).getAsJsonArray("vectors")
                            fun packet(index: Int): ByteArray {
                                val relay = JsonObject().apply {
                                    addProperty("module", "Relay"); addProperty("request", "chat relay")
                                    add("data", operations[index].asJsonObject.getAsJsonObject("signedInner"))
                                }
                                return SaitoWire.applicationRequest(counterpart, relay.toString().toByteArray(Charsets.UTF_8),
                                    1, 1700000080100L + index).drop(5).toByteArray()
                            }
                            waitForEnabled("Refresh")
                            waitForEnabled("Like")
                            peer.sendIncoming(packet(0))
                            waitFor("1 like")
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Like").assertIsEnabled() // The contact's like does not select our Like.
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/incoming-private-like.png"), text)
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor("1 like")
                            waitForEnabled("Refresh")
                            val acknowledgements = peer.incomingAcknowledgements.size
                            peer.sendIncoming(packet(0))
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.size > acknowledgements }
                            check(peer.incomingAcknowledgements.last()[0] == 13.toByte())
                            ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/incoming-private-like-duplicate.png"), text)
                            peer.sendIncoming(packet(1))
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.size > acknowledgements + 1 }
                            check(peer.incomingAcknowledgements.last()[0] == 13.toByte())
                            ui.waitUntil(5_000) {
                                ui.onAllNodesWithText("1 like").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                            }
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            peer.sendIncoming(packet(1))
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.size > acknowledgements + 2 }
                            check(peer.incomingAcknowledgements.last()[0] == 13.toByte())
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/incoming-private-unlike-duplicate.png"), text)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitForEnabled("Like")
                            ui.onAllNodesWithText("1 like").assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/incoming-private-unlike-recreated.png"), text)
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (liking) {
                            val capturePrefix = if (concurrentLike) "private-concurrent-reaction" else "private-message"
                            val envelopePrefix = if (concurrentLike) "native-sent-concurrent" else "native-sent"
                            val initialCount = if (concurrentLike) "2 likes" else "1 like"
                            waitForEnabled("Message")
                            waitForEnabled("Like")
                            ui.onNodeWithText("Like").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                            if (concurrentLike) {
                                ui.waitUntil(5_000) { reactionRelayEntered.count == 0L }
                                waitFor(context.getString(R.string.chat_reaction_submitted))
                                val relay = JsonObject().apply {
                                    addProperty("module", "Relay"); addProperty("request", "chat relay")
                                    add("data", requireNotNull(incomingLikeReference).getAsJsonArray("vectors")[0]
                                        .asJsonObject.getAsJsonObject("signedInner"))
                                }
                                peer.sendIncoming(SaitoWire.applicationRequest(counterpart, relay.toString().toByteArray(Charsets.UTF_8),
                                    1, 1700000080100L).drop(5).toByteArray())
                                waitFor(initialCount)
                                ui.waitUntil(5_000) { peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                                ui.onNodeWithText(context.getString(R.string.chat_reaction_submitted)).assertIsDisplayed()
                                captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-submitted.png"), text)
                                reactionRelayRelease.countDown()
                            }
                            waitFor("Reaction forwarded. Delivery is not confirmed.")
                            waitFor(initialCount)
                            ui.onNodeWithText(initialCount).performScrollTo().assertIsDisplayed()
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("Unlike").assertIsEnabled()
                            ui.waitUntil(5_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                            val original = postingTransaction(peer.transactions.single())
                            val signed = SaitoEnvelope.decodePayload(original.envelope())
                            check(signed.author == wallet.publicKey && signed.recipients.containsAll(listOf(wallet.publicKey, counterpart)))
                            check(signed.body == requireNotNull(likeReference).getAsJsonArray("vectors")[0].asJsonObject.get("body"))
                            check(peer.relayedMessages.single().get("signature").asString == signed.id)
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == original.envelope())
                            File(context.cacheDir, "private-chat-evidence/$envelopePrefix-like-envelope.json").writeText(original.envelope())
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-liked.png"), text)
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitFor(initialCount)
                            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(initialCount).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText("Unlike").assertIsEnabled()
                            waitFor("Reaction forwarded. Delivery is not confirmed.")
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-like-recreated.png"), text)
                            waitForEnabled("Unlike")
                            ui.onNodeWithText("Unlike").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                            waitForEnabled("Like")
                            if (concurrentLike) {
                                waitFor("1 like")
                                ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            }
                            ui.onAllNodesWithText(initialCount).assertCountEquals(0)
                            ui.waitUntil(5_000) { peer.transactions.size == 2 && peer.relayedMessages.size == 2 }
                            val unlike = postingTransaction(peer.transactions[1])
                            val signedUnlike = SaitoEnvelope.decodePayload(unlike.envelope())
                            check(unlike.id != original.id)
                            check(signedUnlike.body == signed.body && signedUnlike.author == signed.author)
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages[1]) == unlike.envelope())
                            File(context.cacheDir, "private-chat-evidence/$envelopePrefix-unlike-envelope.json").writeText(unlike.envelope())
                            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-unliked.png"), text)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            waitForEnabled("Like")
                            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            if (concurrentLike) {
                                waitFor("1 like")
                                ui.onNodeWithText("1 like").performScrollTo().assertIsDisplayed()
                            }
                            ui.onAllNodesWithText(initialCount).assertCountEquals(0)
                            ui.onAllNodesWithText("Unlike").assertCountEquals(0)
                            waitFor("Reaction forwarded. Delivery is not confirmed.")
                            ui.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-unlike-recreated.png"), text)
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.size == 2 && peer.relayedMessages.size == 2 && peer.reports.isEmpty())
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if (nativeReply) {
                            val expected = requireNotNull(composition)
                            val body = expected.get("body").asString
                            val capturePrefix = if (zeroReply) "native-zero-reply" else "native-reply"
                            waitForEnabled("Message")
                            ui.onNodeWithText("Reply").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                            ui.onNodeWithText("Send").assertIsNotEnabled()
                            ui.onNodeWithText("Message").performTextInput(body)
                            clickAfterTyping("Back")
                            waitFor("Public key")
                            ui.onNodeWithText(counterpart).performClick()
                            waitForEnabled("Message")
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString(body)))
                            ui.onNodeWithText(expected.get("quotedText").asString).assertIsDisplayed()
                            ui.onNodeWithText("Remove reply").assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-draft-reopened.png"), body)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            ui.onNodeWithText("Remove reply").performClick()
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString(body)))
                            ui.onAllNodesWithText("Remove reply").assertCountEquals(0)
                            ui.onNodeWithText("Reply").performScrollTo().assertIsDisplayed().performClick()
                            waitFor("Remove reply")
                            ui.onNodeWithText(expected.get("quotedText").asString).assertIsDisplayed()
                            clickAfterTyping("Send")
                            ui.waitUntil(15_000) {
                                peer.assertHealthy()
                                ui.onAllNodesWithText(context.getString(R.string.chat_send_forwarded))
                                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                            }
                            ui.waitUntil(5_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                            val received = PrivateChat.receive(recipientWallet, peer.transactions.single().drop(1).toByteArray())
                            check(received.message.text == outgoingText && received.message.author == wallet.publicKey)
                            check(received.message.id == peer.relayedMessages.single().get("signature").asString)
                            File(context.cacheDir, "private-chat-evidence/${if (zeroReply) "native-sent-zero-reply-envelope" else "native-sent-reply-envelope"}.json").writeText(received.envelope)
                            // A public native-generated spacer makes the quoted original genuinely offscreen.
                            // Independent reply crypto/metadata remains anchored to the original web corpus.
                            waitForEnabled("Refresh")
                            val spacerText = (1..24).joinToString("\n") { "Public layout spacer line $it" }
                            val spacer = PrivateChat.create(recipientWallet, wallet.publicKey,
                                spacerText, received.message.createdAt - 1)
                            val spacerRelay = JsonObject().apply {
                                addProperty("module", "Relay"); addProperty("request", "chat relay")
                                add("data", spacer.signedJson())
                            }
                            peer.sendIncoming(SaitoWire.applicationRequest(recipientWallet.publicKey,
                                spacerRelay.toString().toByteArray(Charsets.UTF_8), 1,
                                received.message.createdAt - 1).drop(5).toByteArray())
                            ui.waitUntil(5_000) { peer.incomingAcknowledgements.any { it[0] == 13.toByte() } }
                            ui.waitUntil(5_000) {
                                ui.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config
                                    .getOrNull(SemanticsProperties.CollectionInfo)?.rowCount == 3
                            }
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(spacerText))
                            ui.onNodeWithText(spacerText).assertIsDisplayed()
                            val sentBody = hasText(body) and !hasSetTextAction()
                            ui.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
                            ui.onNode(sentBody).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-forwarded.png"), body)
                            ui.onNodeWithText(text).assertIsNotDisplayed()
                            captureNativeScreen(File(context.cacheDir,
                                "private-chat-evidence/$capturePrefix-quote-before-tap.png"), body)
                            ui.onNodeWithText(expected.get("quotedText").asString).assertIsDisplayed().performClick()
                            ui.waitUntil(15_000) { ui.onNodeWithText(text).isDisplayed() }
                            ui.onNodeWithText(text).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir,
                                "private-chat-evidence/$capturePrefix-quote-original.png"), text)
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(sentBody)
                            ui.onNode(sentBody).assertIsDisplayed()
                            historyUnavailable.set(true)
                            scenario.recreate()
                            waitFor(counterpart)
                            ui.onNodeWithText(counterpart).performClick()
                            waitFor(context.getString(R.string.chat_load_error))
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(sentBody)
                            ui.onNode(sentBody).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-cached.png"), body)
                            check(AndroidWalletStore(context).loadOrCreate().exportJson() == wallet.exportJson())
                            check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                            check(peer.transactions.size == 1 && peer.relayedMessages.size == 1)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
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
                            captureViewport("before-older-history-wait")
                            waitForMessage(olderText)
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
                            captureViewport("before-reopened-older-history-wait")
                            waitForMessage(olderText)
                            ui.onNodeWithText(olderText).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/$capturePrefix-cached.png"), olderText)
                            waitForMessage(cachedOlderText)
                            ui.onNodeWithText(cachedOlderText).assertIsDisplayed()
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
                            clickAfterTyping("Back")
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
                            waitForMessage(outgoingText)
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
                            waitForStatus(context.getString(R.string.chat_incomplete))
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
                            clickAfterTyping("Send")
                            if (interruptedSend) {
                                waitFor(context.getString(R.string.chat_send_error))
                                waitFor("Submitted. Delivery is not confirmed.")
                                ui.onAllNodesWithText("Forwarded. Delivery is not confirmed.").assertCountEquals(0)
                            } else waitFor("Forwarded. Delivery is not confirmed.")
                            val sentBody = hasText(outgoingText) and !hasSetTextAction()
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(sentBody)
                            ui.onNode(sentBody).assertIsDisplayed()
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
                            waitForStatus(warning)
                            ui.onNodeWithText(warning).assertIsDisplayed()
                            synchronized(SaitoLocalState.lock) { check(cacheFile.isDirectory && cacheFile.list()!!.isEmpty() && cacheFile.delete()) }
                            historyUnavailable.set(true)
                            ui.onNodeWithText("Refresh").assertIsDisplayed().performClick()
                            waitFor("Could not load chats. Check your connection and try Refresh.")
                            waitFor(text)
                            ui.onNodeWithText(text).assertIsDisplayed()
                            if (storagePartial) {
                                waitForStatus(context.getString(R.string.chat_incomplete))
                                ui.onNodeWithText(context.getString(R.string.chat_incomplete)).assertIsDisplayed()
                            }
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
                        captureViewport("before-cached-history-wait")
                        waitForMessage(text)
                        captureViewport("after-cached-history-scroll")
                        ui.onNodeWithContentDescription("Private messages").performScrollToNode(hasText(text) and !hasSetTextAction())
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
                            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(outgoingText) and !hasSetTextAction())
                            ui.onNodeWithText(outgoingText).assertIsDisplayed()
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
                            val restoredMessages = nl.davidengelen.redsquare.chat.ChatHistoryStore(context,
                                requireNotNull(completeKeyWallet), SaitoBackend.OWNER, counterpart).load().entries
                                .filter { it.message.text == oldText }
                            val expectedOriginals = setOf(reference.get("archiveEnvelope").asString,
                                vectors[2].asJsonObject.get("archiveEnvelope").asString)
                            // Two distinct signed messages share their text. A lazy viewport need not compose both at once.
                            check(restoredMessages.size == 2 && restoredMessages.map { it.envelope }.toSet() == expectedOriginals)
                            for ((index, restored) in restoredMessages.withIndex()) {
                                ui.waitUntil(15_000) {
                                    runCatching {
                                        ui.onNodeWithContentDescription("Private messages").performScrollToKey(restored.message.id)
                                        val matches = ui.onAllNodesWithText(oldText)
                                        check(matches.fetchSemanticsNodes(atLeastOneRootRequired = false).indices.any { match ->
                                            runCatching { matches[match].assertIsDisplayed() }.isSuccess
                                        })
                                    }.isSuccess
                                }
                                if (index == 0) captureNativeScreen(File(context.cacheDir,
                                    "private-chat-evidence/exchanged-key-first-restored.png"), oldText)
                            }
                            captureNativeScreen(File(context.cacheDir, "private-chat-evidence/exchanged-key-restored.png"), oldText)
                            check(peer.transactions.size == 1 && peer.relayedMessages.size == 1)
                        }
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            reactionRelayRelease.countDown()
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
                check(current.publicKey in fixtureWallets.map { it.publicKey } || current.publicKey == SaitoWallet.fromBackup(original).publicKey)
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
                        if (file.isDirectory && (file == cacheFile || reactionStorage && file == reactionCacheFile))
                            check(file.list()!!.isEmpty() && file.delete())
                        else check(file.isFile && file.delete())
                    }
                savedFiles.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
            } }
            cleanupFailure?.let { throw it }
        }
    }

    private fun clickAfterTyping(text: String) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.waitUntil(15_000){runCatching{ui.onNodeWithText(text).assertIsDisplayed().assertIsEnabled()}.isSuccess}
        ui.onNodeWithText(text).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun waitForMessage(text: String) = ui.waitUntil(15_000) {
        runCatching {
            ui.onNodeWithContentDescription("Private messages").assertHeightIsAtLeast(48.dp)
                .performScrollToNode(hasText(text) and !hasSetTextAction())
            ui.onNodeWithText(text).assertIsDisplayed()
        }.isSuccess
    }

    private fun waitForStatus(text: String) = ui.waitUntil(15_000) {
        runCatching {
            ui.onNodeWithContentDescription("Chat status").performScrollToNode(hasText(text))
            ui.onNodeWithText(text).assertIsDisplayed()
        }.isSuccess
    }

    fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

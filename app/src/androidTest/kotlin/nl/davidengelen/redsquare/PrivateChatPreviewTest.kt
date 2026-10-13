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

    @Test fun anImportedContactOpensItsAvailableAuthenticatedPrivateHistory() = exerciseHistory(false)

    @Test fun failedLocalSavingDoesNotLoseReadableHistoryOnRefresh() = exerciseHistory(true)

    @Test fun aLivePrivateRelayMessageRemainsReadableAfterReopening() = exerciseHistory(false, live = true)

    private fun exerciseHistory(storageFailure: Boolean, live: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-private-chat-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val counterpart = reference.get("sender").asString
        val publicWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val options = JsonParser.parseString(publicWallet.exportJson()).asJsonObject.apply {
            add("keys", JsonArray().apply { add(JsonObject().apply {
                addProperty("publicKey", counterpart); addProperty("added", true)
            }) })
        }
        val wallet = SaitoWallet.fromBackup(options.toString())
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate().exportJson()
        val priorBackend = AndroidBackendStore(context).load()
        val preferences = listOf("wallet-backup-reminders" to "hidden:${wallet.publicKey}",
            "notification-permission" to "offered").map { (name, key) ->
            val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null)
        }
        val prefixes = listOf("chat.${SaitoBackend.OWNER.id}.${wallet.publicKey}.", "personal.${wallet.publicKey}.")
        val savedFiles = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }.associate { file ->
                check(file.isFile); file.name to file.readBytes()
            }
        }
        val historyReads = java.util.concurrent.atomic.AtomicInteger()
        val historyUnavailable = java.util.concurrent.atomic.AtomicBoolean()
        val historyPruned = java.util.concurrent.atomic.AtomicBoolean()
        val cacheFile = File(context.noBackupFilesDir, "chat.${SaitoBackend.OWNER.id}.${wallet.publicKey}.${reference.get("groupId").asString}.v1")
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (!criteria.has("field1") && criteria.get("field3")?.asString == reference.get("groupId").asString) {
                check(criteria.get("limit").asInt == 50)
                historyReads.incrementAndGet()
                if (historyUnavailable.get()) "invalid archive response"
                else if (historyPruned.get() || live) "[]"
                else JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.get("archiveEnvelope").asString) }) }.toString()
            } else "[]"
        })
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
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "private-chat-evidence/history-first-failure.png")) }) {
                        waitFor("Chat")
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        waitFor(counterpart)
                        ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                        val text = reference.getAsJsonObject("plaintext").get("message").asString
                        if (live) {
                            waitFor("No available messages in this conversation.")
                            ui.onNodeWithText("Refresh").assertIsEnabled()
                            peer.sendIncoming(requireNotNull(liveTransaction))
                        }
                        waitFor(text)
                        ui.onNodeWithText(text).assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/imported-history.png"), text)
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
                        check(peer.transactions.isEmpty() && peer.reports.isEmpty())
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

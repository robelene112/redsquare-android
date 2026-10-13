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

    @Test fun anImportedContactOpensItsAvailableAuthenticatedPrivateHistory() {
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
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (!criteria.has("field1") && criteria.get("field3")?.asString == reference.get("groupId").asString) {
                check(criteria.get("limit").asInt == 50)
                historyReads.incrementAndGet()
                JsonArray().apply { add(JsonObject().apply { addProperty("tx", reference.get("archiveEnvelope").asString) }) }.toString()
            } else "[]"
        })
        var primaryFailure: Throwable? = null
        try {
            synchronized(SaitoLocalState.lock) { savedFiles.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) } }
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, true).commit()) }
            store.replace(store.loadOrCreate(), wallet)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "private-chat-evidence/history-first-failure.png")) }) {
                        waitFor("Chat")
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        waitFor(counterpart)
                        ui.onNodeWithText(counterpart).assertIsDisplayed().performClick()
                        val text = reference.getAsJsonObject("plaintext").get("message").asString
                        waitFor(text)
                        ui.onNodeWithText(text).assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/imported-history.png"), text)
                        check(historyReads.get() > 0)
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
                    .forEach { check(it.isFile && it.delete()) }
                savedFiles.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
            } }
            cleanupFailure?.let { throw it }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

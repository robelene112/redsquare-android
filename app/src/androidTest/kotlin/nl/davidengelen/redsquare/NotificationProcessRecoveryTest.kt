package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Public synthetic activity; the installation's real wallet never changes or leaves its store. */
class NotificationProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "notification-process-checkpoint.json")
    private val actor = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val target = "b".repeat(128)

    @Test fun terminateAfterActivityAndReadStatusAreCommitted() {
        val owner = AndroidWalletStore(context).loadOrCreate().publicKey
        val backend = AndroidBackendStore(context).load()!!
        // This fixture measures read-history recovery, independently of first-use offers.
        check(context.getSharedPreferences("notification-permission", 0).edit()
            .putBoolean("offered", true).putBoolean("social-offered:$owner", true).commit())
        // Exact isolated public fixture families only; no wallet, personal policy or keystore reset.
        listOf("notifications.${backend.id}.$owner.v1", "notificationread.${backend.id}.$owner.v1").forEach { base ->
            synchronized(PublicStringStore.lock) {
                context.noBackupFilesDir.listFiles()?.filter { it.name == base || it.name == "$base.bak" || it.name == "$base.new" }
                    ?.forEach { check(it.isFile && it.delete()) }
            }
        }
        val body = JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "like tweet")
            add("data", JsonObject().apply { addProperty("signature", target) })
        }
        val event = SaitoTransaction.create(actor, body, 1700000000900, listOf(owner))
        val row = JsonObject().apply { addProperty("tx", event.envelope()); addProperty("created_at", 1700000000900L) }
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                require(criteria.get("field3").asString == owner); "[$row]"
            } else "[]"
        }, quoteArchiveResponse = { "[]" })
        peer.start().use { server ->
            launch(server.port) {
                openNotifications()
                waitFor("1 unread"); waitForEnabled("Refresh")
                ui.onNodeWithText("Liked a post").assertIsDisplayed()
                ui.onNodeWithText(actor.publicKey).assertIsDisplayed()
                capture("activity-before-death.png", "1 unread")
                ui.onNodeWithText("Mark all as read").performClick()
                waitFor("No unread activity")
                capture("read-before-death.png", "No unread activity")
                assertIdentity(owner, backend, peer)
                checkpoint.writeText(JsonObject().apply {
                    addProperty("phase", "pending"); addProperty("owner", owner)
                    addProperty("backend", backend.id); addProperty("event", event.id)
                    addProperty("pid", Process.myPid())
                }.toString())
                Process.killProcess(Process.myPid())
                error("Expected actual process termination")
            }
        }
    }

    @Test fun coldRestartRetainsActivityAndReadStatusWhenServerReadsFail() {
        val saved = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("pending", saved.get("phase").asString)
        assertNotEquals(saved.get("pid").asInt, Process.myPid())
        val owner = saved.get("owner").asString
        val backend = AndroidBackendStore(context).load()!!
        assertEquals(saved.get("backend").asString, backend.id)
        assertEquals(owner, AndroidWalletStore(context).loadOrCreate().publicKey)
        val peer = ControlledFeedPeer("[]", archiveResponse = { "{invalid" }, quoteArchiveResponse = { "{invalid" })
        peer.start().use { server ->
            launch(server.port) {
                openNotifications()
                waitFor(context.getString(R.string.notifications_read_error))
                waitFor("No unread activity"); waitForEnabled("Refresh")
                ui.onNodeWithText("Liked a post").assertIsDisplayed()
                ui.onNodeWithText(actor.publicKey).assertIsDisplayed()
                ui.onNodeWithText("Mark all as read").assertIsNotEnabled()
                capture("read-cold-recovered.png", "No unread activity")
                ui.onNodeWithText(actor.publicKey).performClick()
                waitFor("Profile")
                ui.onNodeWithText(actor.publicKey).assertIsDisplayed()
                ui.onNodeWithText("Back").performClick()
                waitFor("No unread activity")
                assertIdentity(owner, backend, peer)
                saved.addProperty("phase", "recovered"); saved.addProperty("recoveredPid", Process.myPid())
                checkpoint.writeText(saved.toString())
            }
        }
    }

    private fun assertIdentity(owner: String, backend: SaitoBackend, peer: ControlledFeedPeer) {
        assertEquals(owner, AndroidWalletStore(context).loadOrCreate().publicKey)
        assertEquals(backend, AndroidBackendStore(context).load())
        peer.assertRequestObserved(); peer.assertHealthy()
        assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
        assertTrue(peer.transactions.isEmpty()); assertTrue(peer.reports.isEmpty())
    }
    private fun launch(port: Int, journey: () -> Unit) {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$port/wsopen")).use {
            withFailureCapture({ capture("first-failure.png") }) { journey() }
        }
    }
    private fun openNotifications() {
        waitForEnabled("Refresh")
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Notifications").performClick()
        if (ui.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Not now").performClick()
    }
    private fun capture(name: String, sentinel: String? = null) = captureNativeScreen(
        File(context.cacheDir, "notification-process-evidence/$name"), sentinel)
    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
}

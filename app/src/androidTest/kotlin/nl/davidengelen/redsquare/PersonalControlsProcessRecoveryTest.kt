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
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Separate host-invoked processes; checkpoint contains public identity only. */
class PersonalControlsProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "personal-controls-process-checkpoint.json")
    private val rootText = "Native Red Square reference post"
    private val otherText = "Another account reference post"
    private val reference by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("native-following-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    }
    private val muted get() = reference.getAsJsonObject("root").get("field2").asString
    private val blocked get() = reference.getAsJsonObject("otherRoot").get("field2").asString

    @Test fun terminateDuringAnUnmuteCommit() {
        // Only preparation clears isolated public fixtures. Cold recovery never resets choices or identity.
        val prefixes = listOf("feed.", "conversation.", "profile.", "following.", "followingfeed.", "search.",
            "quoteoriginal.", "authorposts.", "personal.")
        context.noBackupFilesDir.listFiles()?.filter { file -> prefixes.any(file.name::startsWith) }
            ?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val peer = peer()
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var owner = ""
            launch(intent) {
                waitForEnabled("Refresh")
                showPost(rootText)
                ui.onNodeWithText("Wallet").performClick()
                owner = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                openProfile(muted)
                ui.openProfileActions()
                waitForEnabled("Mute account")
                ui.onNodeWithText("Mute account").performClick()
                waitFor("Mute hides this account's posts and social notifications. Chat messages remain available.")
                ui.onNodeWithText("Mute", substring = false).performClick()
                waitForEnabled("Unmute account")
                ui.closeProfileActions()
                ui.onNodeWithText("Back").performClick()
                showPost(otherText)
                openProfile(blocked)
                ui.openProfileActions()
                waitForEnabled("Block account")
                ui.onNodeWithText("Block account").performClick()
                waitFor("Block hides this account's posts, social notifications and incoming Chat messages.")
                ui.onNodeWithText("Block", substring = false).performClick()
                waitForEnabled("Unblock account")
                ui.closeProfileActions()
                ui.onNodeWithText("Back").performClick()
            }
            launch(Intent(intent).putExtra("personalCommitTerminate", true)) {
                waitForEnabled("Refresh")
                openManagement()
                waitForEnabled("Unmute account")
                waitForEnabled("Unblock account")
                capture("choices-before-death.png", "Unmute account")
                val row = JsonObject().apply {
                    addProperty("phase", "pending"); addProperty("owner", owner)
                    addProperty("muted", muted); addProperty("blocked", blocked)
                    addProperty("backend", "owner"); addProperty("pid", Process.myPid())
                }
                writeCheckpoint(row)
                assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                ui.onNodeWithText("Unmute account").performClick()
                // The DEBUG OS-sync seam kills this process after the active write,
                // while the old-policy guard is still pending. Never substitute Activity.close().
                ui.waitUntil(15_000) { false }
                error("Expected actual process termination")
            }
        }
    }

    @Test fun coldRecoveryRetainsMuteAndBlockBeforeExplicitRemoval() {
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("pending", row.get("phase").asString)
        assertEquals(muted, row.get("muted").asString)
        assertEquals(blocked, row.get("blocked").asString)
        assertEquals("owner", row.get("backend").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        val owner = row.get("owner").asString
        val peer = peer()
        peer.start().use { server ->
            launch(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")) {
                waitForEnabled("Refresh")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(owner, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                openManagement()
                waitForEnabled("Unmute account")
                waitForEnabled("Unblock account")
                ui.onNodeWithText(muted).assertExists()
                ui.onNodeWithText(blocked).assertExists()
                capture("choices-cold-recovered.png", "Unmute account")
                ui.onNodeWithText("Unblock account").performClick()
                waitForEnabled("Unmute account")
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Unblock account").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("Done").performClick()
                showPost(otherText)
                val failure = assertThrows(AssertionError::class.java) {
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(rootText))
                }
                assertTrue(failure.message?.contains("No node found that matches") == true)
                showPost(otherText)
                capture("mute-retained-after-unblock.png", otherText)
                openManagement()
                waitForEnabled("Unmute account")
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("No muted or blocked accounts.")
                ui.onNodeWithText("Done").performClick()
                showPost(rootText)
                capture("explicit-unmute-restored-post.png", rootText)
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(owner, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
                writeCheckpoint(row)
            }
        }
    }

    private fun peer() = ControlledFeedPeer("[${reference.getAsJsonObject("root")},${reference.getAsJsonObject("otherRoot")}]",
        archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            val author = criteria.get("field2")?.asString
            val rows = listOf(reference.getAsJsonObject("root"), reference.getAsJsonObject("otherRoot"))
                .filter { author == null || it.get("field2").asString == author }
            rows.joinToString(prefix = "[", postfix = "]")
        })

    private fun launch(intent: Intent, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(intent).use {
            withFailureCapture({ capture("process-recovery-failure.png") }, block)
        }
    }
    private fun openProfile(author: String) {
        ui.clickVisiblePostProfile("Anon-${author.take(6)}")
        waitFor("Profile")
    }
    private fun openManagement() {
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Privacy and safety").performScrollTo().performClick()
        waitFor("Muted and blocked accounts")
        ui.onNodeWithText("Muted and blocked accounts").performClick()
    }
    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun waitForEnabled(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun showPost(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        ui.onNodeWithText(text).assertIsDisplayed()
    }
    private fun writeCheckpoint(row: JsonObject) {
        checkpoint.outputStream().use { output -> output.write(row.toString().toByteArray()); output.fd.sync() }
    }
    private fun capture(name: String, expected: String? = null) =
        captureNativeScreen(File(context.cacheDir, "personal-controls-process-evidence/$name"), expected)
}

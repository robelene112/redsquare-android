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
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch

/** Host-invoked phases run in separate processes; checkpoint contains public report data only. */
class ReportProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "report-process-checkpoint.json")
    private val root by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
    }

    @Test fun terminateAfterReceiptBeforeAcknowledgement() {
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("report.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val received = CountDownLatch(1)
        val peer = ControlledFeedPeer("[$root]", reportResponse = { frame ->
            val encoded = Base64.getEncoder().encodeToString(frame.copyOfRange(5, frame.size))
            val signed = SaitoEnvelope.decode(SaitoTransaction.restore(encoded).envelope())
            assertEquals(root.get("sig").asString, signed.body.getAsJsonObject("data").get("signature").asString)
            assertEquals(listOf(signed.author), signed.recipients)
            writeCheckpoint(JsonObject().apply {
                addProperty("phase", "received"); addProperty("pid", Process.myPid())
                addProperty("owner", signed.author); addProperty("backend", "owner")
                addProperty("target", root.get("sig").asString); addProperty("transaction", encoded)
            })
            received.countDown()
            "{}"
        }).apply { withholdReportReply = true }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("interruption-failure.png") }) {
                    ui.clickVisiblePostMenuAction("Anon-jesTu2", "Report")
                    ui.onNodeWithText("Send report").performClick()
                    ui.waitUntil(5_000) { received.count == 0L }
                    peer.assertHealthy()
                    assertEquals(1, peer.reports.size); assertTrue(peer.transactions.isEmpty())
                    val sending = "Sending report to saito.davidengelen.nl..."
                    ui.onNodeWithText(sending).assertIsDisplayed()
                    capture("pending-before-process-death.png", sending)
                    ui.onNodeWithText(sending).assertIsDisplayed()
                    val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
                    assertEquals("received", row.get("phase").asString)
                    row.addProperty("phase", "pending")
                    writeCheckpoint(row)
                    Process.killProcess(Process.myPid())
                    error("Expected process termination")
                }
            }
        }
    }

    @Test fun coldStartupKeepsUnknownDeliveryUntilExplicitIdenticalRetry() {
        // No fixture reset, wallet creation or backend-selection rewrite during recovery.
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("pending", row.get("phase").asString)
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertEquals(row.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("cold-recovery-failure.png") }) {
                    ui.clickVisiblePostMenuAction("Anon-jesTu2", "Retry report")
                    val unknown = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                    waitFor(unknown)
                    capture("cold-recovered-unknown.png", unknown)
                    assertTrue(peer.reports.isEmpty()); assertTrue(peer.transactions.isEmpty())
                    ui.onNodeWithText("Cancel").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(row.get("owner").asString, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.clickVisiblePostMenuAction("Anon-jesTu2", "Retry report")
                    waitFor(unknown)
                    assertTrue(peer.reports.isEmpty())
                    ui.onNodeWithText("Send report").performClick()
                    val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                    waitFor(acknowledged)
                    capture("cold-explicit-retry-acknowledged.png", acknowledged)
                    val frame = peer.reports.single()
                    assertArrayEquals(Base64.getDecoder().decode(row.get("transaction").asString), frame.copyOfRange(5, frame.size))
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                    assertEquals(row.get("owner").asString, AndroidWalletStore(context).loadExisting().publicKey)
                    row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
                    writeCheckpoint(row)
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun writeCheckpoint(row: JsonObject) {
        checkpoint.outputStream().use { output -> output.write(row.toString().toByteArray()); output.fd.sync() }
    }
    private fun capture(name: String, expected: String? = null) =
        captureNativeScreen(File(context.cacheDir, "report-process-evidence/$name"), expected)
}

package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64

class ReportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("report.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun confirmedReportGoesOnlyToNamedBackendAndAcknowledgementDoesNotRemovePost() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("confirmation-failure.png") }) {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Report to saito.davidengelen.nl?").assertIsDisplayed()
                    ui.onNodeWithText("This sends this post to this server's moderators. Acknowledgement does not mean it has been reviewed or removed.").assertIsDisplayed()
                    assertTrue(peer.reports.isEmpty())
                    assertTrue(peer.transactions.isEmpty())
                    capture("confirmation.png")
                    ui.onNodeWithText("Send report").assertIsEnabled().performClick()
                    ui.waitUntil(20_000) { ui.onAllNodesWithText("Report acknowledged by saito.davidengelen.nl. Moderator review is separate.").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Report acknowledged by saito.davidengelen.nl. Moderator review is separate.").assertIsDisplayed()
                    capture("acknowledged-feed.png", "Report acknowledged by saito.davidengelen.nl. Moderator review is separate.")
                    peer.assertHealthy()
                    val frame = peer.reports.single()
                    assertEquals(12, frame[0].toInt())
                    assertTrue(ByteBuffer.wrap(frame, 1, 4).int > 0)
                    val candidate = SaitoTransaction.restore(Base64.getEncoder().encodeToString(frame.copyOfRange(5, frame.size)))
                    val signed = SaitoEnvelope.decode(candidate.envelope())
                    assertEquals(setOf("module", "request", "data"), signed.body.keySet())
                    assertEquals("RedSquare", signed.body.get("module").asString)
                    assertEquals("flag tweet", signed.body.get("request").asString)
                    assertEquals(setOf("signature"), signed.body.getAsJsonObject("data").keySet())
                    assertEquals(root.get("sig").asString, signed.body.getAsJsonObject("data").get("signature").asString)
                    assertEquals(listOf(signed.author), signed.recipients)
                    assertTrue(peer.clientKeys.contains(signed.author))
                    assertTrue(peer.transactions.isEmpty())
                    // Public signed transaction only, never wallet storage or private signing material.
                    File(context.cacheDir, "report-evidence/acknowledged-frame.b64").writeText(Base64.getEncoder().encodeToString(frame))
                    File(context.cacheDir, "report-evidence/acknowledged-envelope.json").writeText(candidate.envelope())
                    ui.onNodeWithText("Done").performClick()
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    ui.onNodeWithText("Conversation").assertIsDisplayed()
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    capture("acknowledged-conversation.png")
                }
            }
        }
    }

    @Test fun lostAcknowledgementSurvivesReopeningAndRetryUsesTheSameSignedReport() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]").apply { disconnectAfterReport = true }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ capture("reopening-failure.png") }) {
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Send report").performClick()
                    val uncertain = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                    waitFor(uncertain)
                    ui.onNodeWithText(uncertain).assertIsDisplayed()
                    peer.assertHealthy()
                    val original = peer.reports.single().copyOfRange(5, peer.reports.single().size)
                    capture("lost-acknowledgement.png", uncertain)
                    ui.onNodeWithText("Done").performClick()
                    activity.recreate()
                    ui.clickVisiblePostContent("Retry report")
                    waitFor(uncertain)
                    ui.onNodeWithText(uncertain).assertIsDisplayed()
                    assertEquals(1, peer.reports.size)
                    capture("reopened-uncertain.png", uncertain)
                    peer.disconnectAfterReport = false
                    ui.onNodeWithText("Report to saito.davidengelen.nl?").assertIsDisplayed()
                    ui.onNodeWithText("Send report").performClick()
                    val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                    waitFor(acknowledged)
                    ui.onNodeWithText(acknowledged).assertIsDisplayed()
                    assertEquals(2, peer.reports.size)
                    val retry = peer.reports.last()
                    assertArrayEquals(original, retry.copyOfRange(5, retry.size))
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    capture("retry-acknowledged.png", acknowledged)
                }
            }
        }
    }

    @Test fun unansweredReportDeadlineShowsUnknownAndExplicitRetryKeepsTheOriginalSignature() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]").apply { withholdReportReply = true }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("deadline-failure.png") }) {
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Send report").performClick()
                    val uncertain = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                    waitFor(uncertain)
                    capture("deadline-unknown.png", uncertain)
                    peer.assertHealthy()
                    val original = peer.reports.single().copyOfRange(5, peer.reports.single().size)
                    assertTrue(peer.transactions.isEmpty())
                    ui.onNodeWithText("Done").assertIsEnabled().performClick()
                    ui.clickVisiblePostContent("Retry report")
                    waitFor(uncertain)
                    assertEquals(1, peer.reports.size)
                    peer.withholdReportReply = false
                    ui.onNodeWithText("Send report").performClick()
                    val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                    waitFor(acknowledged)
                    capture("deadline-retry-acknowledged.png", acknowledged)
                    assertEquals(2, peer.reports.size)
                    val retry = peer.reports.last()
                    assertArrayEquals(original, retry.copyOfRange(5, retry.size))
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        }
    }

    @Test fun failedReportSavingSendsNothingAndRepairAllowsExplicitRetry() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ capture("saving-failure.png") }) {
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Cancel").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    val blocked = File(context.noBackupFilesDir, "report.owner.$author.${root.get("sig").asString}.v1")
                    check(blocked.mkdir())
                    val obstacle = File(blocked, "retain-public-save-fault").apply { writeText("public test fault") }
                    try {
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Send report").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Done") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        assertTrue(peer.reports.isEmpty()); assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                        val error = "Report could not be saved. Nothing new was sent to saito.davidengelen.nl. Try again."
                        waitFor(error)
                        capture("saving-prevents-transmission.png", error)
                    } finally { check(obstacle.delete()); check(blocked.delete()) }
                    ui.onNodeWithText("Done").performClick()
                    ui.clickVisiblePostContent("Retry report")
                    ui.onNodeWithText("Report to saito.davidengelen.nl?").assertIsDisplayed()
                    assertTrue(peer.reports.isEmpty())
                    ui.onNodeWithText("Send report").performClick()
                    val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                    waitFor(acknowledged)
                    capture("saving-repaired-acknowledged.png", acknowledged)
                    val frame = peer.reports.single()
                    val candidate = SaitoTransaction.restore(Base64.getEncoder().encodeToString(frame.copyOfRange(5, frame.size)))
                    val signed = SaitoEnvelope.decode(candidate.envelope())
                    assertEquals(author, signed.author)
                    assertEquals(root.get("sig").asString, signed.body.getAsJsonObject("data").get("signature").asString)
                    assertEquals(listOf(author), signed.recipients)
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun capture(name: String, expectedText: String? = null) = captureNativeScreen(File(context.cacheDir, "report-evidence/$name"), expectedText)
}

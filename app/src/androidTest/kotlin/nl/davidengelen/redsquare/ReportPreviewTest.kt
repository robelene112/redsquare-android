package nl.davidengelen.redsquare

import android.content.Intent
import android.app.KeyguardManager
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    @Test fun acknowledgedReportWithFailedReceiptSavingWarnsAndReopensTheSameUnknownIntent() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val savePath = AtomicReference<File>()
        val blockOnce = AtomicBoolean(true)
        val fileMode = AtomicReference<Int?>()
        val directoryMode = AtomicReference<Int?>()
        fun repairSaving(primary: Throwable? = null) {
            var failure = primary
            listOf(directoryMode to context.noBackupFilesDir, fileMode to savePath.get()).forEach { (mode, file) ->
                try {
                    mode.get()?.let {
                        android.system.Os.chmod(requireNotNull(file).path, it)
                        mode.set(null)
                    }
                } catch (cleanup: Throwable) {
                    val earlier = failure
                    if (earlier == null) failure = cleanup
                    else if (earlier !== cleanup) earlier.addSuppressed(cleanup)
                }
            }
            if (primary == null && failure != null) throw requireNotNull(failure)
        }
        val peer = ControlledFeedPeer("[$root]", reportResponse = {
            if (blockOnce.compareAndSet(true, false)) {
                val saved = requireNotNull(savePath.get())
                fileMode.set(android.system.Os.stat(saved.path).st_mode and 0x1ff)
                directoryMode.set(android.system.Os.stat(context.noBackupFilesDir.path).st_mode and 0x1ff)
                // Block both legacy base-file writing and newer temporary-file commits.
                // The original public record remains intact. Always restore these modes.
                android.system.Os.chmod(saved.path, 0x100)
                android.system.Os.chmod(context.noBackupFilesDir.path, 0x140)
            }
            "{}"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ capture("receipt-save-failure.png") }) {
                    var failure: Throwable? = null
                    try {
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Cancel").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        val author = displayedWalletAddress(ui)
                        ui.onNodeWithText("Done").performClick()
                        savePath.set(File(context.noBackupFilesDir, "report.owner.$author.${root.get("sig").asString}.v1"))
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Send report").performClick()
                        val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                        waitFor(acknowledged)
                        peer.assertHealthy()
                        val original = peer.reports.single().copyOfRange(5, peer.reports.single().size)
                        val warning = "Report details could not be saved on this phone. Reopening may show delivery unknown."
                        waitFor(warning)
                        capture("receipt-save-warning.png", warning)
                        assertTrue(peer.transactions.isEmpty())
                        repairSaving()
                        ui.onNodeWithText("Done").performClick()
                        activity.recreate()
                        ui.clickVisiblePostContent("Retry report")
                        val uncertain = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                        waitFor(uncertain)
                        capture("receipt-save-reopened-unknown.png", uncertain)
                        assertEquals(1, peer.reports.size)
                        ui.onNodeWithText("Send report").performClick()
                        waitFor(acknowledged)
                        capture("receipt-save-repaired-acknowledged.png", acknowledged)
                        assertEquals(2, peer.reports.size)
                        val retry = peer.reports.last()
                        assertArrayEquals(original, retry.copyOfRange(5, retry.size))
                        assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    } catch (primary: Throwable) {
                        failure = primary
                        throw primary
                    } finally { repairSaving(failure) }
                }
            }
        }
    }

    @Test fun unavailableBackendBeforeSendingStaysNotSentAfterReopeningAndNeedsExplicitRetry() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]")
        // Explicit port enables reuse for both listener lifetimes.
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        peer.start(port).use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")).use { activity ->
                withFailureCapture({ capture("unavailable-before-send-failure.png") }) {
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Cancel").performClick()
                    server.close()
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Send report").performClick()
                    val notSent = "Report was not sent to saito.davidengelen.nl. Try again when this server is available."
                    waitFor(notSent)
                    capture("unavailable-before-send.png", notSent)
                    assertTrue(peer.reports.isEmpty()); assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    ui.onNodeWithText("Done").performClick()
                    activity.recreate()
                    ui.clickVisiblePostContent("Retry report")
                    waitFor(notSent)
                    capture("unavailable-before-send-reopened.png", notSent)
                    assertTrue(peer.reports.isEmpty()); assertTrue(peer.transactions.isEmpty())
                    peer.start(port).use {
                        ui.onNodeWithText("Send report").performClick()
                        val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                        waitFor(acknowledged)
                        capture("unavailable-before-send-repaired.png", acknowledged)
                        assertEquals(1, peer.reports.size)
                        assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    }
                }
            }
        }
    }

    @Test fun switchingBackendsKeepsReportsAndAcknowledgementsWithTheirOriginalDestination() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val owner = ControlledFeedPeer("[$root]").apply { disconnectAfterReport = true }
        val official = ControlledFeedPeer("[$root]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use { activity ->
                    withFailureCapture({ capture("backend-isolation-failure.png") }) {
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Send report").performClick()
                        val uncertain = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                        waitFor(uncertain)
                        val first = owner.reports.single()
                        val original = first.copyOfRange(5, first.size)
                        val ownerReport = SaitoEnvelope.decode(SaitoTransaction.restore(
                            Base64.getEncoder().encodeToString(original)).envelope())
                        capture("backend-owner-unknown.png", uncertain)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("saito.io", substring = false).performClick()
                        waitFor("Server: saito.io")
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Report to saito.io?").assertIsDisplayed()
                        capture("backend-official-confirmation.png", "Report to saito.io?")
                        assertEquals(1, owner.reports.size); assertTrue(official.reports.isEmpty())
                        ui.onNodeWithText("Send report").performClick()
                        val officialAck = "Report acknowledged by saito.io. Moderator review is separate."
                        waitFor(officialAck)
                        capture("backend-official-acknowledged.png", officialAck)
                        val separate = official.reports.single()
                        val officialBytes = separate.copyOfRange(5, separate.size)
                        assertFalse(original.contentEquals(officialBytes))
                        val officialReport = SaitoEnvelope.decode(SaitoTransaction.restore(
                            Base64.getEncoder().encodeToString(officialBytes)).envelope())
                        assertEquals(ownerReport.author, officialReport.author)
                        assertEquals(listOf(ownerReport.author), ownerReport.recipients)
                        assertEquals(listOf(ownerReport.author), officialReport.recipients)
                        assertEquals(root.get("sig").asString, ownerReport.body.getAsJsonObject("data").get("signature").asString)
                        assertEquals(root.get("sig").asString, officialReport.body.getAsJsonObject("data").get("signature").asString)
                        ui.onNodeWithText("Done").performClick()
                        activity.recreate()
                        ui.clickVisiblePostContent("Report details")
                        waitFor(officialAck)
                        assertEquals(1, official.reports.size)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("saito.davidengelen.nl", substring = false).performClick()
                        waitFor("Server: saito.davidengelen.nl")
                        ui.clickVisiblePostContent("Retry report")
                        waitFor(uncertain)
                        capture("backend-owner-restored-unknown.png", uncertain)
                        assertEquals(1, owner.reports.size); assertEquals(1, official.reports.size)
                        owner.disconnectAfterReport = false
                        ui.onNodeWithText("Send report").performClick()
                        val ownerAck = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                        waitFor(ownerAck)
                        capture("backend-owner-retry-acknowledged.png", ownerAck)
                        val retry = owner.reports.last()
                        assertEquals(2, owner.reports.size); assertEquals(1, official.reports.size)
                        assertArrayEquals(original, retry.copyOfRange(5, retry.size))
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(ownerReport.author, displayedWalletAddress(ui))
                        assertEquals(ownerReport.author, AndroidWalletStore(context).loadExisting().publicKey)
                        assertTrue(owner.transactions.isEmpty()); assertTrue(official.transactions.isEmpty())
                        owner.assertHealthy(); official.assertHealthy()
                    }
                }
            }
        }
    }

    @Test fun unreadableReportDetailsFailClosedAndRepairRecoversTheOriginalIntent() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val peer = ControlledFeedPeer("[$root]").apply { disconnectAfterReport = true }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                withFailureCapture({ capture("unreadable-details-failure.png") }) {
                    ui.clickVisiblePostContent("Report")
                    ui.onNodeWithText("Send report").performClick()
                    val unknown = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                    waitFor(unknown)
                    val frame = peer.reports.single()
                    val original = frame.copyOfRange(5, frame.size)
                    val author = SaitoEnvelope.decode(SaitoTransaction.restore(
                        Base64.getEncoder().encodeToString(original)).envelope()).author
                    val saved = File(context.noBackupFilesDir, "report.owner.$author.${root.get("sig").asString}.v1")
                    check(saved.length() in 1L..8192L)
                    val record = saved.readBytes() // Public signed report only.
                    var restoreNeeded = false
                    fun repair(primary: Throwable? = null) {
                        if (!restoreNeeded) return
                        try {
                            saved.outputStream().use { it.write(record); it.fd.sync() }
                            restoreNeeded = false
                        } catch (cleanup: Throwable) {
                            if (primary == null) throw cleanup
                            if (primary !== cleanup) primary.addSuppressed(cleanup)
                        }
                    }
                    var failure: Throwable? = null
                    try {
                        ui.onNodeWithText("Done").performClick()
                        restoreNeeded = true
                        saved.outputStream().use { it.write("invalid public report fixture".toByteArray()); it.fd.sync() }
                        activity.recreate()
                        ui.clickVisiblePostContent("Report")
                        val error = "Report details for saito.davidengelen.nl could not be opened. Retry to reload them. Nothing new has been sent."
                        waitFor(error)
                        capture("unreadable-details-closed.png", error)
                        ui.onNodeWithText("Send report").assertDoesNotExist()
                        assertEquals(1, peer.reports.size); assertTrue(peer.transactions.isEmpty())
                        repair()
                        ui.onNodeWithText("Retry", substring = false).performClick()
                        waitFor(unknown)
                        capture("unreadable-details-repaired-unknown.png", unknown)
                        assertEquals(1, peer.reports.size)
                        ui.onNodeWithText("Done").performClick()
                        ui.clickVisiblePostContent("Retry report")
                        peer.disconnectAfterReport = false
                        ui.onNodeWithText("Send report").performClick()
                        val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                        waitFor(acknowledged)
                        capture("unreadable-details-explicit-acknowledged.png", acknowledged)
                        val retry = peer.reports.last()
                        assertEquals(2, peer.reports.size)
                        assertArrayEquals(original, retry.copyOfRange(5, retry.size))
                        assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    } catch (primary: Throwable) { failure = primary; throw primary }
                    finally { repair(failure) }
                }
            }
        }
    }

    @Test fun authenticatedWalletReplacementIsolatesReportsAndRestorationRecoversTheOriginalRetry() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        // Bootstrap through the real app if this case is first on a focused installation.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra("feedDisabled", true)).use {
            withFailureCapture({ capture("identity-bootstrap-failure.png") }) {
                waitFor("Wallet")
                ui.onNodeWithText("Wallet").performClick()
                displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
            }
        }
        val walletStore = AndroidWalletStore(context)
        val originalWallet = walletStore.loadExisting()
        val scalar = if (originalWallet.publicKey == "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU") "3" else "2"
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + scalar) // Public unsafe fixture only.
        assertNotEquals(originalWallet.publicKey, incoming.publicKey)
        assertNotEquals(root.get("field2").asString, incoming.publicKey)
        val incomingFile = "00-report-import-$scalar.json"
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/$incomingFile")
            .trim().toLongOrNull()?.let { it > 0 } == true)
        val backupFile = "00-report-original-${UUID.randomUUID()}.json"
        val backupUri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, backupFile)
        val peer = ControlledFeedPeer("[$root]").apply { disconnectAfterReport = true }
        val balanceServer = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest) = mockwebserver3.MockResponse.Builder()
                    .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
            }
            start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        }
        var failure: Throwable? = null
        try {
            device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${balanceServer.port}/balance/")
                ActivityScenario.launch<MainActivity>(intent).use {
                    withFailureCapture({ capture("identity-replacement-failure.png") }) {
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Send report").performClick()
                        val unknown = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                        waitFor(unknown)
                        val frame = peer.reports.single()
                        val original = frame.copyOfRange(5, frame.size)
                        assertEquals(originalWallet.publicKey, SaitoEnvelope.decode(SaitoTransaction.restore(
                            Base64.getEncoder().encodeToString(original)).envelope()).author)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(originalWallet.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, incomingFile, incoming.publicKey)
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                        ui.onNodeWithText("Back up current wallet").performScrollTo().performClick()
                        chooseTestBackup(device, backupFile)
                        enterRealSystemPin(device)
                        waitFor("Current wallet backed up for this replacement")
                        // Keep private backup bytes only in bounded memory, never logs/artifacts.
                        val bytes = requireNotNull(context.contentResolver.openInputStream(backupUri)).use { stream ->
                            val buffer = ByteArray(SaitoWallet.MAX_BACKUP_BYTES + 1)
                            try {
                                var size = 0
                                while (size < buffer.size) {
                                    val count = stream.read(buffer, size, buffer.size - size)
                                    if (count < 0) break
                                    check(count > 0); size += count
                                }
                                check(size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                                buffer.copyOf(size)
                            } finally { buffer.fill(0) }
                        }
                        try {
                            // Invalid private JSON must fail through the wallet's sanitized parser.
                            val backedUp = SaitoWallet.fromBackup(bytes.toString(Charsets.UTF_8))
                            assertTrue("Authenticated backup must retain all original options",
                                JsonParser.parseString(originalWallet.exportJson()) == JsonParser.parseString(backedUp.exportJson()))
                        }
                        finally { bytes.fill(0) }
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                        capture("identity-backed-up-consent.png", "Current wallet backed up for this replacement")
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                        waitFor("Wallet imported")
                        assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                        assertEquals("retain-this-opaque-option", JsonParser.parseString(walletStore.loadExisting().exportJson())
                            .asJsonObject.getAsJsonObject("reportFixtureExtension").get("opaque").asString)
                        ui.onNodeWithText("Done").performClick()
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Report to saito.davidengelen.nl?").assertIsDisplayed()
                        ui.onNodeWithText(unknown).assertDoesNotExist()
                        capture("identity-incoming-independent-report.png", "Report to saito.davidengelen.nl?")
                        assertEquals(1, peer.reports.size)
                        ui.onNodeWithText("Cancel").performClick()
                    }
                }
                ActivityScenario.launch<MainActivity>(intent).use {
                    withFailureCapture({ capture("identity-restoration-failure.png") }) {
                        ui.clickVisiblePostContent("Report")
                        ui.onNodeWithText("Report to saito.davidengelen.nl?").assertIsDisplayed()
                        assertEquals(1, peer.reports.size)
                        ui.onNodeWithText("Cancel").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, backupFile, originalWallet.publicKey)
                        waitFor("Server reports zero SAITO")
                        ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff().performClick()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                        waitFor("Wallet imported")
                        assertEquals(originalWallet.publicKey, displayedWalletAddress(ui))
                        assertTrue("Restoration must retain all original options",
                            JsonParser.parseString(originalWallet.exportJson()) == JsonParser.parseString(walletStore.loadExisting().exportJson()))
                        ui.onNodeWithText("Done").performClick()
                        ui.clickVisiblePostContent("Retry report")
                        val unknown = "Report delivery to saito.davidengelen.nl is unknown. Try again to send the same report."
                        waitFor(unknown)
                        capture("identity-original-restored-unknown.png", unknown)
                        assertEquals(1, peer.reports.size)
                        peer.disconnectAfterReport = false
                        ui.onNodeWithText("Send report").performClick()
                        val acknowledged = "Report acknowledged by saito.davidengelen.nl. Moderator review is separate."
                        waitFor(acknowledged)
                        capture("identity-original-explicit-acknowledged.png", acknowledged)
                        assertEquals(2, peer.reports.size)
                        val first = peer.reports.first(); val retry = peer.reports.last()
                        assertArrayEquals(first.copyOfRange(5, first.size), retry.copyOfRange(5, retry.size))
                        assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                        assertTrue(peer.clientKeys.contains(originalWallet.publicKey) && peer.clientKeys.contains(incoming.publicKey))
                        assertTrue(peer.clientKeys.all { it == originalWallet.publicKey || it == incoming.publicKey })
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                    }
                }
            }
        } catch (primary: Throwable) { failure = primary; throw primary }
        finally {
            var cleanupFailure = failure
            val cleanups: List<() -> Unit> = listOf({
                val active = walletStore.loadExisting()
                check(active.publicKey == originalWallet.publicKey || active.publicKey == incoming.publicKey)
                // Emergency isolated-fixture cleanup only; successful restore is proved through SAF.
                if (active.publicKey == incoming.publicKey) walletStore.replace(active, originalWallet)
            }, {
                device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                ui.waitUntil(10_000) { !keyguard.isDeviceSecure }
            }, {
                check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test rm -f files/wallet-test-documents/$backupFile files/wallet-test-documents/00-report-import-2.json files/wallet-test-documents/00-report-import-3.json").isBlank())
            }, { balanceServer.close() })
            cleanups.forEach { cleanup ->
                try { cleanup() }
                catch (secondary: Throwable) {
                    val earlier = cleanupFailure
                    if (earlier == null) cleanupFailure = secondary else if (earlier !== secondary) earlier.addSuppressed(secondary)
                }
            }
            if (failure == null && cleanupFailure != null) throw requireNotNull(cleanupFailure)
        }
    }

    private fun waitFor(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun capture(name: String, expectedText: String? = null) = captureNativeScreen(File(context.cacheDir, "report-evidence/$name"), expectedText)
}

package nl.davidengelen.redsquare

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import nl.davidengelen.redsquare.saito.SaitoPayment
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import androidx.test.uiautomator.UiDevice
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

class WalletTransferPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("transfer.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun pendingPaymentReopensWithoutSendingAndExactRetryRequiresFreshReviewedDeviceAuthentication() = withDevicePin { device ->
        val peer = ControlledFeedPeer("[]")
        val server = fundedTransferServer()
        server.use {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { transport ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "transfer-evidence/authenticated-send-first-failure.png")) }) {
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" // Public unsafe test identity.
                        enterNativeTransfer(ui, recipient, "6.25", "0.1")
                        ui.onNodeWithContentDescription("Public memo").performTextInput("Native transfer test")
                        reviewNativeTransfer(ui, device)
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Confirm SAITO transfer"))
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                        assertTrue(peer.transactions.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/reviewed-payment.png"), "Confirm SAITO transfer")
                        ui.onNodeWithText("Authenticate and send").performClick()
                        waitForSystemPin(device)
                        assertTrue(peer.transactions.isEmpty())
                        captureSystemCredentialEvidence(device)
                        enterRealSystemPin(device)
                        val pending = "Pending. Inclusion has not been verified."
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText(pending))
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
                        ui.waitUntil(5_000) { peer.assertHealthy(); peer.transactions.isNotEmpty() }
                        peer.assertHealthy()
                        val frame = peer.transactions.single()
                        assertEquals(4, frame[0].toInt())
                        assertEquals(1, ByteBuffer.wrap(frame, 1 + 12, 4).int)
                        val wire = frame.copyOfRange(1, frame.size - 130)
                        ByteBuffer.wrap(wire).putInt(12, 0)
                        val payment = SaitoPayment.restore(Base64.getEncoder().encodeToString(wire))
                        assertEquals(author, payment.author)
                        assertEquals(recipient, payment.recipient)
                        assertEquals(BigInteger("625000000"), payment.amount)
                        assertEquals(BigInteger("10000000"), payment.fee)
                        assertEquals(listOf(2, 0), payment.inputs.map { input -> input.index })
                        assertEquals(listOf(0, 7), payment.inputs.map { input -> input.type })
                        ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/pending-payment.png"), pending)
                        File(context.cacheDir, "transfer-evidence/authenticated-frame.b64")
                            .writeText(Base64.getEncoder().encodeToString(frame))
                        scenario.recreate()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                        ui.onNodeWithText("Wallet").performClick()
                        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        ui.waitUntil(20_000) { ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                        ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").assertIsDisplayed()
                        peer.assertHealthy()
                        assertEquals("Reopening must not send the saved payment", 1, peer.transactions.size)
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/pending-reopened.png"), pending)
                        ui.onNodeWithText("Retry transfer").performScrollTo().performClick()
                        ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
                        ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                        ui.onNodeWithText("Memo: Native transfer test").assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/reviewed-retry.png"), "Confirm SAITO transfer")
                        ui.onNodeWithText("Authenticate and send").performClick()
                        waitForSystemPin(device)
                        assertEquals("A reopened payment needs a fresh grant", 1, peer.transactions.size)
                        enterRealSystemPin(device)
                        ui.waitUntil(20_000) { ui.onNodeWithText(pending).isDisplayed() }
                        ui.waitUntil(5_000) { peer.assertHealthy(); peer.transactions.size == 2 }
                        assertArrayEquals("Retry must preserve the exact signed and routed candidate", frame, peer.transactions.last())
                        ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/exact-retry-pending.png"), pending)
                    }
                }
            }
        }
    }

    @Test fun cancellingTheRealCredentialPromptDoesNotSendOrReserveAndReopeningDoesNotQueue() = withDevicePin { device ->
        val peer = ControlledFeedPeer("[]")
        fundedTransferServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { transport ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                    ui.onNodeWithText("Wallet").performClick()
                    val address = displayedWalletAddress(ui)
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "transfer-evidence/auth-cancel-failure.png")) }) {
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        enterNativeTransfer(ui, "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", "6.25", "0.1")
                        reviewNativeTransfer(ui, device)
                        ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
                        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Authenticate and send").performClick()
                        waitForSystemPin(device)
                        assertTrue(peer.transactions.isEmpty())
                        device.dumpWindowHierarchy(File(context.cacheDir,
                            "transfer-evidence/auth-cancel-before-system-back.xml"))
                        cancelRealSystemAuthentication(device, context.packageName)
                        ui.waitUntil(15_000) { ui.onAllNodes(hasText("Transfer cancelled before sending."))
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText("Transfer cancelled before sending.").assertIsDisplayed()
                        peer.assertHealthy()
                        assertTrue(peer.transactions.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/auth-cancelled.png"),
                            "Transfer cancelled before sending.")
                        device.pressBack() // Close the reviewed transfer and return to the Wallet.
                        ui.waitUntil(5_000) { ui.onNodeWithText("Send SAITO").isDisplayed() }
                        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(address)
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 7.5 SAITO").isDisplayed() }
                        ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                        peer.assertHealthy()
                        assertTrue(peer.transactions.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/auth-cancel-reopened.png"),
                            "Available to send: 7.5 SAITO")
                    }
                }
            }
        }
    }

    @Test fun sendingWithoutAScreenLockExplainsTransferSetupAndDoesNotQueue() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        val peer = ControlledFeedPeer("[]")
        fundedTransferServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { transport ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "transfer-evidence/auth-setup-first-failure.png")) }) {
                        val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        enterNativeTransfer(ui, recipient, "6.25", "0.1")
                        reviewNativeTransfer(ui, device)
                        ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
                        ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                        peer.assertHealthy()
                        ui.waitUntil(5_000) { peer.assertHealthy(); peer.servedChallenges.get() > 0 }
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Authenticate and send").performClick()
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        val setup = "Set a screen lock in Android settings to send SAITO."
                        ui.onNodeWithText(setup).assertIsDisplayed()
                        assertFalse(keyguard.isDeviceSecure)
                        assertEquals(context.packageName, device.currentPackageName)
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/auth-setup.png"), setup)
                        device.pressBack() // Dismiss the positively observed app dialog, with no system credential screen.
                        ui.waitUntil(5_000) { ui.onNodeWithText("Send SAITO").isDisplayed() }
                        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 7.5 SAITO").isDisplayed() }
                        ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/auth-setup-reopened.png"),
                            "Available to send: 7.5 SAITO")
                    }
                }
            }
        }
    }

    @Test fun fundingLostAfterReviewAndRealAuthenticationCannotSendReserveOrQueue() = withDevicePin { device ->
        val lostFunding = java.util.concurrent.atomic.AtomicBoolean(false)
        val lostOptionsReads = AtomicInteger()
        val lostBalanceReads = AtomicInteger()
        val peer = ControlledFeedPeer("[]")
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.target == "/options") {
                        if (lostFunding.get()) lostOptionsReads.incrementAndGet()
                        return MockResponse.Builder().body("""{"consensus":{"genesis_period":80640}}""").build()
                    }
                    val key = request.target.removePrefix("/balance/")
                    if (request.target != "/balance/$key" || !SaitoWallet.isPublicKey(key)) {
                        return MockResponse.Builder().code(400).build()
                    }
                    val header = "${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n"
                    val rows = if (lostFunding.get()) {
                        lostBalanceReads.incrementAndGet()
                        ""
                    } else "$key 1001 7 2 500000000 0\n$key 1002 9 0 250000000 7\n"
                    return MockResponse.Builder().body(header + rows).build()
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { transport ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "transfer-evidence/funding-lost-first-failure.png")) }) {
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                        enterNativeTransfer(ui, recipient, "6.25", "0.1")
                        reviewNativeTransfer(ui, device)
                        ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
                        ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                        ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        lostFunding.set(true)
                        ui.onNodeWithText("Authenticate and send").performClick()
                        waitForSystemPin(device)
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        enterRealSystemPin(device)
                        val oldFailure = "Transfer was not submitted. Review again when the selected server is available."
                        val failure = "Transfer was not submitted. Check your available funds and connection, then review again."
                        ui.waitUntil(20_000) { ui.onNodeWithText(oldFailure).isDisplayed() || ui.onNodeWithText(failure).isDisplayed() }
                        assertTrue(lostOptionsReads.get() > 0)
                        assertTrue(lostBalanceReads.get() > 0)
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText(failure).assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/funding-lost.png"), failure)
                        assertEquals(context.packageName, device.currentPackageName)
                        device.pressBack() // The observed failure is in the app, after the credential prompt finished.
                        ui.waitUntil(5_000) { ui.onNodeWithText("Send SAITO").isDisplayed() }
                        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
                        lostFunding.set(false)
                        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                        ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 7.5 SAITO").isDisplayed() }
                        ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                        peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "transfer-evidence/funding-lost-reopened.png"),
                            "Available to send: 7.5 SAITO")
                    }
                }
            }
        }
    }

    @Test fun unreadableTransferRecordsBlockAuthenticationAndReviewUntilReadableAgain() = withDevicePin { device ->
        val peer = ControlledFeedPeer("[]")
        var ownedFault: File? = null
        var primaryFailure: Throwable? = null
        try {
            fundedTransferServer().use { server ->
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { transport ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedDisabled", true)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                        ui.onNodeWithText("Wallet").performClick()
                        val author = displayedWalletAddress(ui)
                        withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                            "transfer-evidence/storage-first-failure.png")) }) {
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                            enterNativeTransfer(ui, recipient, "6.25", "0.1")
                            reviewNativeTransfer(ui, device)
                            ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
                            ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                            ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                            ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                            ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            // A new owned orphan at the real filesystem seam, only in the guarded .dev emulator.
                            val fault = File(context.noBackupFilesDir, "transfer.$author.pending")
                            check(!fault.exists())
                            ownedFault = fault
                            fault.writeBytes(byteArrayOf(0))
                            ui.onNodeWithText("Authenticate and send").performClick()
                            waitForSystemPin(device)
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            enterRealSystemPin(device)
                            val recovery = "Transfer records need recovery. Available funds are unknown. Nothing new can be sent until these records are recovered."
                            ui.waitUntil(20_000) { ui.onNodeWithText(recovery).isDisplayed() }
                            assertEquals(context.packageName, device.currentPackageName)
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            ui.onNodeWithText("Authenticate and send").assertIsNotEnabled()
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/storage-blocked-review.png"), recovery)
                            scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            ui.waitUntil(15_000) { ui.onNodeWithText(recovery).isDisplayed() }
                            ui.onAllNodesWithText("Authenticate and send").assertCountEquals(0)
                            ui.onAllNodesWithText("Review transfer").assertCountEquals(0)
                            ui.onNodeWithContentDescription("Recipient address").assertDoesNotExist()
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            assertTrue(fault.readBytes().contentEquals(byteArrayOf(0)))
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/storage-blocked-reopened.png"), recovery)
                            // Restore the known empty fixture baseline, never delete real candidate records as recovery.
                            check(fault.delete())
                            scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 7.5 SAITO").isDisplayed() }
                            ui.onAllNodesWithText(recovery).assertCountEquals(0)
                            ui.onAllNodesWithText("Pending. Inclusion has not been verified.").assertCountEquals(0)
                            peer.assertHealthy(); assertTrue(peer.transactions.isEmpty())
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/storage-readable-again.png"),
                                "Available to send: 7.5 SAITO")
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            try {
                ownedFault?.takeIf { it.exists() }?.let { fault ->
                    check(fault.readBytes().contentEquals(byteArrayOf(0)))
                    check(fault.delete())
                }
            } catch (cleanup: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanup else primary.addSuppressed(cleanup)
            }
        }
    }

    private fun withDevicePin(action: (UiDevice) -> Unit) {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        var primaryFailure: Throwable? = null
        try {
            device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            action(device)
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            try {
                device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                ui.waitUntil(10_000) { !keyguard.isDeviceSecure }
            } catch (cleanupFailure: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanupFailure else primary.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun captureSystemCredentialEvidence(device: UiDevice) {
        // System credential windows may refuse screenshots or return a black bitmap.
        // The real PIN control and authentication assertions remain mandatory.
        waitForSystemPin(device)
        val directory = File(context.cacheDir, "transfer-evidence").apply { mkdirs() }
        device.dumpWindowHierarchy(File(directory, "real-device-auth.xml"))
        val captured = device.takeScreenshot(File(directory, "real-device-auth.png"))
        File(directory, "real-device-auth-capture.txt").writeText(
            "System PIN control observed before entry. Screenshot returned $captured.\n" +
                "A refused or black system screenshot does not establish visible credential content.\n")
    }

    @Test fun selectedServerBalanceAndReceivingAddressCanBeViewedAndCopied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val requestedKeys = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.target
                val key = path.removePrefix("/balance/")
                if (path != "/balance/$key" || !SaitoWallet.isPublicKey(key)) {
                    return MockResponse.Builder().code(400).build()
                }
                requestedKeys.add(key)
                return MockResponse.Builder().body(
                    "${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n" +
                        "$key 1001 7 2 500000000 0\n" +
                        "$key 1002 9 0 250000000 7\n").build()
            }
        }
        server.use {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedDisabled", true)
                .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                    "transfer-evidence/balance-first-failure.png")) }) {
                    ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onAllNodes(hasText("7.5 SAITO"))
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNodeWithText("7.5 SAITO").assertIsDisplayed()
                    ui.onNodeWithText("Balance from saito.davidengelen.nl").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Receiving address").assertTextEquals(address)
                    assertEquals(listOf(address), requestedKeys.toList())
                    ui.onNodeWithText("Copy address").performClick()
                    scenario.onActivity { activity ->
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        assertEquals(address, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                    }
                    captureNativeScreen(File(context.cacheDir, "transfer-evidence/balance-and-receive.png"), "7.5 SAITO")
                }
            }
        }
    }

    @Test fun receivingQrContainsTheFullActiveAddressWithoutBalanceConnectivityAfterReopening() {
        // Published unsafe scalar2 identity, used only on the isolated emulator.
        val address = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
        val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        assertEquals(address, fixture.publicKey)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val unavailableBalanceReads = AtomicInteger()
        var primaryFailure: Throwable? = null
        try {
            wallets.replace(previous, fixture)
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.target == "/balance/$address") unavailableBalanceReads.incrementAndGet()
                        return MockResponse.Builder().code(503).build()
                    }
                }
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "transfer-evidence/receiving-qr-first-failure.png")) }) {
                        repeat(2) { opening ->
                            if (opening > 0) scenario.recreate()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithContentDescription("Wallet address").assertTextEquals(address)
                            ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                            ui.waitUntil(15_000) { ui.onNodeWithText("Balance unavailable").isDisplayed() }
                            assertTrue("The receiving view must tolerate an actual failed balance request",
                                unavailableBalanceReads.get() > opening)
                            ui.onNodeWithContentDescription("Receiving address").assertTextEquals(address)
                            ui.onNodeWithText("Copy address").performScrollTo().performClick()
                            scenario.onActivity { activity ->
                                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                assertEquals(address, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                            }
                            val bitmap = ui.onNodeWithContentDescription("Receiving address QR code")
                                .performScrollTo().assertIsDisplayed().captureToImage().asAndroidBitmap()
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            assertEquals(address, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
                                RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text)
                            captureNativeScreen(File(context.cacheDir,
                                "transfer-evidence/receiving-qr-$opening.png"))
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
        finally {
            try {
                val current = wallets.loadExisting()
                when (current.exportJson()) {
                    previous.exportJson() -> Unit
                    fixture.exportJson() -> wallets.replace(current, previous)
                    else -> error("Unexpected receiving fixture wallet state")
                }
                check(wallets.loadExisting().exportJson() == previous.exportJson())
            } catch (cleanup: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanup else primary.addSuppressed(cleanup)
            }
        }
    }
}

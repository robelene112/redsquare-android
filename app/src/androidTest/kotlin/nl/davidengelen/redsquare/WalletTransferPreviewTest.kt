package nl.davidengelen.redsquare

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoWallet
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

    @Test fun onlinePaymentRequiresReviewedAmountsThenRealDeviceAuthenticationAndRemainsPending() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        try {
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            val peer = ControlledFeedPeer("[]")
            val server = MockWebServer()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.target == "/options") return MockResponse.Builder().body(
                        "{\"consensus\":{\"genesis_period\":80640,\"default_social_stake_period\":100,\"block_confirmation_limit\":1}}").build()
                    val key = request.target.removePrefix("/balance/")
                    if (request.target != "/balance/$key" || !SaitoWallet.isPublicKey(key)) {
                        return MockResponse.Builder().code(400).build()
                    }
                    return MockResponse.Builder().body(
                        "${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n" +
                            "$key 1001 7 2 500000000 0\n" +
                            "$key 1002 9 0 250000000 7\n").build()
                }
            }
            server.use {
                server.start(InetAddress.getByName("127.0.0.1"), 0)
                peer.start().use { transport ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedDisabled", true)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                        ui.onNodeWithText("Wallet").performClick()
                        val author = displayedWalletAddress(ui)
                        withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                            "transfer-evidence/authenticated-send-first-failure.png")) }) {
                            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                            val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" // Public unsafe test identity.
                            ui.onNodeWithContentDescription("Recipient address").performTextInput(recipient)
                            ui.onNodeWithContentDescription("Amount in SAITO").performTextInput("6.25")
                            ui.onNodeWithContentDescription("Fee in SAITO").performTextReplacement("0.1")
                            ui.onNodeWithContentDescription("Public memo").performTextInput("Native transfer test")
                            ui.onNodeWithText("Review transfer").performScrollTo().performClick()
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
                            captureNativeScreen(File(context.cacheDir, "transfer-evidence/real-device-auth.png"))
                            enterRealSystemPin(device)
                            val pending = "Pending. Inclusion has not been verified."
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText(pending))
                                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
                            ui.waitUntil(5_000) { peer.transactions.isNotEmpty() }
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
                        }
                    }
                }
            }
        } finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
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
}

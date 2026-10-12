package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.content.Intent
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.Base64
import java.util.regex.Pattern

/** Separate host-driven processes. Only public payment data enters the checkpoint. */
class TransferProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.cacheDir, "transfer-process-checkpoint.json")
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val keyguard get() = context.getSystemService(KeyguardManager::class.java)
    private val pending = "Pending. Inclusion has not been verified."
    private val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" // Public unsafe scalar2.

    private fun guard() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Test fun terminateAfterAnAuthenticatedPendingNativeTransfer() {
        guard()
        // Preparation only. The cold phase never resets the wallet, backend or saved work.
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("transfer.") }
            ?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val author = AndroidWalletStore(context).loadExisting().publicKey
        assertFalse(keyguard.isDeviceSecure)
        // The host owns cleanup, including failures and deliberate termination of this process.
        device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
        ui.waitUntil(10_000) { keyguard.isDeviceSecure }
        val peer = ControlledFeedPeer("[]")
        launch(peer) {
            openTransfer(author)
            ui.onNodeWithContentDescription("Recipient address").performTextInput(recipient)
            ui.onNodeWithContentDescription("Amount in SAITO").performTextInput("6.25")
            ui.onNodeWithContentDescription("Fee in SAITO").performTextReplacement("0.1")
            ui.onNodeWithContentDescription("Public memo").performTextInput("Native transfer test")
            val keyboard = By.pkg(Pattern.compile("com\\.(?:google\\.)?android\\.inputmethod\\.latin"))
            ui.waitUntil(5_000) { device.hasObject(keyboard) || ui.onNodeWithText("Review transfer").isDisplayed() }
            if (device.hasObject(keyboard)) {
                device.pressBack()
                check(device.wait(Until.gone(keyboard), 5_000))
            }
            waitFor("Review transfer")
            ui.onNodeWithText("Review transfer").performClick()
            assertReview()
            assertTrue(peer.transactions.isEmpty())
            ui.onNodeWithText("Authenticate and send").performClick()
            waitForSystemPin(device)
            assertTrue(peer.transactions.isEmpty())
            enterRealSystemPin(device)
            assertPending()
            ui.waitUntil(5_000) { peer.assertHealthy(); peer.transactions.isNotEmpty() }
            val frame = peer.transactions.single()
            assertEquals(4, frame[0].toInt())
            assertEquals(1, ByteBuffer.wrap(frame, 13, 4).int)
            val wire = frame.copyOfRange(1, frame.size - 130)
            ByteBuffer.wrap(wire).putInt(12, 0)
            val payment = SaitoPayment.restore(Base64.getEncoder().encodeToString(wire))
            assertEquals(author, payment.author)
            assertEquals(recipient, payment.recipient)
            assertEquals(BigInteger("625000000"), payment.amount)
            assertEquals(BigInteger("10000000"), payment.fee)
            assertEquals(listOf(2, 0), payment.inputs.map { it.index })
            assertEquals(listOf(0, 7), payment.inputs.map { it.type })
            assertTrue(peer.clientKeys.all { it == author })
            peer.assertHealthy()
            capture("pending-before-death.png", pending)
            val row = JsonObject().apply {
                addProperty("phase", "pending"); addProperty("native", true)
                addProperty("author", author); addProperty("backend", AndroidBackendStore(context).load().id)
                addProperty("pid", Process.myPid()); addProperty("sig", payment.transaction.id)
                addProperty("frame", Base64.getEncoder().encodeToString(frame))
            }
            saveCheckpoint(row)
            Process.killProcess(Process.myPid())
            error("Expected actual process termination")
        }
    }

    @Test fun coldPendingTransferRequiresFreshAuthenticationBeforeExactRetry() {
        guard()
        val row = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertEquals("pending", row.get("phase").asString)
        val author = row.get("author").asString
        assertEquals(author, AndroidWalletStore(context).loadExisting().publicKey)
        assertEquals("owner", row.get("backend").asString)
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertTrue("The real system credential survives process death", keyguard.isDeviceSecure)
        val original = Base64.getDecoder().decode(row.get("frame").asString)
        val peer = ControlledFeedPeer("[]")
        launch(peer) {
            openTransfer(author)
            assertPending()
            assertTrue("Cold reopening must not send automatically", peer.transactions.isEmpty())
            peer.assertHealthy()
            capture("pending-cold-recovery.png", pending)
            ui.onNodeWithText("Retry transfer").performScrollTo().performClick()
            assertReview()
            assertTrue(peer.transactions.isEmpty())
            capture("cold-reviewed-retry.png", "Confirm SAITO transfer")
            ui.onNodeWithText("Authenticate and send").performClick()
            waitForSystemPin(device)
            assertTrue("The old authentication grant must not survive", peer.transactions.isEmpty())
            enterRealSystemPin(device)
            assertPending()
            ui.waitUntil(5_000) { peer.assertHealthy(); peer.transactions.isNotEmpty() }
            val retry = peer.transactions.single()
            assertArrayEquals("Retry must preserve the full original signed and routed frame", original, retry)
            assertEquals(author, AndroidWalletStore(context).loadExisting().publicKey)
            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
            assertTrue(peer.clientKeys.all { it == author }); peer.assertHealthy()
            capture("cold-exact-retry-pending.png", pending)
            File(context.cacheDir, "transfer-process-evidence/retry-frame.b64")
                .writeText(Base64.getEncoder().encodeToString(retry))
            row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid())
            saveCheckpoint(row)
        }
    }

    @Test fun screenLockIsClearedAfterProcessRecovery() {
        guard()
        ui.waitUntil(10_000) { !keyguard.isDeviceSecure }
        assertFalse(keyguard.isDeviceSecure)
    }

    private fun openTransfer(author: String) {
        waitFor("Wallet")
        ui.onNodeWithText("Wallet").performClick()
        ui.onNodeWithContentDescription("Wallet address").assertTextEquals(author)
        ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
    }

    private fun assertReview() {
        waitFor("Authenticate and send")
        ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
        ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
        ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
        ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
        ui.onNodeWithText("Memo: Native transfer test").assertIsDisplayed()
    }

    private fun assertPending() {
        waitFor(pending)
        ui.onNodeWithText("Available to send: 0 SAITO").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Transfer server: saito.davidengelen.nl.").assertIsDisplayed()
    }

    private fun launch(peer: ControlledFeedPeer, action: () -> Unit) {
        transferServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            peer.start().use { transport ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedDisabled", true)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${transport.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${server.port}/balance/")).use {
                    withFailureCapture({ capture("transfer-process-failure.png") }, action)
                }
            }
        }
    }

    private fun transferServer() = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
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
    }

    private fun saveCheckpoint(row: JsonObject) = checkpoint.outputStream().use { output ->
        output.write(row.toString().toByteArray()); output.fd.sync()
    }
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String, expected: String? = null) =
        captureNativeScreen(File(context.cacheDir, "transfer-process-evidence/$name"), expected)
}

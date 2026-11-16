package nl.davidengelen.redsquare

import android.app.KeyguardManager
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.TransferStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress

/** Actual editable wallet task recreation without preparing or publishing a transfer. */
class WalletTransferRecreationTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val values = listOf(
        "Recipient address" to "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU",
        "Amount in SAITO" to "1.2300",
        "Fee in SAITO" to "0.0120",
        "Public memo" to "Exact editable memo, before review",
    )

    @Test fun allFourEditableValuesAndTheActiveTaskSurviveRecreationWithoutPreparingATransfer() {
        withAppFrameFixture("editable-rotation", evidenceFolder = "wallet-input-evidence", feedRows = "[]") { activity ->
            ui.openWallet()
            val owner = displayedWalletAddress(ui)
            val before = TransferStore(context, owner).pending()
            ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
            values.forEach { (label, value) ->
                ui.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
            }
            assertEditableValues()
            captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/editable-before.png"))
            activity.recreate()
            ui.waitUntil(10_000) { ui.onAllNodes(hasContentDescription("Recipient address") and hasSetTextAction()).fetchSemanticsNodes().size == 1 }
            assertEditableValues()
            ui.onAllNodesWithText("Confirm SAITO transfer").assertCountEquals(0)
            ui.onAllNodesWithText("Authenticate and send").assertCountEquals(0)
            assertEquals(before, TransferStore(context, owner).pending())
            captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/editable-restored.png"))
        }
    }

    @Test fun cancelledAndRecreatedAuthenticationRequireFreshReviewAndARealNewGrant() = withDevicePin { device ->
        fundedTransferServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            withTransferRecords {
                lateinit var peer: ControlledFeedPeer
                withAppFrameFixture("authentication-rotation", evidenceFolder = "wallet-input-evidence", feedRows = "[]",
                    balanceApiUrl = "http://127.0.0.1:${server.port}/balance/", expectedTransactionCount = 1,
                    configurePeer = { peer = it }) { activity ->
                    ui.openWallet()
                    val owner = displayedWalletAddress(ui)
                    ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                    enterValues()
                    review(device)
                    ui.onNodeWithText("Authenticate and send").performClick()
                    waitForSystemPin(device)
                    assertTrue(peer.transactions.isEmpty())
                    cancelRealSystemAuthentication(device, context.packageName)
                    ui.waitUntil(15_000) { ui.onNodeWithText("Transfer cancelled before sending.").isDisplayed() }
                    assertTrue(TransferStore(context, owner).pending().isEmpty())
                    activity.recreate()
                    waitForEditableTask()
                    assertEditableValues()
                    ui.onAllNodesWithText("Authenticate and send").assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/cancelled-auth-restored.png"))

                    review(device)
                    ui.onNodeWithText("Authenticate and send").performClick()
                    waitForSystemPin(device)
                    val challenge = File(context.cacheDir, "wallet-input-evidence/recreation-original-challenge.xml")
                    challenge.parentFile!!.mkdirs()
                    device.dumpWindowHierarchy(challenge)
                    assertTrue(peer.transactions.isEmpty())
                    activity.recreate()
                    cancelRealSystemAuthentication(device, context.packageName)
                    waitForEditableTask()
                    assertEditableValues()
                    ui.onAllNodesWithText("Authenticate and send").assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    assertTrue(TransferStore(context, owner).pending().isEmpty())
                    captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/interrupted-auth-restored.png"))

                    review(device)
                    ui.onNodeWithText("Authenticate and send").performClick()
                    waitForSystemPin(device)
                    assertTrue(peer.transactions.isEmpty())
                    device.dumpWindowHierarchy(File(context.cacheDir, "wallet-input-evidence/fresh-authentication-challenge.xml"))
                    enterRealSystemPin(device)
                    ui.waitUntil(20_000) { peer.assertHealthy(); peer.transactions.size == 1 &&
                        ui.onNodeWithText("Pending. Inclusion has not been verified.").isDisplayed() }
                    val payment = TransferStore(context, owner).pending().single().payment
                    assertEquals(values[0].second, payment.recipient)
                    assertEquals(java.math.BigInteger("123000000"), payment.amount)
                    assertEquals(java.math.BigInteger("1200000"), payment.fee)
                    assertEquals(values[3].second, payment.memo)
                    captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/fresh-grant-submitted.png"))
                    File(context.cacheDir, "wallet-input-evidence/authentication.json").writeText(JsonObject().apply {
                        addProperty("cancelledGrantDidNotSend", true)
                        addProperty("actualChallengeRecreationReturnedToEditableDetails", true)
                        addProperty("freshReviewAndSystemPinRequired", true)
                        addProperty("ownedPeerTransactions", peer.transactions.size)
                    }.toString())
                }
            }
        }
    }

    @Test fun changedWalletOrNodeCannotInheritOrResurrectAnotherEditableTask() {
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
        assertNotEquals(original.publicKey, incoming.publicKey)
        var primary: Throwable? = null
        try {
            withAppFrameFixture("context-isolation", evidenceFolder = "wallet-input-evidence", feedRows = "[]", mapOfficialPeer = true) { activity ->
                val backends = AndroidBackendStore(context)
                ui.openWallet()
                assertEquals(original.publicKey, displayedWalletAddress(ui))
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                enterValues()
                // Persisted account/node changes enter through their real stores, then a real Activity reload.
                // Existing transfer status scenarios separately exercise Settings and the Android import picker.
                backends.save(SaitoBackend.OFFICIAL)
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Account").isDisplayed() }
                ui.onAllNodesWithContentDescription("Recipient address").assertCountEquals(0)
                ui.openWallet()
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                assertEmptyValues()
                captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/different-node-empty.png"))
                closeTaskAndWallet()
                backends.save(SaitoBackend.OWNER)
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Account").isDisplayed() }
                ui.onAllNodesWithContentDescription("Recipient address").assertCountEquals(0)
                ui.openWallet()
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                assertEmptyValues()
                enterValues()
                wallets.replace(original, incoming)
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Account").isDisplayed() }
                ui.onAllNodesWithContentDescription("Recipient address").assertCountEquals(0)
                ui.openWallet()
                assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                assertEmptyValues()
                captureNativeScreen(File(context.cacheDir, "wallet-input-evidence/different-wallet-empty.png"))
                closeTaskAndWallet()
                wallets.replace(incoming, original)
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Account").isDisplayed() }
                ui.onAllNodesWithContentDescription("Recipient address").assertCountEquals(0)
                ui.openWallet()
                assertEquals(original.publicKey, displayedWalletAddress(ui))
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                assertEmptyValues()
                closeTaskAndWallet()
                assertEquals(SaitoBackend.OWNER, backends.load())
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val current = wallets.loadExisting()
                when (current.exportJson()) {
                    original.exportJson() -> Unit
                    incoming.exportJson() -> wallets.replace(current, original)
                    else -> error("Unexpected editable-transfer fixture wallet")
                }
                check(wallets.loadExisting().exportJson() == original.exportJson())
            })
        }
    }

    private fun enterValues() = values.forEach { (label, value) ->
        ui.onNodeWithContentDescription(label).performScrollTo().performTextReplacement(value)
    }

    private fun waitForEditableTask() = ui.waitUntil(10_000) {
        ui.onAllNodes(hasContentDescription("Recipient address") and hasSetTextAction()).fetchSemanticsNodes().size == 1
    }

    private fun review(device: UiDevice) {
        ui.waitUntil(20_000) { ui.onNode(hasText("Review transfer") and isEnabled()).isDisplayed() }
        reviewNativeTransfer(ui, device)
        ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }
    }

    private fun assertEmptyValues() {
        values.forEach { (label, _) ->
            ui.onNodeWithContentDescription(label).performScrollTo().assertTextEquals(label, if (label == "Fee in SAITO") "0" else "")
        }
    }

    private fun closeTaskAndWallet() {
        ui.onNodeWithText("Cancel").performClick()
        ui.closeWalletOverview()
    }

    private fun withTransferRecords(block: () -> Unit) {
        val owner = AndroidWalletStore(context).loadOrCreate().publicKey
        val retained = ChatFixtureState(context, listOf("transfer.$owner."), owner)
        var primary: Throwable? = null
        try { retained.prepare(); block() }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }) }
    }

    private fun withDevicePin(block: (UiDevice) -> Unit) {
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var primary: Throwable? = null
        try {
            device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            block(device)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                ui.waitUntil(10_000) { !keyguard.isDeviceSecure }
            })
        }
    }

    private fun assertEditableValues() {
        values.forEach { (label, value) ->
            ui.onNodeWithContentDescription(label).performScrollTo().assertTextEquals(label, value)
        }
    }
}

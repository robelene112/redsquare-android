package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import com.google.gson.JsonObject
import org.junit.Rule
import org.junit.Test

/** Real TalkBack and offline speech read the reviewed recipient, amount, fee and node. */
class WalletTalkBackPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun reviewedPaymentValuesAndSelectedNodeAreSpokenWithoutSending() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "wallet-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            fundedTransferServer().use { balanceServer ->
                balanceServer.start(InetAddress.getByName("127.0.0.1"), 0)
                withAppFrameFixture("wallet-talkback", "wallet-talkback-evidence",
                    balanceApiUrl = "http://127.0.0.1:${balanceServer.port}/balance/") { activity ->
                    ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
                    ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val address = displayedWalletAddress(ui)
                    ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                    focusAndHear("recipient-field", "Recipient address", folder)

                    val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                    ui.onNodeWithContentDescription("Recipient address").performTextInput(recipient)
                    ui.onNodeWithContentDescription("Amount in SAITO").performTextInput("6.25")
                    ui.onNodeWithContentDescription("Fee in SAITO").performTextInput("0.1")
                    reviewNativeTransfer(ui, device)
                    ui.waitUntil(20_000) { ui.onNodeWithText("Authenticate and send").isDisplayed() }

                    var recipientSpoken = false
                    var amountSpoken = false
                    var feeSpoken = false
                    var selectedNodeSpoken = false
                    focusAndHear("reviewed-recipient", "Recipient: $recipient", folder,
                        textLabel = true, requireClickAction = false, completionTimeoutMillis = 20_000)
                    recipientSpoken = completedFragments(device, recipient).isNotEmpty()
                    focusAndHear("reviewed-amount", "Amount: 6.25 SAITO", folder,
                        textLabel = true, requireClickAction = false)
                    amountSpoken = completedFragments(device, "6.25 SAITO").isNotEmpty()
                    focusAndHear("reviewed-fee", "Fee: 0.1 SAITO", folder,
                        textLabel = true, requireClickAction = false)
                    feeSpoken = completedFragments(device, "0.1 SAITO").isNotEmpty()
                    focusAndHear("reviewed-node", "Send through saito.davidengelen.nl", folder,
                        textLabel = true, requireClickAction = false)
                    selectedNodeSpoken = completedFragments(device, "saito.davidengelen.nl").isNotEmpty()

                    ui.onNodeWithText("Recipient: $recipient").assertIsDisplayed()
                    ui.onNodeWithText("Amount: 6.25 SAITO").assertIsDisplayed()
                    ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                    ui.onNodeWithText("Send through saito.davidengelen.nl").assertIsDisplayed()
                    check(recipientSpoken && amountSpoken && feeSpoken && selectedNodeSpoken)
                    File(folder, "reviewed-payment-result.json").writeText(JsonObject().apply {
                        addProperty("walletPreserved", AndroidWalletStore(context).loadExisting().publicKey == address)
                        addProperty("recipientSpoken", recipientSpoken)
                        addProperty("amountSpoken", amountSpoken)
                        addProperty("feeSpoken", feeSpoken)
                        addProperty("selectedNodeSpoken", selectedNodeSpoken)
                        addProperty("outgoingTransactions", 0)
                        addProperty("physicalAudibility", false)
                        addProperty("serviceProcess", serviceProcess)
                    }.toString())
                    ui.onNodeWithText("Cancel").performClick()
                    ui.onNodeWithText("Done").performClick()
                    check(AndroidWalletStore(context).loadExisting().publicKey == address)
                }
            }
        }
    }
}

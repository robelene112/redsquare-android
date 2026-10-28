package nl.davidengelen.redsquare

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Wallet entry and payment details remain reachable at compact large-text viewports. */
class WalletFlowViewportPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun walletActionsAndPaymentInputsRemainReachableAcrossThemesAndLargeText() =
        MockWebServer().use { balanceServer ->
            balanceServer.dispatcher = object : Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder().code(503).build()
            }
            balanceServer.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("wallet-flow-viewport", "wallet-flow-viewport-evidence",
                balanceApiUrl = "http://127.0.0.1:${balanceServer.port}/balance/") { activity ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val folder = File(context.cacheDir, "wallet-flow-viewport-evidence").apply { mkdirs() }
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "wallet", scales = listOf(1.3f, 2f)) { label, _ ->
                ui.onNodeWithText("Wallet").performClick()
                ui.waitUntil(10_000) { ui.onAllNodesWithContentDescription("Wallet address")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Back up or restore").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Receive or send").performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-wallet-overview.png"), "Your wallet")

                val activeAddress = displayedWalletAddress(ui)
                val firstImportAddress = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
                val secondImportAddress = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                val incomingAddress = if (activeAddress == firstImportAddress) secondImportAddress else firstImportAddress
                val incomingFile = if (incomingAddress == firstImportAddress) "00-transfer-import-1.json" else "00-transfer-import-2.json"
                ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                chooseWalletImport(ui, incomingFile, incomingAddress)
                ui.onNodeWithText("Replace your wallet").performScrollTo().assertIsDisplayed()
                val importRisk = context.getString(R.string.wallet_import_work_warning)
                ui.onNodeWithText(importRisk).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-import-risk.png"))
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                ui.onNodeWithText("Review wallet import").assertIsDisplayed()
                ui.onNodeWithText("Back up or restore").assertDoesNotExist()
                ui.onNodeWithText("Receive or send").assertDoesNotExist()
                ui.onNodeWithText("Back").performClick()
                ui.onNodeWithText("Your wallet").assertIsDisplayed()
                ui.onNodeWithContentDescription("Wallet address").assertIsDisplayed()
                ui.onNodeWithText("Back up or restore").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Confirm replacement").assertDoesNotExist()
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                ui.onNodeWithText("Wallet").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Wallet address").isDisplayed() }
                ui.onNodeWithContentDescription("Wallet address").assertTextEquals(activeAddress)

                ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Receiving address").performScrollTo().assertIsDisplayed()
                ui.onNodeWithContentDescription("Receiving address QR code").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Your wallet").assertDoesNotExist()
                captureNativeScreen(File(folder, "$label-receive-address.png"), "Receiving address")
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Your wallet").isDisplayed() }
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                ui.onNodeWithText("Wallet").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Wallet address").isDisplayed() }

                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Recipient address").performScrollTo().assertIsDisplayed()
                ui.onNodeWithContentDescription("Amount in SAITO").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Amount in SAITO").performTextInput("1.25")
                ui.waitUntil(5_000) { focusedWindowShowsIme() }
                ui.onNodeWithText("Send SAITO").assertIsDisplayed()
                ui.onNodeWithText("Confirm SAITO transfer").assertDoesNotExist()
                ui.onNodeWithText("Authenticate and send").assertDoesNotExist()
                captureNativeScreen(File(folder, "$label-payment-details-ime.png"), "Amount in SAITO")

                ui.onNodeWithContentDescription("Public memo").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Public memo").performTextInput("viewport memo")
                ui.onNodeWithContentDescription("Public memo").performImeAction()
                ui.waitUntil(5_000) { !focusedWindowShowsIme() }
                ui.onNodeWithContentDescription("Fee in SAITO").performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Review transfer").assertIsDisplayed()
                ui.onNodeWithText("Authenticate and send").assertDoesNotExist()
                ui.onNodeWithText("Cancel").assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Your wallet").isDisplayed() }
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                assertTrue("Every viewport run must finish on the Home destination", ui.onNode(hasText("Home") and isSelected()).isDisplayed())
            }
            }
        }

    private fun focusedWindowShowsIme(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            visible = root?.let { ViewCompat.getRootWindowInsets(it) }
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}

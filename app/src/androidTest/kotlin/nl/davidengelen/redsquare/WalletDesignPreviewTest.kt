package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real account routes, accessible task headers and cancellation, retaining the live wallet. */
class WalletDesignPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun accountWalletAndSettingsHaveClearTaskHeadersAndReturnToTheSameFeed() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "wallet-design-evidence").apply { mkdirs() }
        withAppFrameFixture("account-return", "wallet-design-evidence") { _ ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
            ui.openWallet()
            ui.waitUntil(10_000) { ui.onNodeWithContentDescription("Wallet address").isDisplayed() }
            captureNativeScreen(File(folder, "wallet-before-header-assertion.png"))
            val overviewWindow = hasAnyAncestor(isDialog())
            ui.onNode(hasContentDescription("Switch app: Red Square") and overviewWindow).assertIsDisplayed()
            ui.onNode(hasContentDescription("Account") and overviewWindow).assertIsDisplayed()
            ui.onNode(hasText("Done") and overviewWindow).assertDoesNotExist()
            ui.onNodeWithText("Wallet").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            ui.onNode(hasText("Close wallet") and hasClickAction()).assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.openSettings()
            ui.onNodeWithText("Settings").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            captureNativeScreen(File(folder, "settings-overview.png"))
            ui.onNode(hasContentDescription("Switch app: Red Square") and overviewWindow).assertIsDisplayed()
            ui.onNode(hasContentDescription("Account") and overviewWindow).assertIsDisplayed()
            ui.onNode(hasText("Done") and overviewWindow).assertDoesNotExist()
            ui.onNodeWithText("Appearance").performScrollTo().performClick()
            ui.onNodeWithText("Appearance").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            ui.onNode(hasText("Back to Settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.onNode(hasText("Close settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }
    }

    @Test fun balanceAndPaymentReviewKeepTheirRealValuesAndCancelBackToTheWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.cacheDir, "wallet-design-evidence").apply { mkdirs() }
        fundedTransferServer().use { balanceServer ->
            balanceServer.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("review-return", "wallet-design-evidence",
                balanceApiUrl = "http://127.0.0.1:${balanceServer.port}/balance/") { _ ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                ui.openWallet()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("7.5 SAITO").isDisplayed() }
                captureNativeScreen(File(folder, "receive-before-header-assertion.png"))
                ui.onNodeWithText("Balance and receive").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                ui.onNode(hasText("Close balance and receive") and hasClickAction()).assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 7.5 SAITO").isDisplayed() }
                ui.onNodeWithText("Send SAITO").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                val recipient = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
                enterNativeTransfer(ui, recipient, "1.25", "0.1")
                reviewNativeTransfer(ui, androidx.test.uiautomator.UiDevice.getInstance(instrumentation))
                ui.waitUntil(15_000) { ui.onNodeWithText("Confirm SAITO transfer").isDisplayed() }
                ui.onNodeWithText("Amount: 1.25 SAITO").assertIsDisplayed()
                ui.onNodeWithText("Fee: 0.1 SAITO").assertIsDisplayed()
                ui.onNodeWithText(context.getString(R.string.transfer_recipient_review, recipient)).assertIsDisplayed()
                captureNativeScreen(File(folder, "actual-payment-review.png"))
                ui.onNode(hasText("Close transfer") and hasClickAction()).assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Wallet").isDisplayed() }
                ui.onNodeWithContentDescription("Wallet address").assertTextEquals(address)
                ui.closeWalletOverview()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }

}

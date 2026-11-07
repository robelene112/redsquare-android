package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Account identity and categories remain readable and reachable in both native densities. */
class AccountDesignViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun compactAccountJourneys() = ui.verifyAccountViewports(320)
}

class AccountDesignPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelAccountJourneys() = ui.verifyAccountViewports(412)
}

private fun ComposeTestRule.verifyAccountViewports(width: Int) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val folder = File(context.cacheDir, "account-design-viewport-evidence").apply { mkdirs() }
    withAppFrameFixture("account-$width", "account-design-viewport-evidence") { activity ->
        waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }
        onNodeWithText("Later").performClick()
        withPostViewports(activity, folder, "account", widthDp = width) { label, _ ->
            openWallet()
            displayedWalletAddress(this)
            val address = displayedWalletAddress(this)
            captureNativeScreen(File(folder, "$label-wallet-identity.png"))
            for (action in listOf("Export wallet", "Import wallet", "Balance and receive", "Send SAITO", "Recovery guide")) {
                onNode(hasText(action) and hasClickAction()).performScrollTo().assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
            }
            captureNativeScreen(File(folder, "$label-wallet-actions.png"))
            onNodeWithText("Recovery guide").performClick()
            waitUntil(10_000) { onNode(hasText("Close recovery guidance") and hasClickAction()).isDisplayed() }
            captureNativeScreen(File(folder, "$label-recovery.png"))
            onNode(hasText("Close recovery guidance") and hasClickAction()).performClick()
            waitUntil(10_000) { onNodeWithText("Your wallet").isDisplayed() }
            onNodeWithContentDescription("Wallet address").performScrollTo().assertTextEquals(address)
            onNode(hasText("Close wallet") and hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
            waitUntil(10_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
            openSettings()
            waitUntil(10_000) { onNodeWithText("Privacy and safety").isDisplayed() }
            captureNativeScreen(File(folder, "$label-settings.png"))
            onNodeWithText("Node").performScrollTo().performClick()
            for (domain in listOf("saito.io", "saito.davidengelen.nl")) {
                onNode(hasText(domain) and hasClickAction()).performScrollTo().assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
            }
            onNode(hasText("saito.davidengelen.nl") and isSelected()).assertIsDisplayed()
            captureNativeScreen(File(folder, "$label-node-choices.png"))
            onNode(hasText("Back to Settings") and hasClickAction()).performClick()
            onNodeWithText("Appearance").performScrollTo().performClick()
            for (theme in listOf("Light", "Dark", "System default")) {
                onNode(hasText(theme) and hasClickAction()).performScrollTo().assertHeightIsAtLeast(48.dp)
            }
            captureNativeScreen(File(folder, "$label-appearance.png"))
            onNode(hasText("Back to Settings") and hasClickAction()).performClick()
            onNode(hasText("Close settings") and hasClickAction()).performClick()
            waitUntil(10_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
        }
    }
}

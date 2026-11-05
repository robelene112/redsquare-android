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
            ui.onNodeWithText("Your wallet").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            ui.onNode(hasText("Close wallet") and hasClickAction()).assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.openSettings()
            ui.onNodeWithText("Settings").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            captureNativeScreen(File(folder, "settings-overview.png"))
            ui.onNodeWithText("Appearance").performScrollTo().performClick()
            ui.onNodeWithText("Appearance").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            ui.onNode(hasText("Back to Settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.onNode(hasText("Close settings") and hasClickAction()).assertIsDisplayed().performClick()
            ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
        }
    }
}

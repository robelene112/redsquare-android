package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against the isolated .dev application, never the signed user preview. */
@RunWith(AndroidJUnit4::class)
class WalletPreviewTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchCreatesAnIdentityAndReopeningWalletKeepsIt() {
        ui.onNodeWithText("Wallet").performClick()
        val address = displayedAddress()
        assertTrue(address.length == 44)
        ui.onNodeWithText("Done").performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Wallet").performClick()
        val reopenedAddress = displayedAddress()
        ui.onNodeWithContentDescription("Wallet address").assertIsDisplayed()
        assertEquals(address, reopenedAddress)
    }

    private fun displayedAddress(): String {
        ui.waitUntil(10_000) {
            ui.onAllNodes(androidx.compose.ui.test.hasContentDescription("Wallet address"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        return ui.onNodeWithContentDescription("Wallet address")
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    }
}

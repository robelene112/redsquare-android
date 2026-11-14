package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalCreationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun rentalCreationIsAnExplicitNativeChoiceWithoutPublishingOrMovingTheMaster() {
        withStoreSellerFixture("rental-entry") { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Rental duration (hours)").isDisplayed() }
            ui.onNodeWithText("The Vault master stays yours. Create a separate temporary access key.").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "native-rental-creation-entry.png"))
        }
    }
}

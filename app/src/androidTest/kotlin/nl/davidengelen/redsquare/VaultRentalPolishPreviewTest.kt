package nl.davidengelen.redsquare

import android.os.Debug
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual native creator-master discovery, with a bounded Android method trace. */
class VaultRentalPolishPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun creatorMasterDiscoveryRemainsUsableAndItsMainThreadWorkIsObservable() {
        withVaultReadingFixture("rental-discovery-profile", creatorRental = true) { scene ->
            Debug.startMethodTracing(File(scene.evidence, "creator-discovery.trace").absolutePath, 16 * 1024 * 1024)
            try {
                ui.openNativeVault()
                ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
                ui.waitUntil(15_000) { ui.onNode(hasText("Rental details") and isEnabled()).isDisplayed() }
                ui.onNodeWithText("Rental details").assertHeightIsAtLeast(48.dp)
            } finally { Debug.stopMethodTracing() }
            captureNativeScreen(File(scene.evidence, "creator-master-ready.png"))
        }
    }
}

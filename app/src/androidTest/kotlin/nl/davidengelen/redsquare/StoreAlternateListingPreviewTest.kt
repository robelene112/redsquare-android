package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreAlternateListingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anActualOfferedCurrencyRemainsInTheReviewedOriginalAcrossRecreationWithoutPublishing() {
        val provider = OwnedStoreCryptoFixture()
        withStoreSellerFixture("alternate-currency-review", crypto = provider) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Item title").performScrollTo().performTextReplacement("Native crypto sale")
            ui.onNodeWithText("Description").performScrollTo().performTextReplacement("An owned key offered for USDT.")
            ui.onNodeWithText("Payment currency: SAITO").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("USDT").isDisplayed() }
            ui.onNodeWithText("USDT").performClick()
            ui.onNodeWithText("Price per item (USDT)").performScrollTo().performTextReplacement("3.75")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Field notes.txt").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Field notes.txt").performScrollTo().performClick()
            ui.onNodeWithText("Review sale offer").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Quantity: 1 · 3.75 USDT each").isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
            val first = StoreListingStore(context, owner).read().single()
            assertEquals("USDT", first.listing.terms.currency)
            assertEquals("3.75", first.listing.terms.price)
            assertFalse(first.mayHaveSent)
            assertTrue(scene.peer.transactions.isEmpty()); assertEquals(0, provider.reservations)
            captureNativeScreen(File(scene.evidence, "alternate-original-before-authorization.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native crypto sale"))
            ui.onNodeWithText("Native crypto sale").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Quantity: 1 · 3.75 USDT each").isDisplayed() }
            assertEquals(first.listing.transaction.encoded(), StoreListingStore(context, owner).read().single().listing.transaction.encoded())
            assertTrue(scene.peer.transactions.isEmpty())
            ui.onNodeWithText("Discard unsent offer").performClick()
            ui.waitUntil(5_000) { StoreListingStore(context, owner).read().isEmpty() }
            captureNativeScreen(File(scene.evidence, "alternate-original-restored-and-discarded.png"))
        }
    }
}

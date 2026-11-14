package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalAccessPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private fun payAndOpen(scene: StorePurchaseScene, device: UiDevice) {
        scene.node.phase = OwnedStorePurchaseFixture.Phase.DELIVERED
        ui.openStorePurchaseProduct(illustrated = true)
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNodeWithText("Confirm and pay").performScrollTo().performClick()
        waitForSystemPin(device); assertTrue(scene.node.peer.transactions.isEmpty()); enterRealSystemPin(device)
        ui.waitUntil(25_000) { ui.onAllNodes(hasText("Article received") and hasAnyAncestor(isDialog())).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        // A received order must communicate success in the current viewport.
        ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        captureNativeScreen(File(scene.evidence, "rental-received-status-without-scrolling.png"))
        ui.onNode(hasText("Article received") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Open in Vault").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNodeWithText("Open in Vault").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Vault").isDisplayed() && ui.onNodeWithText("Upload notes.txt").isDisplayed() }
        assertEquals(1, scene.node.peer.transactions.size)
    }
    @Test fun anAuthenticatedReceivedRentalOpensItsVaultDestinationWithoutCopyingAnyKey() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-vault-entry", expectedTransactions = 1, rental = true) { scene ->
            payAndOpen(scene, device)
            captureNativeScreen(File(scene.evidence, "received-rental-native-vault-destination.png"))
            ui.onNodeWithText("Back to order").performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Check order").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Check order").performScrollTo().assertIsDisplayed()
            assertEquals(1, scene.node.peer.transactions.size)
        }
    }
    @Test fun activationAndOfflineExportSurviveRecreationAndProviderExpiryWithoutAnotherPayment() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-offline", expectedTransactions = 1, rental = true) { scene ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val destination = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-rental-saved.bin")
            context.contentResolver.call(destination, "prepare", "vault-rental-saved.bin", android.os.Bundle().apply { putByteArray("bytes", byteArrayOf()) })
            androidx.test.espresso.intent.Intents.init()
            try {
                androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_CREATE_DOCUMENT))
                    .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(destination).addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
                payAndOpen(scene, device)
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Keep offline") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
                val provider = requireNotNull(scene.rental)
                assertEquals(1, provider.checkouts)
                val reads = provider.reads
                scene.activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
                assertEquals(reads, provider.reads)
                provider.time = OwnedStoreRentalFixture.Time.AFTER; provider.available = false
                ui.onNodeWithText("Save as…").performScrollTo().performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() && ui.onNode(hasText("Save as…") and isEnabled()).isDisplayed() }
                val expected = java.util.Base64.getDecoder().decode(scene.node.reference.getAsJsonObject("rentalAccess").get("contentsBase64").asString)
                assertArrayEquals(expected, context.contentResolver.openInputStream(destination)!!.use { it.readBytes() })
                assertEquals(reads, provider.reads); assertEquals(1, provider.checkouts)
                assertEquals(1, scene.node.peer.transactions.size)
                captureNativeScreen(File(scene.evidence, "rental-cached-exact-export-after-expiry-outage.png"))
            } finally { androidx.test.espresso.intent.Intents.release() }
        }
    }
    @Test fun aBrowserPurchasedRentalAppearsWithoutAnyLocalOrderAndUsesItsActualProviderGrant() {
        withStorePurchaseFixture("rental-browser-import", rental = true, browserRental = true) { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertTrue(nl.davidengelen.redsquare.saito.StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Keep offline") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            assertEquals(1, requireNotNull(scene.rental).checkouts)
            assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "browser-rental-imported-wallet-native-vault.png"))
        }
    }
    @Test fun anEndedRentalHasNoNewPaymentAction() {
        withStorePurchaseFixture("rental-ended", rental = true, rentalClock = 1700012800000L) { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick()
            ui.onNodeWithText("Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }
            ui.onNodeWithText("Field notes access key").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Refresh Store") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("This rental has ended. It cannot be purchased again."))
            ui.onNodeWithText("This rental has ended. It cannot be purchased again.").assertIsDisplayed()
            ui.onAllNodesWithText("Review purchase").assertCountEquals(0)
            ui.onAllNodesWithText("Other payment methods").assertCountEquals(0)
            assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "ended-rental-no-financial-action.png"))
        }
    }
    @Test fun expiryDuringAuthenticationCannotCreateOrSendANewRentalPayment() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-auth-expiry", rental = true) { scene ->
            ui.openStorePurchaseProduct(illustrated = true)
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick()
            waitForSystemPin(device)
            scene.activity.onActivity { it.intent.putExtra("storeRentalFixtureTime", 1700012800000L) }
            enterRealSystemPin(device)
            ui.waitUntil(15_000) { ui.onNodeWithText("Check the quantity and fee, then refresh this offer. Its terms or available stock may have changed.").isDisplayed() }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertTrue(nl.davidengelen.redsquare.saito.StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().isEmpty())
            assertTrue(scene.node.peer.transactions.isEmpty())
        }
    }
    @Test fun expiryDuringFundingLookupKeepsTheUnsentOriginalWithoutBroadcast() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("rental-send-expiry", rental = true) { scene ->
            ui.openStorePurchaseProduct(illustrated = true)
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick()
            waitForSystemPin(device)
            val gate = java.util.concurrent.CountDownLatch(1)
            scene.node.fundingGate = gate
            try {
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { scene.node.fundingStarted.count == 0L }
                scene.activity.onActivity { it.intent.putExtra("storeRentalFixtureTime", 1700012800000L) }
                gate.countDown()
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Check the quantity and fee, then refresh this offer. Its terms or available stock may have changed.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val retained = nl.davidengelen.redsquare.saito.StoreOrderStore(context, scene.node.reference.get("buyer").asString).read().single()
                assertFalse(retained.mayHaveSent)
                assertTrue(scene.node.peer.transactions.isEmpty())
            } finally { gate.countDown(); scene.node.fundingGate = null }
        }
    }
    @Test fun aNewBrowserRentalReplacesTheOlderLocalRightForTheSameFile() = mixedRental(false)
    @Test fun anAlreadyActiveOlderLoanRemainsReadableDespiteANewerReceivedGrant() = mixedRental(true)
    private fun mixedRental(activeOld: Boolean) {
        withStorePurchaseFixture(if (activeOld) "rental-active-old-new-inventory" else "rental-new-browser-old-local", rental = true, browserRental = true, laterRental = true) { scene ->
            fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name).reader()
                .use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val offer = fixture("native-store-rental-listing-reference.json")
            val paid = fixture("native-store-rental-purchase-reference.json")
            val purchase = nl.davidengelen.redsquare.saito.SaitoStorePurchase.restore(paid.getAsJsonObject("purchase").get("networkBase64").asString)
            val product = requireNotNull(nl.davidengelen.redsquare.saito.SaitoStore.readListing(nl.davidengelen.redsquare.saito.SaitoStore.readCatalogue(offer.get("page").toString()).items.single(), offer.getAsJsonObject("listing").get("envelope").asString))
            val oldSnapshot = nl.davidengelen.redsquare.saito.SaitoBalanceSnapshot.parse(paid.getAsJsonObject("blocks").get("snapshot").asString.toByteArray(), purchase.buyer, 1700004000000)
            val observation = nl.davidengelen.redsquare.saito.SaitoStoreChain.observe(purchase, oldSnapshot, product.creator) { hash ->
                listOf("payment", "delivery", "refund").map { paid.getAsJsonObject("blocks").getAsJsonObject(it) }
                    .singleOrNull { it.get("hash").asString == hash }?.get("fullBase64")?.asString?.let { java.util.Base64.getDecoder().decode(it) }
            }
            assertTrue(observation.delivered)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val journal = nl.davidengelen.redsquare.saito.StoreOrderStore(context, purchase.buyer)
            val old = journal.reserve(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER, purchase, product)
            journal.observe(old.id, observation)
            requireNotNull(scene.rental).knownLoans = listOf(paid.getAsJsonObject("rentalAccess"))
            if (activeOld) { requireNotNull(scene.rental).activeLoan = paid.getAsJsonObject("rentalAccess"); requireNotNull(scene.rental).activated = true }
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Upload notes.txt").isDisplayed() }
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Keep offline") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Keep offline").performScrollTo().performClick()
            // The provider accepts only its actual active loan. Both independent signed grants remain known.
            ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
            assertEquals(if (activeOld) 0 else 1, requireNotNull(scene.rental).checkouts)
            assertEquals(1700012800000L, requireNotNull(nl.davidengelen.redsquare.saito.SaitoStoreRental.entitlement(purchase, product, requireNotNull(journal.read().single().receipt).encoded).grant).expiresAt)
            assertTrue(scene.node.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, if (activeOld) "active-old-loan-preserved-with-newer-grant.png" else "new-browser-rental-preferred-over-old-local-order.png"))
        }
    }
}

package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StorePurchaseRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun anObservedStoreRefundStaysDistinctFromDeliveryAndCannotBeRequestedManually() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("refund", 1) { scene ->
            scene.node.phase = OwnedStorePurchaseFixture.Phase.REFUNDED
            ui.openStorePurchaseProduct()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Refund observed on this node") and hasAnyAncestor(isDialog())).isDisplayed() }
            ui.onNodeWithText("Store refund reason: insufficient-quantity").assertIsDisplayed()
            ui.onAllNodesWithText("Article received").assertCountEquals(0)
            ui.onAllNodesWithText("Retry original payment").assertCountEquals(0)
            assertEquals(1, scene.node.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "actual-store-refund-separate-from-delivery.png"))
        }
    }
    @Test fun nodeAndAccountChangesDuringPinCannotSendTheReviewedPurchase() = withVaultDeviceCredential { device ->
        for (change in listOf("node", "account")) withStorePurchaseFixture("$change-authority") { scene ->
            ui.openStorePurchaseProduct()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device)
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val wallets = AndroidWalletStore(context); val original = wallets.loadExisting()
            val nodes = AndroidBackendStore(context)
            try {
                if (change == "node") nodes.save(SaitoBackend.OFFICIAL)
                else wallets.replace(original, SaitoWallet.fromPrivateKey("0".repeat(63) + "3"))
                enterRealSystemPin(device)
                ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.vault_key_context_changed)).isDisplayed() }
                assertTrue(scene.node.peer.transactions.isEmpty())
                assertTrue(StoreOrderStore(context, original.publicKey).read().isEmpty())
                captureNativeScreen(File(scene.evidence, "$change-change-revokes-purchase.png"))
            } finally {
                val current = wallets.loadExisting(); if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
                nodes.save(SaitoBackend.OWNER)
            }
        }
    }
    @Test fun anotherWalletOperationCanReserveReviewedFundsWithoutASecondSpend() = withVaultDeviceCredential { device ->
        withStorePurchaseFixture("reservation-race") { scene ->
            ui.openStorePurchaseProduct()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device)
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val wallet = AndroidWalletStore(context).loadExisting()
            val input = SaitoBalanceSnapshot.Slip(java.math.BigInteger.TEN, java.math.BigInteger.TWO, 0, java.math.BigInteger("1000000000"), 0)
            val competing = SaitoPayment.create(wallet, listOf(input), scene.node.reference.get("store").asString,
                java.math.BigInteger.ONE, java.math.BigInteger.ZERO, System.currentTimeMillis(), "store110-race", "")
            TransferStore(context, wallet.publicKey).reserve(SaitoBackend.OWNER, competing)
            enterRealSystemPin(device)
            ui.waitUntil(15_000) { ui.onNodeWithText(context.getString(R.string.store_purchase_connection)).isDisplayed() }
            assertTrue(scene.node.peer.transactions.isEmpty())
            assertTrue(StoreOrderStore(context, wallet.publicKey).read().isEmpty())
            assertEquals(competing.transaction.encoded(), TransferStore(context, wallet.publicKey).pending().single().payment.transaction.encoded())
            captureNativeScreen(File(scene.evidence, "reviewed-funding-reservation-prevents-second-spend.png"))
        }
    }
}

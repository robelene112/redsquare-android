package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreProceedsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun actualPendingAndEligibleProceedsRemainDistinctWithoutAnUnrequestedWithdrawal() {
        withStoreSellerFixture("proceeds", crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
            val original = requireNotNull(scene.peer.storeResponse)
            val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-seller-payments-reference.json")
                .reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            scene.peer.storeResponse = { query -> if (query.get("request").asString == "load-web3-proceeds")
                reference.get("eligible").toString() else original(query) }
            ui.openNativeStoreSelling()
            ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available to request: 6.25 USDT").isDisplayed() }
            ui.onNodeWithText("Waiting for settlement: 9.5 USDT").assertIsDisplayed()
            ui.onNodeWithText("Waiting for settlement: 0.1 BTC").performScrollTo().assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            assertTrue(scene.peer.storeRequests.none { it.get("request").asString == "store-withdrawal" })
            captureNativeScreen(File(scene.evidence, "seller-proceeds-distinct-before-any-withdrawal.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("New listing").isDisplayed() }
            ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available to request: 6.25 USDT").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty())
        }
    }
    @Test fun anInterruptedAuthorizedWithdrawalRetainsItsOriginalAcrossRecreationAndRequiresFreshAuthentication() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("withdrawal-original", crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val ref = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-seller-payments-reference.json")
                .reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val ordinary = requireNotNull(scene.peer.storeResponse)
            val signer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
            val originals = java.util.concurrent.CopyOnWriteArrayList<String>()
            var quotes = 0
            scene.peer.storeResponse = { query -> when (query.get("request").asString) {
                "load-web3-proceeds" -> ref.get("eligible").toString()
                "store-withdrawal-quote" -> {
                    quotes++
                    val data = ref.getAsJsonObject("quote").getAsJsonObject("data").deepCopy().apply {
                        addProperty("publickey", signer.publicKey); addProperty("timestamp", "${System.currentTimeMillis()}-1")
                        addProperty("withdrawal_address", query.getAsJsonObject("data").get("withdrawal_address").asString)
                    }
                    val sorted = com.google.gson.JsonObject().apply { data.keySet().sorted().forEach { add(it, data.get(it)) } }
                    com.google.gson.JsonObject().apply { add("data", data); addProperty("signature", signer.sign(sorted.toString().toByteArray())) }.toString()
                }
                "store-withdrawal" -> {
                    originals.add(query.getAsJsonObject("data").get("transaction").asString)
                    ref.get("submitted").toString()
                }
                else -> ordinary(query)
            } }
            ui.openNativeStoreSelling(); ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Available to request: 6.25 USDT").isDisplayed() }
            ui.onNodeWithText("Withdraw USDT").performScrollTo().performClick()
            ui.onNodeWithText("Destination address").performScrollTo().performClick()
            ui.waitUntil(15_000) {
                var visible = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    visible = android.view.inspector.WindowInspector.getGlobalWindowViews().any { root -> root.isShown && root.hasWindowFocus() &&
                        androidx.core.view.ViewCompat.getRootWindowInsets(root)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                }
                visible
            }
            ui.onNodeWithText("Destination address").assertIsFocused().performTextReplacement("0x1111111111111111111111111111111111111111")
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            ui.onNodeWithText("Review withdrawal").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Requested payout: 6.25 USDT").isDisplayed() }
            ui.onNodeWithText("Service fee: 0.25 SAITO").performScrollTo().assertIsDisplayed()
            val journal = StoreWithdrawalStore(context, SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey)
            val original = journal.read().single()
            assertFalse(original.mayHaveSent); assertTrue(originals.isEmpty()); assertEquals(1, quotes)
            assertTrue(TransferStore(context, original.candidate.author).available(original.candidate.inputs).isEmpty())
            scene.peer.disconnectAfterStoreWithdrawal = true
            ui.onNodeWithText("Authorize withdrawal").assertIsDisplayed().performClick()
            waitForSystemPin(device); assertTrue(originals.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Retry original request") and isEnabled()).isDisplayed() }
            assertEquals(1, originals.size); assertEquals(original.candidate.transaction.envelope(), originals.single())
            assertTrue(journal.read().single().mayHaveSent)
            captureNativeScreen(File(scene.evidence, "withdrawal-uncertain-original-retained.png"))
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNodeWithText("New listing").isDisplayed() }
            ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Withdrawal · 6.25 USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Withdrawal · 6.25 USDT").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Withdrawal · 6.25 USDT").performClick()
            assertEquals(1, originals.size); assertEquals(1, quotes)
            assertEquals(original.candidate.transaction.encoded(), journal.read().single().candidate.transaction.encoded())
            scene.peer.disconnectAfterStoreWithdrawal = false
            ui.onNodeWithText("Retry original request").assertIsDisplayed().performClick()
            waitForSystemPin(device); assertEquals(1, originals.size); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Requested from the treasury. External funds have not been sent.").isDisplayed() }
            assertEquals(listOf(original.candidate.transaction.envelope(), original.candidate.transaction.envelope()), originals.toList())
            assertEquals(1, quotes); assertEquals(SaitoStoreWithdrawal.State.SUBMITTED, journal.read().single().result?.state)
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "withdrawal-original-manual-treasury-request.png"))
        }
    }
}

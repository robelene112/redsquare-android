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
}

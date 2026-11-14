package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

/** Real encrypted original requests must participate in the shared wallet replacement guard. */
class StoreWithdrawalReservationPreviewTest {
    @Test fun aZeroFeeWithdrawalIsRetainedWalletWorkUntilItIsExplicitlyDiscarded() {
        withStoreSellerFixture("withdrawal-retained-work") { _, _, _ ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-seller-payments-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val candidate = SaitoStoreWithdrawal.restore(source.getAsJsonObject("zero").getAsJsonObject("candidate").get("networkBase64").asString,
                source.get("store").asString)
            assertTrue(candidate.inputs.isEmpty())
            val transfers = TransferStore(context, candidate.author)
            assertFalse(transfers.hasWork())
            val journal = StoreWithdrawalStore(context, candidate.author)
            val metadata = OwnedStoreCryptoFixture().methods.getAsJsonArray("ac").map { it.asJsonObject }.single { it.get("ticker").asString == "USDT" }
            val saved = journal.reserve(SaitoBackend.OWNER, candidate, metadata.get("chain_id").asString, "Ethereum")
            assertTrue("A fee-free original still requires the wallet replacement warning", transfers.hasWork())
            val restored = StoreWithdrawalStore(context, candidate.author).read().single()
            assertEquals(candidate.transaction.encoded(), restored.candidate.transaction.encoded())
            assertThrows(IllegalArgumentException::class.java) { journal.discard(saved.copy(backend = SaitoBackend.OFFICIAL)) }
            assertTrue(transfers.hasWork())
            journal.discard(restored)
            assertFalse(transfers.hasWork())
        }
    }
}

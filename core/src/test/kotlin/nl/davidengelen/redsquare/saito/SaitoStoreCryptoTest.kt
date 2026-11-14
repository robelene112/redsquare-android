package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SaitoStoreCryptoTest {
    private fun fixture() = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-crypto-reference.json"))
        .bufferedReader().use { it.readText() }).asJsonObject

    @Test fun anExternalPaymentCarrierMatchesTheOfficialUnfundedPurchaseAndNeverSpendsSaito() {
        val source = fixture()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val carrier = SaitoStoreCarrier.create(wallet, source.get("store").asString, source.get("nftId").asString,
            BigInteger.valueOf(2), StorePrice("USDT", BigDecimal("3.75")), source.get("timestamp").asLong)
        assertEquals(source.getAsJsonObject("carrier").get("networkBase64").asString, carrier.transaction.encoded())
        assertEquals(BigDecimal("7.5"), carrier.total.stripTrailingZeros())
        val restored = SaitoStoreCarrier.restore(carrier.transaction.encoded())
        assertEquals(carrier.transaction.id, restored.transaction.id)
        assertEquals(wallet.publicKey, restored.buyer)
        assertThrows(IllegalArgumentException::class.java) { SaitoStorePurchase.restore(carrier.transaction.encoded()) }
    }
    @Test fun paymentMethodsComeFromTheActualServiceResponseRatherThanAnInventedList() {
        val source = fixture()
        val methods = SaitoStoreCrypto.readMethods(source.get("methods").toString())
        assertEquals(listOf("BNB", "BTC", "ETH", "USDC", "USDT", "SOL", "TRX"), methods.currencies.map { it.ticker })
        assertEquals("43d61dcd-e413-450d-80b8-101d5e903357", methods.currencies.single { it.ticker == "USDT" }.chainId)
        assertEquals(BigDecimal("0.00029596"), methods.saitoUsd)
        assertFalse(methods.verifiedInvoices)
        assertThrows(SaitoStoreCrypto.Unavailable::class.java) { SaitoStoreCrypto.readMethods("{\"err\":\"Provider is not ready\"}") }
    }

}

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

    @Test fun aProviderInvoiceMustBindTheOriginalOrderExactAmountAndNetworkBeforeItIsPayable() {
        val vector = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-provider-reference.json"))
            .bufferedReader().use { it.readText() }).asJsonObject
        val carrier = SaitoStoreCarrier.restore(vector.getAsJsonObject("carrier").get("networkBase64").asString)
        val methods = SaitoStoreCrypto.readMethods(fixture().get("methods").toString())
        val currency = methods.currencies.single { it.ticker == "USDT" }
        val quote = vector.getAsJsonObject("quote")
        val invoice = SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("provider").asString, quote.toString())
        assertEquals(BigDecimal("7.5"), invoice.amount.stripTrailingZeros())
        assertEquals(currency.chainId, invoice.currency.chainId)
        assertTrue(invoice.payable(1700003000000))
        assertFalse(invoice.payable(invoice.expiresAt))
        assertThrows(SaitoStoreCrypto.Conflict::class.java) { SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("provider").asString,
            quote.deepCopy().apply { addProperty("purchase_signature", "ab".repeat(64)) }.toString()) }
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("provider").asString,
            quote.deepCopy().apply { addProperty("expected_deposit", "7.6") }.toString()) }
        assertThrows(IllegalArgumentException::class.java) { SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("provider").asString,
            quote.deepCopy().apply { addProperty("chain_id", "25dabac5-056a-48ff-b9f9-f67395dc407c") }.toString()) }
        assertThrows(SaitoStoreCrypto.Unsupported::class.java) { SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("provider").asString,
            quote.deepCopy().apply { remove("purchase_signature") }.toString()) }
    }

    @Test fun providerPaymentAndStoreDeliveryRemainBoundToTheOriginalBuyerAndExactAsset() {
        val vector = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-crypto-settlement-reference.json"))
            .bufferedReader().use { it.readText() }).asJsonObject
        val carrier = SaitoStoreCarrier.restore(vector.getAsJsonObject("carrier").get("networkBase64").asString)
        val currency = SaitoStoreCrypto.readMethods(fixture().get("methods").toString()).currencies.single { it.ticker == "USDT" }
        val invoice = SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("store").asString, vector.get("invoice").toString())
        val payment = SaitoStoreIssuance.read(carrier, invoice, vector.get("providerStatus").toString())
        assertEquals(vector.getAsJsonObject("purchase").get("networkBase64").asString, payment.transaction.encoded())
        assertEquals(carrier.buyer, payment.buyer)
        assertNotEquals(carrier.buyer, payment.author)
        assertEquals(invoice.issuedSaito.movePointRight(8).toBigIntegerExact(), payment.amount)
        assertThrows(IllegalArgumentException::class.java) { SaitoStorePurchase.restore(payment.transaction.encoded()) }
        val fulfillment = vector.getAsJsonObject("settlement").getAsJsonObject("fulfillment").get("networkBase64").asString
        val receipt = SaitoStoreReceipt.read(payment, fulfillment)
        assertEquals(carrier.buyer, receipt.asset?.owner)
        assertEquals(carrier.quantity, receipt.asset?.quantity)
        val blocks = vector.getAsJsonObject("blocks")
        val snapshot = SaitoBalanceSnapshot.parse(blocks.get("snapshot").asString.toByteArray(), carrier.buyer, 1700004003000)
        val known = listOf("payment", "delivery").associate { blocks.getAsJsonObject(it).let { block ->
            block.get("hash").asString to java.util.Base64.getDecoder().decode(block.get("fullBase64").asString)
        } }
        val observation = SaitoStoreChain.observe(payment, snapshot, requireNotNull(receipt.asset).creator) { known[it] }
        assertTrue(observation.delivered)
        assertEquals(payment.transaction.id, vector.getAsJsonObject("purchase").get("id").asString)
        assertEquals(receipt.signature, observation.receipt?.signature)
    }

    @Test fun aLateProviderFailureCannotEraseAnAuthenticOriginalPaymentOrTurnARefundIntoForeignCurrency() {
        val vector = JsonParser.parseString(requireNotNull(javaClass.getResourceAsStream("/native-store-crypto-settlement-reference.json"))
            .bufferedReader().use { it.readText() }).asJsonObject
        val carrier = SaitoStoreCarrier.restore(vector.getAsJsonObject("carrier").get("networkBase64").asString)
        val currency = SaitoStoreCrypto.readMethods(fixture().get("methods").toString()).currencies.single { it.ticker == "USDT" }
        val invoice = SaitoStoreCrypto.readInvoice(carrier, currency, vector.get("store").asString, vector.get("invoice").toString())
        for (state in listOf("failed", "succeeded", "outbound_confirmed")) {
            val status = vector.getAsJsonObject("providerStatus").deepCopy().apply { addProperty("transfer_status", state) }
            val payment = SaitoStoreIssuance.read(carrier, invoice, status.toString())
            assertEquals(vector.getAsJsonObject("purchase").get("id").asString, payment.transaction.id)
            val refund = SaitoStoreReceipt.read(payment, vector.getAsJsonObject("settlement").getAsJsonObject("refund").get("networkBase64").asString)
            assertEquals(SaitoStoreReceipt.Kind.REFUND, refund.kind)
            assertEquals(invoice.issuedSaito.movePointRight(8).toBigIntegerExact(), refund.refundOutput?.amount)
        }
    }

}

package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.Base64

/** Owned provider adapter. Official patched-service and WASM vectors independently verify its contract. */
internal class OwnedStoreCryptoFixture {
    private fun reference(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name).reader()
        .use { JsonParser.parseReader(it).asJsonObject }
    val source = reference("native-store-provider-reference.json")
    val methods = reference("native-store-crypto-reference.json").getAsJsonObject("methods").deepCopy().apply {
        addProperty("store_purchase_protocol", 1)
        getAsJsonArray("ac").forEach { it.asJsonObject.apply { addProperty("price_usd", 1); addProperty("last_update", System.currentTimeMillis()) } }
        addProperty("erc", "0.001")
    }
    val chains = reference("native-store-chains-observation.json")
    private val signer = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    lateinit var node: OwnedStorePurchaseFixture
    @Volatile var available = true
    @Volatile var reservationLifetime = 2_400_000L
    @Volatile var supported = true
    @Volatile var conflicting = false
    @Volatile var missingTransactionCopy = false
    @Volatile var reservations = 0
        private set
    @Volatile var statusReads = 0
        private set
    @Volatile private var carrier: SaitoStoreCarrier? = null
    @Volatile private var originalQuote: JsonObject? = null
    @Volatile private var originalPayment: SaitoStoreIssuance? = null
    val original: SaitoStoreCarrier? get() = carrier
    val payment: SaitoStoreIssuance? get() = if (node.phase == OwnedStorePurchaseFixture.Phase.WAITING) null else {
        if (originalPayment == null && carrier != null) originalPayment = SaitoStoreIssuance.read(requireNotNull(carrier), invoice(), fundedStatus())
        originalPayment
    }
    @Synchronized fun checkpoint(): JsonObject = JsonObject().apply {
        addProperty("carrier", requireNotNull(carrier).transaction.encoded())
        add("quote", requireNotNull(originalQuote).deepCopy())
        addProperty("reservationCount", reservations)
    }
    @Synchronized fun restoreCheckpoint(value: JsonObject) {
        check(carrier == null && originalQuote == null && reservations == 0)
        carrier = SaitoStoreCarrier.restore(value.get("carrier").asString)
        originalQuote = value.getAsJsonObject("quote").deepCopy()
        invoice() // Admit the actual bound provider record before starting the cold peer.
    }
    fun restore(order: StoreOrderStore.Order) {
        val saved = requireNotNull(order.external)
        carrier = saved.carrier
        originalQuote = saved.response?.let { JsonParser.parseString(it).asJsonObject }
        originalPayment = saved.issuance
    }
    @Synchronized fun reply(signed: SignedSaitoMessage): String {
        val request = signed.body.get("request").asString
        val data = signed.body.getAsJsonObject("data")
        if (request == "store-web3-status") return JsonObject().apply { addProperty("enabled", available) }.toString()
        if (!available) return "{\"err\":\"Payment provider unavailable\"}"
        if (request == "buysaito available currencies") return methods.deepCopy().apply {
            if (!supported) remove("store_purchase_protocol")
            getAsJsonArray("ac").forEach { it.asJsonObject.addProperty("last_update", System.currentTimeMillis()) }
        }.toString()
        if (request == "buysaito purchase status") {
            statusReads++
            if (carrier == null) return "{\"found\":false,\"store_purchase_protocol\":1}"
            check(signed.author == carrier?.buyer && data.get("request_id").asString == carrier?.transaction?.id)
            return if (node.phase == OwnedStorePurchaseFixture.Phase.WAITING) requireNotNull(originalQuote).deepCopy().apply {
                addProperty("found", true)
            }.toString() else fundedStatus().let { text ->
                if (!missingTransactionCopy) text else JsonParser.parseString(text).asJsonObject.apply { addProperty("outbound_tx", "") }.toString()
            }
        }
        check(request == "buysaito reserve address")
        reservations++
        if (originalQuote == null) {
            val envelope = JsonParser.parseString(data.get("tx").asString).asJsonObject
            val header = Base64.getDecoder().decode(envelope.get("t").asString)
            val message = Base64.getDecoder().decode(envelope.get("m").asString)
            ByteBuffer.wrap(header).putInt(8, message.size)
            val original = SaitoStoreCarrier.restore(Base64.getEncoder().encodeToString(header + message))
            check(signed.author == original.buyer && data.get("request_id").asString == original.transaction.id)
            carrier = original
            val ticker = data.get("ticker").asString
            val currency = methods.getAsJsonArray("ac").map { it.asJsonObject }.single { it.get("ticker").asString == ticker }
            val amount = data.get("expected_deposit").asString.toBigDecimal()
            val issued = if (original.price.currency == "SAITO") original.total else amount.divide(BigDecimal("0.0011"), 0, RoundingMode.FLOOR)
            originalQuote = source.getAsJsonObject("quote").deepCopy().apply {
                addProperty("request_id", original.transaction.id); addProperty("purchase_signature", original.transaction.id)
                addProperty("ticker", ticker); addProperty("chain_id", currency.get("chain_id").asString)
                addProperty("expected_deposit", amount); addProperty("issue_amount", issued)
                addProperty("reserved_until", original.timestamp + reservationLifetime)
                // The address is a controlled destination. It is never usable on a live network.
                addProperty("address", "owned-${ticker.lowercase()}-payment-address")
            }
        }
        return requireNotNull(originalQuote).deepCopy().apply {
            if (conflicting) addProperty("purchase_signature", "cd".repeat(64))
        }.toString()
    }
    @Synchronized private fun invoice(): SaitoStoreCrypto.Invoice {
        val quote = requireNotNull(originalQuote)
        val ticker = quote.get("ticker").asString
        val currency = SaitoStoreCrypto.readMethods(methods.toString()).currencies.single { it.ticker == ticker }
        return SaitoStoreCrypto.readInvoice(requireNotNull(carrier), currency, signer.publicKey, quote.toString())
    }
    private fun fundedStatus(): String {
        val original = requireNotNull(carrier)
        val quote = requireNotNull(originalQuote)
        val body = SaitoEnvelope.decode(original.transaction.envelope()).body.deepCopy().apply {
            addProperty("inbound_payment_uuid", "ab".repeat(32)); addProperty("transfer_uuid", quote.get("id").asString)
        }
        val template = Base64.getDecoder().decode(source.getAsJsonObject("issuance").get("networkBase64").asString)
        val inputs = ByteBuffer.wrap(template).getInt(0)
        val offset = 93 + 59 * (inputs + 2)
        val message = body.toString().toByteArray()
        val wire = template.copyOf(offset) + message
        val header = ByteBuffer.wrap(wire).putInt(8, message.size).putLong(80, original.timestamp + 1000)
        val amount = quote.get("issue_amount").asString.toBigDecimal().movePointRight(8).toBigIntegerExact()
        val total = (1 until inputs).fold(BigInteger.ZERO) { sum, index -> sum + BigInteger(1, wire.copyOfRange(93 + 59 * index + 33, 93 + 59 * index + 41)) }
        header.putLong(93 + 59 * inputs + 33, amount.also { check(it.signum() > 0 && it.bitLength() <= 63) }.toLong())
        header.putLong(93 + 59 * (inputs + 1) + 33, (total - amount - BigInteger("10000000")).also { check(it.signum() >= 0 && it.bitLength() <= 63) }.toLong())
        signer.sign(preimage(wire)).chunked(2).map { it.toInt(16).toByte() }.toByteArray().copyInto(wire, 16)
        val detached = wire.copyOf(offset).also { ByteBuffer.wrap(it).putInt(8, 0) }
        val envelope = JsonObject().apply {
            addProperty("t", Base64.getEncoder().encodeToString(detached)); addProperty("m", Base64.getEncoder().encodeToString(message))
            addProperty("opt", Base64.getEncoder().encodeToString("{}".toByteArray()))
        }
        return quote.deepCopy().apply {
            addProperty("found", true); addProperty("transfer_status", "outbound_awaiting")
            addProperty("inbound_payment_uuid", "ab".repeat(32)); addProperty("outbound_tx", envelope.toString())
            addProperty("outbound_tx_sig", wire.copyOfRange(16, 80).joinToString("") { "%02x".format(it.toInt() and 255) })
        }.toString()
    }
    private fun preimage(wire: ByteArray): ByteArray {
        val header = ByteBuffer.wrap(wire); val count = header.getInt(0) + header.getInt(4); val offset = 93 + count * 59
        val output = ByteBuffer.allocate(16 + 43 * count + header.getInt(8)).putLong(header.getLong(80))
        repeat(count) { index -> val position = 93 + index * 59; output.put(wire, position, 41).put(wire, position + 57, 2) }
        return output.putInt(header.getInt(88)).putInt(wire[92].toInt()).put(wire, offset, header.getInt(8)).array()
    }
}

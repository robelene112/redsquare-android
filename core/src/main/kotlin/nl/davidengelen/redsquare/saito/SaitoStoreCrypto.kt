package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.net.URI

/** Actual provider capabilities and quoted payment metadata, distinct from payment or entitlement. */
object SaitoStoreCrypto {
    class Unavailable : IllegalStateException("The payment provider is unavailable")
    class Unsupported : IllegalStateException("This provider cannot verify payment instructions")
    class Conflict(val original: String) : IllegalStateException("The provider has another original payment")
    enum class TransferState(val wire: String) {
        AWAITING("inbound_awaiting"), DETECTED("inbound_broadcast"), RECEIVED("inbound_confirmed"),
        ISSUING("outbound_awaiting"), BROADCAST("outbound_broadcast"), FORWARDED("treasury_submitted"),
        ACCEPTED("treasury_accepted"), INCLUDED("outbound_confirmed"), FAILED("failed"), CANCELLED("cancelled"),
        HISTORICAL("succeeded"), REJECTED("treasury_rejected")
    }
    @ConsistentCopyVisibility
    data class Invoice internal constructor(val id: String, val carrierSignature: String, val provider: String,
        val currency: Currency, val amount: BigDecimal, val issuedSaito: BigDecimal, val issuer: String,
        val address: String, val mixinId: String, val expiresAt: Long, val state: TransferState) {
        fun payable(now: Long): Boolean = state == TransferState.AWAITING && now >= 0 && now < expiresAt
    }
    data class Currency(val ticker: String, val chainId: String, val usd: BigDecimal, val updatedAt: Long)
    data class Methods(val currencies: List<Currency>, val saitoUsd: BigDecimal?, val verifiedInvoices: Boolean)

    fun methodsRequest(): JsonObject = JsonObject().apply {
        addProperty("request", "buysaito available currencies")
        add("data", JsonObject().apply { addProperty("module", "Mixin") })
    }
    fun statusRequest(): JsonObject = JsonObject().apply {
        addProperty("request", "store-web3-status")
        add("data", JsonObject().apply { addProperty("module", "Store") })
    }
    fun readEnabled(response: String): Boolean {
        require(response.toByteArray().size <= 16 * 1024)
        val value = JsonParser.parseString(response).asJsonObject.get("enabled")
        require(value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }
    fun readMethods(response: String): Methods {
        require(response.toByteArray().size <= 128 * 1024)
        val value = JsonParser.parseString(response).asJsonObject
        if (value.has("err") || value.get("ac")?.isJsonArray != true) throw Unavailable()
        val rows = value.getAsJsonArray("ac").also { require(it.size() <= 128) }
        val methods = linkedMapOf<String, Currency>()
        for (entry in rows) {
            val item = entry.asJsonObject
            val ticker = item.get("ticker")?.asString.orEmpty()
            // These are excluded by the pinned Mixin service, not an invented allowed list.
            if (ticker in setOf("ERC-SAITO", "BEP-SAITO")) continue
            require(ticker.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}")))
            val price = item.get("price_usd")?.asString?.toBigDecimalOrNull() ?: continue
            if (price.signum() <= 0 || !price.toDouble().isFinite()) continue
            require(price.precision() <= 32 && price.scale() in -32..32)
            val chain = item.get("chain_id").asString.also { require(it.matches(uuid)) }
            val time = item.get("last_update").asString.toBigDecimal().toBigIntegerExact()
            require(time.signum() > 0 && time.bitLength() <= 63)
            val currency = Currency(ticker, chain, price, time.toLong())
            require(methods[ticker]?.let { it == currency } != false)
            methods[ticker] = currency
        }
        val erc = value.get("erc")?.takeUnless { it.isJsonNull }?.asString?.toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 && it.toDouble().isFinite() && it.precision() <= 32 && it.scale() in -32..32 }
        val verified = value.get("store_purchase_protocol")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.toBigDecimal().compareTo(BigDecimal.ONE) == 0
        } ?: false
        return Methods(java.util.Collections.unmodifiableList(methods.values.toList()), erc, verified)
    }

    fun reservationRequest(carrier: SaitoStoreCarrier, currency: Currency, expectedDeposit: BigDecimal): JsonObject {
        require(currency.ticker != "SAITO" && expectedDeposit.signum() > 0)
        return JsonObject().apply {
            addProperty("request", "buysaito reserve address")
            add("data", JsonObject().apply {
                addProperty("module", "Mixin"); addProperty("initiator_pubkey", carrier.buyer)
                addProperty("recipient_pubkey", carrier.buyer); addProperty("ticker", currency.ticker)
                addProperty("expected_deposit", expectedDeposit)
                if (carrier.price.currency == "SAITO") addProperty("issue_amount", carrier.total)
                addProperty("request_id", carrier.transaction.id); addProperty("tx", carrier.transaction.envelope())
            })
        }
    }
    fun statusRequest(carrier: SaitoStoreCarrier, currency: Currency): JsonObject = JsonObject().apply {
        addProperty("request", "buysaito purchase status")
        add("data", JsonObject().apply {
            addProperty("module", "Mixin"); addProperty("ticker", currency.ticker)
            addProperty("request_id", carrier.transaction.id)
        })
    }
    /** Match the provider's IEEE-754 calculation and upward six-decimal rounding before requesting instructions. */
    fun estimate(price: StorePrice, quantity: java.math.BigInteger, currency: Currency, saitoUsd: BigDecimal?, now: Long): BigDecimal {
        require(quantity.signum() > 0 && quantity <= maximum)
        if (now <= 0 || currency.updatedAt > now + 60_000 || now - currency.updatedAt > 300_000) throw Unavailable()
        val total = money((price.amount * quantity.toBigDecimal()).stripTrailingZeros().toPlainString())
        if (price.currency != "SAITO") {
            require(price.currency == currency.ticker)
            return total
        }
        require(total.movePointRight(8).toBigIntegerExact() <= maximum)
        val saito = saitoUsd?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: throw Unavailable()
        val usd = currency.usd.toDouble().takeIf { it.isFinite() && it > 0 } ?: throw Unavailable()
        val quoted = kotlin.math.ceil(((total.toDouble() * maxOf(0.001, 1.1 * saito)) / usd) * 1_000_000) / 1_000_000
        if (!quoted.isFinite() || quoted <= 0) throw Unavailable()
        return money(BigDecimal.valueOf(quoted).stripTrailingZeros().toPlainString())
    }

    fun readInvoice(carrier: SaitoStoreCarrier, currency: Currency, provider: String, response: String): Invoice {
        require(SaitoWallet.isPublicKey(provider) && response.toByteArray().size <= 128 * 1024)
        val quote = JsonParser.parseString(response).asJsonObject
        if (quote.has("err")) throw Unavailable()
        if (quote.get("store_purchase_protocol")?.asString != "1" ||
            !quote.has("purchase_signature") || !quote.has("request_id")) throw Unsupported()
        val original = quote.get("request_id").asString
        val signature = quote.get("purchase_signature").asString
        if (original != carrier.transaction.id || signature != carrier.transaction.id) throw Conflict(original)
        require(quote.get("initiator_pubkey").asString == carrier.buyer && quote.get("recipient_pubkey").asString == carrier.buyer)
        require(quote.get("ticker").asString == currency.ticker && quote.get("chain_id").asString == currency.chainId)
        val amount = money(quote.get("expected_deposit").asString)
        val issued = money(quote.get("issue_amount").asString)
        require(issued.movePointRight(8).toBigIntegerExact() <= maximum)
        if (carrier.price.currency == "SAITO") require(issued.compareTo(carrier.total) == 0)
        else require(carrier.price.currency == currency.ticker && amount.compareTo(carrier.total) == 0)
        val id = quote.get("id").asString.also { require(it.matches(Regex("[1-9][0-9]{0,15}")) && it.toBigInteger() <= maximum) }
        val address = quote.get("address").asString.also { require(it.isNotBlank() && it.length <= 2048 && it.none { c -> c.isWhitespace() || c.isISOControl() }) }
        val mixin = quote.get("mixin_id").asString.also { require(it.matches(uuid)) }
        val issuer = quote.get("issuance_publickey").asString.also { require(SaitoWallet.isPublicKey(it)) }
        val until = quote.get("reserved_until").asString.toBigDecimal().toBigIntegerExact().also { require(it.signum() > 0 && it <= maximum) }.toLong()
        val state = TransferState.entries.single { it.wire == quote.get("transfer_status").asString }
        return Invoice(id, signature, provider, currency, amount, issued, issuer, address, mixin, until, state)
    }
    private fun money(value: String): BigDecimal {
        require(value.length <= 40 && value.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,18})?")))
        return value.toBigDecimal().also { require(it.signum() > 0 && it <= maximum.toBigDecimal() &&
            it.toDouble().isFinite() && it.compareTo(BigDecimal.valueOf(it.toDouble())) == 0) }
    }
    private val maximum = java.math.BigInteger("9007199254740991")

    /** Mixin's public network metadata identifies the network without importing an account. */
    fun readChainName(response: String, expectedId: String): String {
        require(expectedId.matches(uuid) && response.toByteArray().size <= 128 * 1024)
        val chain = JsonParser.parseString(response).asJsonObject.getAsJsonObject("data")
        require(chain.get("chain_id").asString == expectedId)
        return chain.get("name").asString.also { require(it.isNotBlank() && it.length <= 120 && it.none(Char::isISOControl)) }
    }
    fun chainUrl(id: String): URI {
        require(id.matches(uuid))
        return URI("https://api.mixin.one/network/chains/$id")
    }
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}

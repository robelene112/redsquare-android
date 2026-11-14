package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.net.URI

/** Actual provider capabilities and quoted payment metadata, distinct from payment or entitlement. */
object SaitoStoreCrypto {
    class Unavailable : IllegalStateException("The payment provider is unavailable")
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

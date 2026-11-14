package nl.davidengelen.redsquare.saito

import com.google.gson.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.Collections

/** A Store-signed request to its manual treasury. Submitted never means externally paid. */
object SaitoStoreWithdrawal {
    data class Proceeds(val pending: Map<String, BigDecimal>, val eligible: Map<String, BigDecimal>)
    class Quote internal constructor(val store: String, private val signed: String, val ticker: String,
        val address: String, val amount: BigDecimal, val serviceFee: BigInteger, val treasury: String,
        val issuedAt: Long, val claimIds: List<String>) {
        fun encoded(): String = signed
        fun validAt(now: Long): Boolean = now >= issuedAt - 60_000 && now - issuedAt <= 25 * 60_000
        internal fun body(): JsonObject = JsonParser.parseString(signed).asJsonObject.let { quote -> JsonObject().apply {
            addProperty("module", "Store"); addProperty("request", "store-withdrawal")
            add("data", quote.get("data")); add("signature", quote.get("signature"))
        } }
    }
    class Candidate internal constructor(val transaction: SaitoTransaction, val author: String, val quote: Quote,
        inputs: List<SaitoBalanceSnapshot.Slip>, val fee: BigInteger) {
        val inputs = Collections.unmodifiableList(inputs.toList())
    }
    enum class State { ACCEPTED, SUBMITTED, FORWARD_FAILED }
    data class Result(val state: State, val id: String)

    fun proceedsRequest() = request("load-web3-proceeds", JsonObject())
    fun quoteRequest(ticker: String, address: String) = request("store-withdrawal-quote", JsonObject().apply {
        symbol(ticker); destination(address); addProperty("ticker", ticker); addProperty("withdrawal_address", address)
    })
    fun submitRequest(candidate: Candidate) = request("store-withdrawal", JsonObject().apply {
        addProperty("transaction", candidate.transaction.envelope())
    })
    private fun request(name: String, data: JsonObject) = JsonObject().apply {
        addProperty("request", name); add("data", JsonObject().apply { addProperty("module", "Store"); data.entrySet().forEach { add(it.key, it.value) } })
    }
    fun readProceeds(response: String): Proceeds {
        val body = bounded(response); require(!body.has("err"))
        fun amounts(name: String): Map<String, BigDecimal> {
            val rows = body.getAsJsonObject(name).also { require(it.size() <= 128) }
            return Collections.unmodifiableMap(rows.entrySet().associate { (ticker, value) -> symbol(ticker) to money(value.asString, true) })
        }
        return Proceeds(amounts("pending"), amounts("eligible"))
    }
    fun readQuote(response: String, store: String, ticker: String, address: String, now: Long): Quote =
        parseQuote(response, store).also { require(it.ticker == ticker && it.address == address && now >= 0 && it.validAt(now)) }
    private fun parseQuote(response: String, store: String): Quote {
        require(SaitoWallet.isPublicKey(store))
        val body = bounded(response)
        require(body.keySet() == setOf("data", "signature") && body.get("signature").asJsonPrimitive.isString)
        val data = body.getAsJsonObject("data")
        require(data.keySet() == setOf("amount", "publickey", "ticker", "withdrawal_amount", "withdrawal_address", "transfer_uuids", "payment_uuids", "timestamp"))
        for ((name, value) in data.entrySet()) if (name !in setOf("transfer_uuids", "payment_uuids")) require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        require(SaitoWallet.verify(canonical(data).toByteArray(), body.get("signature").asString, store))
        val ticker = symbol(data.get("ticker").asString)
        val address = destination(data.get("withdrawal_address").asString)
        val amount = money(data.get("withdrawal_amount").asString)
        val fee = money(data.get("amount").asString, true).movePointRight(8).toBigIntegerExact().also { require(it <= maximum) }
        val treasury = data.get("publickey").asString.also { require(SaitoWallet.isPublicKey(it)) }
        fun ids(name: String, pattern: Regex): List<String> = data.getAsJsonArray(name).also { require(it.size() > 0) }.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString.also { id -> require(id.matches(pattern)) }
        }.also { require(it.distinct().size == it.size) }
        val transfers = ids("transfer_uuids", Regex("[1-9][0-9]{0,15}"))
        val payments = ids("payment_uuids", Regex("[A-Za-z0-9_-]{1,128}"))
        require(transfers.size == payments.size)
        val timestamp = data.get("timestamp").asString.also { require(it.matches(Regex("[1-9][0-9]{0,14}-[0-9]{1,15}"))) }.substringBefore('-').toLong()
        return Quote(store, body.toString(), ticker, address, amount, fee, treasury, timestamp, Collections.unmodifiableList(payments))
    }
    fun create(wallet: SaitoWallet, quote: Quote, inputs: List<SaitoBalanceSnapshot.Slip>, fee: BigInteger, timestamp: Long): Candidate {
        require(timestamp >= 0 && quote.validAt(timestamp) && fee.signum() >= 0 && fee <= maximum)
        val required = quote.serviceFee + fee
        val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
        val total = if (inputs.isEmpty()) BigInteger.ZERO else SaitoFundingInputs.checked(inputs)
        require(total >= required && (required.signum() > 0 || inputs.isEmpty()))
        val funding = if (inputs.isEmpty()) listOf(zero) else inputs
        val owned = (listOf(zero) + funding).map { SaitoTransaction.OwnedSlip(wallet.publicKey, it) }
        val outputs = listOf(SaitoTransaction.OwnedSlip(quote.treasury, zero.copy(amount = quote.serviceFee)),
            SaitoTransaction.OwnedSlip(wallet.publicKey, zero.copy(index = 1, amount = total - required)))
        return restore(SaitoTransaction.createNormal(wallet, quote.body(), timestamp, owned, outputs).encoded(), quote.store)
    }
    /** Restores an expired original without granting permission to create or submit another request. */
    fun restore(encoded: String, store: String): Candidate {
        require(encoded.length <= 256 * 1024)
        val bytes = Base64.getDecoder().decode(encoded); require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size >= 330)
        val h = ByteBuffer.wrap(bytes); val count = h.getInt(0); val size = h.getInt(8)
        require(count in 2..255 && h.getInt(4) == 2 && h.getInt(12) == 0 && h.getInt(88) == 1 && bytes[92] == 0.toByte() && h.getLong(80) >= 0 &&
            size in 1..128 * 1024 && bytes.size == 93 + 59 * (count + 2) + size)
        val transaction = SaitoTransaction.fromFundedBytes(bytes)
        val decoded = SaitoEnvelope.decode(transaction.envelope())
        val body = decoded.body
        require(body.keySet() == setOf("module", "request", "data", "signature") && body.get("module").asString == "Store" && body.get("request").asString == "store-withdrawal")
        val quote = parseQuote(JsonObject().apply { add("data", body.get("data")); add("signature", body.get("signature")) }.toString(), store)
        require(body.toString().toByteArray().contentEquals(bytes.copyOfRange(bytes.size - size, bytes.size)))
        val author = decoded.author
        require(bytes.copyOfRange(126, 152).all { it == 0.toByte() })
        val rows = (1 until count).map { index ->
            val p = 93 + 59 * index; require(Base58.encode(bytes.copyOfRange(p, p + 33)) == author)
            SaitoFundingInputs.slip(bytes, p)
        }
        val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
        val inputs = if (rows == listOf(zero)) emptyList() else rows
        val total = if (inputs.isEmpty()) BigInteger.ZERO else SaitoFundingInputs.checked(inputs)
        val p = 93 + 59 * count
        require(Base58.encode(bytes.copyOfRange(p, p + 33)) == quote.treasury && SaitoFundingInputs.slip(bytes, p) == zero.copy(amount = quote.serviceFee))
        val change = SaitoFundingInputs.slip(bytes, p + 59)
        require(Base58.encode(bytes.copyOfRange(p + 59, p + 92)) == author && change == zero.copy(index = 1, amount = change.amount))
        val fee = total - quote.serviceFee - change.amount
        require(fee.signum() >= 0 && fee <= maximum && (inputs.isNotEmpty() || quote.serviceFee.signum() == 0 && change.amount.signum() == 0))
        return Candidate(transaction, author, quote, inputs, fee)
    }
    fun readResult(response: String): Result {
        val body = bounded(response)
        val state = State.entries.single { it.name.lowercase() == body.get("status")?.asString }
        val id = body.get("id").asString.also { require(it.matches(Regex("[1-9][0-9]{0,15}"))) }
        return Result(state, id)
    }
    private fun bounded(value: String): JsonObject { require(value.toByteArray().size <= 128 * 1024); return JsonParser.parseString(value).asJsonObject }
    private fun symbol(value: String): String = value.also { require(it != "SAITO" && it.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,15}"))) }
    private fun destination(value: String): String = value.also { require(it.isNotBlank() && it.length <= 2048 && it.none { c -> c.isWhitespace() || c.isISOControl() }) }
    private fun money(value: String, zero: Boolean = false): BigDecimal {
        require(value.length <= 40 && value.matches(Regex("[0-9]{1,20}(\\.[0-9]{1,18})?")))
        return value.toBigDecimal().also { require(it.precision() <= 32 && if (zero) it.signum() >= 0 else it.signum() > 0) }
    }
    private fun canonical(value: JsonElement): String = when {
        value.isJsonObject -> value.asJsonObject.keySet().sorted().joinToString(",", "{", "}") { JsonPrimitive(it).toString() + ":" + canonical(value.asJsonObject.get(it)) }
        value.isJsonArray -> value.asJsonArray.joinToString(",", "[", "]") { canonical(it) }
        else -> GsonBuilder().disableHtmlEscaping().create().toJson(value)
    }
    private val maximum = BigInteger("9007199254740991")
}

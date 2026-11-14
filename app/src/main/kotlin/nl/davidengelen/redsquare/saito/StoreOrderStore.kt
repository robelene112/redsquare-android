package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One wallet-wide encrypted journal for funded orders and original external-payment intents. */
internal class StoreOrderStore(private val context: Context, private val owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    data class Order(val backend: SaitoBackend, val purchase: SaitoStorePurchase?, val product: StoreProduct,
        val mayHaveSent: Boolean = false, val payment: Payment? = null, val receipt: SaitoStoreReceipt? = null,
        val receiptBlock: String? = null, val cursor: SaitoStoreChain.Cursor? = null, val external: External? = null) {
        init { require((purchase == null) != (external == null)) }
        val originalTransaction: SaitoTransaction get() = purchase?.transaction ?: requireNotNull(external).carrier.transaction
        val id: String get() = originalTransaction.id
        val fundedPayment: SaitoStorePayment? get() = purchase ?: external?.issuance
        // Provider issuance spends the provider's inputs, never the buyer's wallet funds.
        val reservedInputs: List<SaitoBalanceSnapshot.Slip> get() = purchase?.inputs.orEmpty()
    }
    data class External(val carrier: SaitoStoreCarrier, val currency: SaitoStoreCrypto.Currency, val provider: String,
        val network: String, val response: String? = null, val status: String? = null, val hidden: Boolean = false) {
        init {
            require(SaitoWallet.isPublicKey(provider) && network.isNotBlank() && network.length <= 120 && network.none(Char::isISOControl))
            require(currency.ticker != "SAITO" && currency.usd.signum() > 0 && currency.updatedAt > 0)
            SaitoStoreCrypto.chainUrl(currency.chainId)
            require(status == null || response != null)
        }
        private val originalInvoice = response?.let { SaitoStoreCrypto.readInvoice(carrier, currency, provider, it) }
        val invoice: SaitoStoreCrypto.Invoice? = status?.let { text ->
            SaitoStoreCrypto.readInvoice(carrier, currency, provider, text).also { latest ->
                val first = requireNotNull(originalInvoice)
                require(latest.id == first.id && latest.carrierSignature == first.carrierSignature && latest.address == first.address &&
                    latest.mixinId == first.mixinId && latest.amount.compareTo(first.amount) == 0 &&
                    latest.issuedSaito.compareTo(first.issuedSaito) == 0 && latest.issuer == first.issuer && latest.expiresAt == first.expiresAt)
            }
        } ?: originalInvoice
        val issuance: SaitoStoreIssuance? = status?.takeIf { JsonParser.parseString(it).asJsonObject.get("outbound_tx")?.asString?.isNotBlank() == true }
            ?.let { SaitoStoreIssuance.read(carrier, requireNotNull(invoice), it) }
    }
    data class Payment(val height: java.math.BigInteger, val hash: String, val ordinal: java.math.BigInteger)
    private val name = "store-orders.$owner"
    private fun file(suffix: String) = EncryptedLocalFile(context, "$name.$suffix", "redsquare-store-orders-v1", "store-orders.")
    private fun exists(suffix: String) = requireNotNull(context.noBackupFilesDir.listFiles()).any {
        it.name in listOf("$name.$suffix", "$name.$suffix.bak", "$name.$suffix.new")
    }
    class StorageUnavailable(cause: Exception) : IllegalStateException("Order storage needs recovery", cause)
    fun read(): List<Order> = try { synchronized(SaitoLocalState.lock) {
        if (!exists("rows") && !exists("guard")) return@synchronized emptyList()
        val guard = requireNotNull(file("guard").load())
        require(guard.version == 1.toByte() && guard.text == owner)
        val contents = requireNotNull(file("rows").load()); require(contents.version == 1.toByte())
        val rows = JsonParser.parseString(contents.text).asJsonArray; require(rows.size() <= 100)
        val ids = HashSet<String>()
        rows.map { entry ->
            val row = entry.asJsonObject
            require(row.keySet() - setOf("provenance", "external") == setOf("backend", "candidate", "offer", "offerSignature", "mayHaveSent", "payment", "receipt", "receiptBlock", "cursor"))
            val external = row.get("external")?.takeUnless { it.isJsonNull }?.asJsonObject?.let { value ->
                require(value.keySet() == setOf("ticker", "chain", "usd", "updated", "provider", "network", "response", "status", "hidden"))
                require(value.get("hidden").asJsonPrimitive.isBoolean)
                External(SaitoStoreCarrier.restore(row.get("candidate").asString), SaitoStoreCrypto.Currency(value.get("ticker").asString,
                    value.get("chain").asString, value.get("usd").asString.toBigDecimal(), value.get("updated").asLong),
                    value.get("provider").asString, value.get("network").asString,
                    value.get("response").takeUnless { it.isJsonNull }?.asString,
                    value.get("status").takeUnless { it.isJsonNull }?.asString, value.get("hidden").asBoolean)
            }
            val purchase = if (external == null) SaitoStorePurchase.restore(row.get("candidate").asString) else null
            val original = purchase?.transaction ?: requireNotNull(external).carrier.transaction
            require((purchase?.buyer ?: external?.carrier?.buyer) == owner && ids.add(original.id))
            val product = SaitoStoreLineage.readSaved(row.get("offerSignature").asString, row.get("offer").asString,
                row.get("provenance")?.asJsonArray?.map { it.asString }.orEmpty())
            if (purchase != null) require(product.nftId == purchase.nftId && product.price?.currency == "SAITO" &&
                requireNotNull(product.price).amount.movePointRight(8).toBigIntegerExact() == purchase.unitPrice)
            else require(product.nftId == requireNotNull(external).carrier.nftId && product.price?.currency == external.carrier.price.currency &&
                requireNotNull(product.price).amount.compareTo(external.carrier.price.amount) == 0)
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            val payment = row.get("payment").takeUnless { it.isJsonNull }?.asJsonObject?.let {
                Payment(height(it.get("height").asString), hash(it.get("hash").asString),
                    it.get("ordinal").asString.toBigInteger().also { n -> require(n.signum() >= 0 && n.bitLength() <= 64) })
            }
            val funded = purchase ?: external?.issuance
            val receipt = row.get("receipt").takeUnless { it.isJsonNull }?.asString?.let { SaitoStoreReceipt.read(requireNotNull(funded), it) }
            val receiptBlock = row.get("receiptBlock").takeUnless { it.isJsonNull }?.asString?.let(::hash)
            require((receipt == null) == (receiptBlock == null) && (payment == null || funded != null))
            require(receipt?.asset?.creator?.let { it == product.creator } != false)
            if (receipt != null && payment != null) require(receipt.paymentInput.block == payment.height && receipt.paymentInput.ordinal == payment.ordinal)
            Order(SaitoBackend.entries.single { it.id == row.get("backend").asString }, purchase, product,
                row.get("mayHaveSent").asBoolean, payment, receipt, receiptBlock, row.get("cursor").takeUnless { it.isJsonNull }?.asJsonObject?.let {
                    SaitoStoreChain.Cursor(height(it.get("height").asString), hash(it.get("hash").asString))
                }, external)
        }
    } } catch (failure: Exception) { throw if (failure is StorageUnavailable) failure else StorageUnavailable(failure) }
    fun reserve(backend: SaitoBackend, purchase: SaitoStorePurchase, product: StoreProduct): Order = synchronized(SaitoLocalState.lock) {
        val prior = read()
        prior.firstOrNull { it.id == purchase.transaction.id }?.let {
            require(it.backend == backend && it.originalTransaction.encoded() == purchase.transaction.encoded()); return@synchronized it
        }
        require(prior.size < 100 && purchase.author == owner)
        require(TransferStore(context, owner).available(purchase.inputs) == purchase.inputs)
        val record = Order(backend, purchase, product)
        save(prior + record); read().single { it.id == record.id }
    }
    fun reserveExternal(backend: SaitoBackend, carrier: SaitoStoreCarrier, product: StoreProduct,
        currency: SaitoStoreCrypto.Currency, provider: String, network: String): Order = synchronized(SaitoLocalState.lock) {
        val rows = read()
        rows.firstOrNull { it.id == carrier.transaction.id }?.let {
            require(it.backend == backend && it.originalTransaction.encoded() == carrier.transaction.encoded() && it.external?.currency == currency &&
                it.external.provider == provider && it.external.network == network); return@synchronized it
        }
        require(rows.size < 100 && carrier.buyer == owner)
        // Only one unresolved invoice per provider/currency, including other selected nodes.
        require(rows.none { it.external?.let { previous -> previous.provider == provider && previous.currency.ticker == currency.ticker } == true &&
            it.receipt == null && it.external.invoice?.state !in setOf(SaitoStoreCrypto.TransferState.FAILED, SaitoStoreCrypto.TransferState.CANCELLED) })
        val record = Order(backend, null, product, external = External(carrier, currency, provider, network))
        save(rows + record); read().single { it.id == record.id }
    }
    fun markSending(order: Order) = update(order.id) { saved ->
        require(saved.backend == order.backend && saved.originalTransaction.encoded() == order.originalTransaction.encoded())
        saved.copy(mayHaveSent = true)
    }
    fun recordProvider(id: String, response: String) = update(id) { saved ->
        require(saved.mayHaveSent)
        val external = requireNotNull(saved.external)
        val value = JsonParser.parseString(response).asJsonObject
        if (value.has("found")) require(value.get("found").asJsonPrimitive.isBoolean && value.get("found").asBoolean)
        val next = if (external.response == null) external.copy(response = response,
            status = response.takeIf { value.has("found") }) else external.copy(status = response)
        val oldIssuance = external.issuance
        require(oldIssuance == null || next.issuance?.transaction?.encoded() == oldIssuance.transaction.encoded())
        saved.copy(external = next)
    }
    fun hideInstructions(id: String) = update(id) { it.copy(external = requireNotNull(it.external).copy(hidden = true)) }
    fun observe(id: String, observation: SaitoStoreChain.Observation) = update(id) { saved ->
        require(saved.fundedPayment != null)
        val receipt = observation.receipt
        require(receipt?.asset?.creator?.let { it == saved.product.creator } != false)
        val payment = observation.payment?.let { Payment(it.height, it.hash, it.ordinal) }
        saved.copy(payment = payment, receipt = receipt, receiptBlock = observation.receiptBlock, cursor = observation.cursor)
    }
    fun discard(id: String) = synchronized(SaitoLocalState.lock) {
        val rows = read(); require(!rows.single { it.id == id }.mayHaveSent)
        save(rows.filterNot { it.id == id })
    }
    private fun update(id: String, transform: (Order) -> Order) = synchronized(SaitoLocalState.lock) {
        val rows = read(); require(rows.count { it.id == id } == 1)
        save(rows.map { if (it.id == id) transform(it) else it })
    }
    private fun save(rows: List<Order>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.originalTransaction.encoded())
            addProperty("offer", record.product.envelope); addProperty("offerSignature", record.product.signature)
            add("provenance", JsonArray().apply { record.product.provenance.forEach(::add) })
            addProperty("mayHaveSent", record.mayHaveSent)
            add("payment", record.payment?.let { payment -> JsonObject().apply {
                addProperty("height", payment.height.toString()); addProperty("hash", payment.hash); addProperty("ordinal", payment.ordinal.toString())
            } } ?: com.google.gson.JsonNull.INSTANCE)
            addProperty("receipt", record.receipt?.encoded); addProperty("receiptBlock", record.receiptBlock)
            add("cursor", record.cursor?.let { cursor -> JsonObject().apply {
                addProperty("height", cursor.height.toString()); addProperty("hash", cursor.hash)
            } } ?: com.google.gson.JsonNull.INSTANCE)
            record.external?.let { external -> add("external", JsonObject().apply {
                addProperty("ticker", external.currency.ticker); addProperty("chain", external.currency.chainId)
                addProperty("usd", external.currency.usd.toPlainString()); addProperty("updated", external.currency.updatedAt)
                addProperty("provider", external.provider); addProperty("network", external.network)
                addProperty("response", external.response); addProperty("status", external.status); addProperty("hidden", external.hidden)
            }) }
        }) } }
        // Reject inconsistent new rows before replacing the only durable copy.
        rows.forEach { record ->
            val product = SaitoStoreLineage.restore(record.product)
            val direct = record.purchase; val external = record.external
            require((direct?.buyer ?: external?.carrier?.buyer) == owner)
            require(product.nftId == (direct?.nftId ?: external?.carrier?.nftId))
            val price = requireNotNull(product.price)
            if (direct != null) require(price.currency == "SAITO" && price.amount.movePointRight(8).toBigIntegerExact() == direct.unitPrice)
            else require(price.currency == requireNotNull(external).carrier.price.currency && price.amount.compareTo(external.carrier.price.amount) == 0)
            require((record.receipt == null) == (record.receiptBlock == null))
            record.receipt?.let { SaitoStoreReceipt.read(requireNotNull(record.fundedPayment), it.encoded) }
        }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { it.originalTransaction.encoded() } == rows.map { it.originalTransaction.encoded() })
    }
    private fun height(value: String) = value.toBigInteger().also { require(it.signum() > 0 && it.bitLength() <= 64) }
    private fun hash(value: String) = value.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
}

package nl.davidengelen.redsquare.saito

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Wallet-wide encrypted original orders. Uncertain sends retain their funding across node changes. */
internal class StoreOrderStore(private val context: Context, private val owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    data class Order(val backend: SaitoBackend, val purchase: SaitoStorePurchase, val product: StoreProduct,
        val mayHaveSent: Boolean = false, val payment: Payment? = null, val receipt: SaitoStoreReceipt? = null,
        val receiptBlock: String? = null, val cursor: SaitoStoreChain.Cursor? = null)
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
            require(row.keySet() == setOf("backend", "candidate", "offer", "offerSignature", "mayHaveSent", "payment", "receipt", "receiptBlock", "cursor"))
            val purchase = SaitoStorePurchase.restore(row.get("candidate").asString)
            require(purchase.author == owner && ids.add(purchase.transaction.id))
            val signature = row.get("offerSignature").asString
            val archive = JsonArray().apply { add(JsonObject().apply { addProperty("tx", row.get("offer").asString) }) }
            val product = requireNotNull(SaitoStore.readOriginalListingArchive(signature, archive.toString()))
            require(product.nftId == purchase.nftId && product.price?.currency == "SAITO" &&
                requireNotNull(product.price).amount.movePointRight(8).toBigIntegerExact() == purchase.unitPrice)
            require(row.get("mayHaveSent").asJsonPrimitive.isBoolean)
            val payment = row.get("payment").takeUnless { it.isJsonNull }?.asJsonObject?.let {
                Payment(it.get("height").asString.toBigInteger().also { h -> require(h.signum() > 0 && h.bitLength() <= 64) },
                    it.get("hash").asString.also { h -> require(h.matches(Regex("[0-9a-f]{64}"))) },
                    it.get("ordinal").asString.toBigInteger().also { o -> require(o.signum() >= 0 && o.bitLength() <= 64) })
            }
            val receipt = row.get("receipt").takeUnless { it.isJsonNull }?.asString?.let { SaitoStoreReceipt.read(purchase, it) }
            val receiptBlock = row.get("receiptBlock").takeUnless { it.isJsonNull }?.asString?.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
            require((receipt == null) == (receiptBlock == null))
            require(receipt?.asset?.creator?.let { it == product.creator } != false)
            if (receipt != null && payment != null) require(receipt.paymentInput.block == payment.height && receipt.paymentInput.ordinal == payment.ordinal)
            Order(SaitoBackend.entries.single { it.id == row.get("backend").asString }, purchase, product,
                row.get("mayHaveSent").asBoolean, payment, receipt, receiptBlock, row.get("cursor").takeUnless { it.isJsonNull }?.asJsonObject?.let {
                    SaitoStoreChain.Cursor(it.get("height").asString.toBigInteger().also { h -> require(h.signum() > 0 && h.bitLength() <= 64) },
                        it.get("hash").asString.also { h -> require(h.matches(Regex("[0-9a-f]{64}"))) })
                })
        }
    }
    } catch (failure: Exception) { throw if (failure is StorageUnavailable) failure else StorageUnavailable(failure) }
    fun reserve(backend: SaitoBackend, purchase: SaitoStorePurchase, product: StoreProduct): Order = synchronized(SaitoLocalState.lock) {
        val prior = read()
        prior.firstOrNull { it.purchase.transaction.id == purchase.transaction.id }?.let {
            require(it.backend == backend && it.purchase.transaction.encoded() == purchase.transaction.encoded()); return@synchronized it
        }
        require(prior.size < 100 && purchase.author == owner)
        require(TransferStore(context, owner).available(purchase.inputs) == purchase.inputs)
        val record = Order(backend, purchase, product)
        save(prior + record); read().single { it.purchase.transaction.id == purchase.transaction.id }
    }
    fun markSending(order: Order) = update(order.purchase.transaction.id) { saved ->
        require(saved.backend == order.backend && saved.purchase.transaction.encoded() == order.purchase.transaction.encoded())
        saved.copy(mayHaveSent = true)
    }
    fun observe(id: String, observation: SaitoStoreChain.Observation) = update(id) { saved ->
        val receipt = observation.receipt
        require(receipt?.asset?.creator?.let { it == saved.product.creator } != false)
        val payment = observation.payment?.let { Payment(it.height, it.hash, it.ordinal) }
        saved.copy(payment = payment, receipt = receipt, receiptBlock = observation.receiptBlock, cursor = observation.cursor)
    }
    fun discard(id: String) = synchronized(SaitoLocalState.lock) {
        val rows = read(); require(!rows.single { it.purchase.transaction.id == id }.mayHaveSent)
        save(rows.filterNot { it.purchase.transaction.id == id })
    }
    private fun update(id: String, transform: (Order) -> Order) = synchronized(SaitoLocalState.lock) {
        val rows = read(); require(rows.count { it.purchase.transaction.id == id } == 1)
        save(rows.map { if (it.purchase.transaction.id == id) transform(it) else it })
    }
    private fun save(rows: List<Order>) {
        val json = JsonArray().apply { rows.forEach { record -> add(JsonObject().apply {
            addProperty("backend", record.backend.id); addProperty("candidate", record.purchase.transaction.encoded())
            addProperty("offer", record.product.envelope); addProperty("offerSignature", record.product.signature)
            addProperty("mayHaveSent", record.mayHaveSent)
            add("payment", record.payment?.let { payment -> JsonObject().apply {
                addProperty("height", payment.height.toString()); addProperty("hash", payment.hash); addProperty("ordinal", payment.ordinal.toString())
            } } ?: com.google.gson.JsonNull.INSTANCE)
            addProperty("receipt", record.receipt?.encoded); addProperty("receiptBlock", record.receiptBlock)
            add("cursor", record.cursor?.let { cursor -> JsonObject().apply {
                addProperty("height", cursor.height.toString()); addProperty("hash", cursor.hash)
            } } ?: com.google.gson.JsonNull.INSTANCE)
        }) } }
        file("rows").save(EncryptedLocalFile.Contents(1, json.toString()))
        if (!exists("guard")) file("guard").save(EncryptedLocalFile.Contents(1, owner))
        check(read().map { it.purchase.transaction.encoded() } == rows.map { it.purchase.transaction.encoded() })
    }
}

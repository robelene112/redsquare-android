package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

data class StoreListingTerms(val title: String, val description: String, val category: StoreCategory,
    val price: String, val quantity: BigInteger) {
    init {
        require(title.isNotBlank() && title.length <= 10_000 && description.length <= 200_000)
        require(quantity.signum() > 0 && quantity <= BigInteger("9007199254740991"))
        val amount = price.toBigDecimal().movePointRight(8).toBigIntegerExact()
        require(amount.signum() > 0 && amount <= BigInteger("9007199254740991"))
    }
}

/** One original signed sale offer. Current rights and Store acceptance are separate observations. */
class SaitoStoreListing private constructor(val transaction: SaitoTransaction, val holding: SaitoNft,
    val store: String, val terms: StoreListingTerms) {
    val inputs: List<SaitoBalanceSnapshot.Slip> get() = holding.positions
    val author: String get() = holding.owner
    companion object {
        fun sellable(holding: SaitoNft): Boolean = holding.type !in setOf("vault-nft-rental", "store-nft-rental")

        fun create(wallet: SaitoWallet, holding: SaitoNft, envelope: String, store: String,
            terms: StoreListingTerms, timestamp: Long): SaitoStoreListing {
            return createWithMetadata(wallet, holding, metadata(holding, envelope), store, terms, timestamp)
        }

        /** Store-delivered metadata has a P2SH signer and is admitted only against the retained purchase. */
        fun receiptMetadata(holding: SaitoNft, purchase: SaitoStorePayment, encoded: String): JsonObject {
            val receipt = SaitoStoreReceipt.read(purchase, encoded)
            val delivered = requireNotNull(receipt.asset)
            require(receipt.kind == SaitoStoreReceipt.Kind.FULFILLMENT && delivered.id == holding.id &&
                delivered.creator == holding.creator && delivered.owner == holding.owner)
            val wire = Base64.getDecoder().decode(encoded)
            val header = ByteBuffer.wrap(wire)
            val start = 93 + 59 * (header.getInt(0) + header.getInt(4))
            return JsonParser.parseString(wire.copyOfRange(start, start + header.getInt(8)).toString(Charsets.UTF_8)).asJsonObject
        }

        fun createFromReceipt(wallet: SaitoWallet, holding: SaitoNft, purchase: SaitoStorePayment,
            encoded: String, store: String, terms: StoreListingTerms, timestamp: Long): SaitoStoreListing =
            createWithMetadata(wallet, holding, receiptMetadata(holding, purchase, encoded), store, terms, timestamp)

        private fun createWithMetadata(wallet: SaitoWallet, holding: SaitoNft, body: JsonObject, store: String,
            terms: StoreListingTerms, timestamp: Long): SaitoStoreListing {
            require(holding.owner == wallet.publicKey && sellable(holding) && SaitoWallet.isPublicKey(store) && store != wallet.publicKey)
            require(terms.quantity <= holding.quantity)
            for (field in listOf("request", "access_script", "access_hash", "p2sh_address", "access_scripts", "fulfill_sale", "listing_signature", "listing")) body.remove(field)
            val script = SaitoAccessScript.parse(JsonObject().apply {
                addProperty("op", "CHECKMULTISIG"); addProperty("m", 1)
                add("publickeys", com.google.gson.JsonArray().apply { add(wallet.publicKey); add(store) })
                addProperty("msg", "tx.from.p2sh.utxoset_key")
            }.toString())
            body.addProperty("module", "Store"); body.addProperty("request", "list-asset")
            body.addProperty("access_script", script.source); body.addProperty("access_hash", script.hash)
            body.addProperty("p2sh_address", "00" + script.hash)
            body.add("listing", JsonObject().apply {
                addProperty("title", terms.title); addProperty("description", terms.description)
                addProperty("category", terms.category.wire); addProperty("price", terms.price)
                addProperty("quantity_total", terms.quantity); addProperty("quantity_available", terms.quantity)
            })
            if (holding.type == "stack") transferStack(wallet, holding, body, store, timestamp)
            val source = holding.witnesses.map(::witnessRow)
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            val deposit = holding.deposit * terms.quantity / holding.quantity
            val outputs = source.mapIndexed { index, row -> SaitoTransaction.OwnedSlip(
                if (index == 1) Base58.encode(("00" + script.hash).chunked(2).map { it.toInt(16).toByte() }.toByteArray()) else row.publicKey,
                zero.copy(index = index, amount = if (index == 0) terms.quantity else if (index == 1) deposit else BigInteger.ZERO,
                    type = row.position.type)) } +
                (if (terms.quantity < holding.quantity) source.mapIndexed { index, row -> SaitoTransaction.OwnedSlip(row.publicKey,
                    zero.copy(index = index + 3, amount = if (index == 0) holding.quantity - terms.quantity
                        else if (index == 1) holding.deposit - deposit else BigInteger.ZERO, type = row.position.type)) } else emptyList())
            val completeOutputs = outputs + SaitoTransaction.OwnedSlip(wallet.publicKey, zero.copy(index = outputs.size))
            val transaction = SaitoTransaction.createStoreListing(wallet, body, timestamp,
                source.map { SaitoTransaction.OwnedSlip(it.publicKey, it.position) } + SaitoTransaction.OwnedSlip(wallet.publicKey, zero), completeOutputs)
            return restore(transaction.encoded())
        }

        /** Original signed metadata must contain this creator/id; a display title alone never binds stock. */
        fun metadata(holding: SaitoNft, envelope: String): JsonObject {
            require(envelope.toByteArray().size <= 1024 * 1024)
            val signed = SaitoEnvelope.decodeBound(envelope)
            val bytes = Base64.getDecoder().decode(JsonParser.parseString(envelope).asJsonObject.get("t").asString)
            val header = ByteBuffer.wrap(bytes); val inputs = header.getInt(0); val outputs = header.getInt(4)
            require((inputs until inputs + outputs - 2).any { index -> runCatching {
                val before = networkRow(bytes, index); val after = networkRow(bytes, index + 2)
                before.position.type == 9 && after.position.type == 9 && before.publicKey == holding.creator &&
                    Base58.decode(after.publicKey).hex() == holding.id
            }.getOrDefault(false) })
            return signed.body.deepCopy()
        }

        fun readInventoryArchive(holding: SaitoNft, response: String): String? =
            readArchiveRows(response, { envelope -> metadata(holding, envelope); envelope }, { true }, {
                SaitoEnvelope.decodeBound(it).id
            }).entries.lastOrNull()

        fun restore(encoded: String): SaitoStoreListing {
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size >= 565)
            val header = ByteBuffer.wrap(bytes)
            require(header.getInt(0) == 4 && header.getInt(4) in setOf(4, 7) && header.getInt(12) == 0 && header.getInt(88) == 1 && bytes[92] == 8.toByte())
            require(header.getInt(8) > 0 && bytes.size == 93 + (4 + header.getInt(4)) * 59 + header.getInt(8))
            val transaction = SaitoTransaction.fromVaultMintBytes(bytes)
            val signed = SaitoEnvelope.decodeBound(transaction.envelope()); val body = signed.body
            val source = (0 until 4 + header.getInt(4)).map { networkRow(bytes, it) }
            val holding = SaitoNft.fromSnapshot(source[1], source[0], source[2])
            require(holding.owner == signed.author && sellable(holding) && holding.positions.all { it.block.signum() > 0 })
            val script = SaitoAccessScript.parse(body.get("access_script").asString)
            val rules = JsonParser.parseString(script.source).asJsonObject
            require(rules.keySet() == setOf("op", "m", "publickeys", "msg") && rules.get("op").asString == "CHECKMULTISIG" &&
                rules.get("m").asInt == 1 && rules.get("msg").asString == "tx.from.p2sh.utxoset_key")
            val keys = rules.getAsJsonArray("publickeys").map { it.asString }
            require(keys.size == 2 && keys[0] == holding.owner && SaitoWallet.isPublicKey(keys[1]) && keys[1] != holding.owner)
            require(body.get("module").asString == "Store" && body.get("request").asString == "list-asset" &&
                body.get("access_hash").asString == script.hash && body.get("p2sh_address").asString == "00" + script.hash &&
                listOf("access_scripts", "fulfill_sale", "listing_signature").none(body::has))
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            require(source[3].publicKey == holding.owner && source[3].position == zero)
            val row = body.getAsJsonObject("listing")
            val terms = StoreListingTerms(row.get("title").asString, row.get("description").asString,
                StoreCategory.entries.single { it.wire == row.get("category").asString }, row.get("price").asString,
                row.get("quantity_total").asString.toBigInteger())
            require(terms.quantity <= holding.quantity && row.get("quantity_available").asString.toBigInteger() == terms.quantity)
            val partial = terms.quantity < holding.quantity
            require(header.getInt(4) == if (partial) 7 else 4)
            val deposit = holding.deposit * terms.quantity / holding.quantity
            for (index in 0..2) {
                val amount = if (index == 0) terms.quantity else if (index == 1) deposit else BigInteger.ZERO
                require(source[index + 4].position == zero.copy(index = index, amount = amount, type = source[index].position.type))
                require(if (index == 1) Base58.decode(source[5].publicKey).hex() == "00" + script.hash
                    else source[index + 4].publicKey == source[index].publicKey)
                if (partial) {
                    require(source[index + 7].publicKey == source[index].publicKey && source[index + 7].position ==
                        zero.copy(index = index + 3, amount = source[index].position.amount - amount, type = source[index].position.type))
                }
            }
            require(source.last().publicKey == holding.owner && source.last().position == zero.copy(index = if (partial) 6 else 3))
            if (holding.type == "stack") {
                val data = body.getAsJsonObject("data"); val path = data.getAsJsonArray("path")
                require(path.size() in 1..256)
                val hop = path.last().asJsonObject
                require(hop.keySet() == setOf("to", "value", "sig") && hop.get("to").asString == keys[1])
                val value = hop.get("value").asString
                val raw = Base64.getDecoder().decode(value)
                require(raw.size <= 256 && Base64.getEncoder().encodeToString(raw) == value)
                val event = JsonParser.parseString(raw.toString(Charsets.UTF_8)).asJsonObject
                require(event.keySet() == setOf("timestamp", "delegate") && event.get("timestamp").asLong == signed.createdAt &&
                    event.get("delegate").asJsonPrimitive.isBoolean && !event.get("delegate").asBoolean)
                require(SaitoWallet.verify(hashText(keys[1] + "|" + value + "|" + holding.id).toByteArray(), hop.get("sig").asString, signed.author))
                if (holding.creator == signed.author && path.size() == 1 && truthy(data.get("duration"))) {
                    require(SaitoWallet.verify(hashText(data.get("duration").asString + "|" + holding.id).toByteArray(), data.get("duration_sig").asString, signed.author))
                }
            }
            return SaitoStoreListing(transaction, holding, keys[1], terms)
        }
        /** Official Stack ownership hop, including creator duration binding and last inbound truncation. */
        private fun transferStack(wallet: SaitoWallet, holding: SaitoNft, body: JsonObject, receiver: String, timestamp: Long) {
            val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { body.add("data", it) }
            var path = data.get("path")?.takeIf { it.isJsonArray }?.asJsonArray ?: com.google.gson.JsonArray()
            require(path.size() < 256)
            if (path.isEmpty && holding.creator == wallet.publicKey && truthy(data.get("duration")) && !truthy(data.get("duration_sig")))
                data.addProperty("duration_sig", wallet.sign(hashText(data.get("duration").asString + "|" + holding.id).toByteArray()))
            val inbound = path.indexOfLast { it.asJsonObject.get("to")?.asString == wallet.publicKey }
            if (inbound >= 0) path = com.google.gson.JsonArray().apply { path.take(inbound + 1).forEach(::add) }
            val value = Base64.getEncoder().encodeToString(JsonObject().apply {
                addProperty("timestamp", timestamp); addProperty("delegate", false)
            }.toString().toByteArray())
            path.add(JsonObject().apply {
                addProperty("to", receiver); addProperty("value", value)
                addProperty("sig", wallet.sign(hashText(receiver + "|" + value + "|" + holding.id).toByteArray()))
            })
            data.add("path", path)
        }
        private fun truthy(value: com.google.gson.JsonElement?): Boolean = value != null && !value.isJsonNull &&
            (!value.isJsonPrimitive || value.asJsonPrimitive.let { when {
                it.isBoolean -> it.asBoolean; it.isNumber -> it.asBigDecimal.signum() != 0; else -> it.asString.isNotEmpty()
            } })
        private fun hashText(value: String): String {
            val bytes = value.toByteArray(); val result = ByteArray(32)
            org.bouncycastle.crypto.digests.Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) }
            return result.hex()
        }
        private fun witnessRow(value: String): SaitoNft.Row {
            val b = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray().also { require(it.size == 59) }
            return SaitoNft.Row(Base58.encode(b.copyOfRange(0, 33)), SaitoBalanceSnapshot.Slip(
                BigInteger(1, b.copyOfRange(33, 41)), BigInteger(1, b.copyOfRange(41, 49)), b[49].toInt() and 255,
                BigInteger(1, b.copyOfRange(50, 58)), b[58].toInt() and 255))
        }
        private fun networkRow(bytes: ByteArray, index: Int): SaitoNft.Row {
            val p = 93 + index * 59
            return SaitoNft.Row(Base58.encode(bytes.copyOfRange(p, p + 33)), SaitoBalanceSnapshot.Slip(
                BigInteger(1, bytes.copyOfRange(p + 41, p + 49)), BigInteger(1, bytes.copyOfRange(p + 49, p + 57)), bytes[p + 57].toInt() and 255,
                BigInteger(1, bytes.copyOfRange(p + 33, p + 41)), bytes[p + 58].toInt() and 255))
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

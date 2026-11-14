package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake3Digest

/** Seller-authorized withdrawal of one exact Store custody tuple. It does not assert chain inclusion. */
class SaitoStoreDelist private constructor(val transaction: SaitoTransaction, val product: StoreProduct,
    val quantity: BigInteger, val author: String, val inputs: List<SaitoBalanceSnapshot.Slip>) {
    val store: String = JsonParser.parseString(SaitoStoreLineage.body(product.envelope).get("access_script").asString)
        .asJsonObject.getAsJsonArray("publickeys")[1].asString
    fun matchesSpend(response: String): Boolean = runCatching {
        val script = SaitoAccessScript.parse(SaitoStoreLineage.body(product.envelope).get("access_script").asString)
        readSpend(product, response, script).map { it.position } == inputs
    }.getOrDefault(false)
    companion object {
        fun create(wallet: SaitoWallet, original: StoreProduct, response: String, timestamp: Long): SaitoStoreDelist {
            val product = authentic(original)
            require(wallet.publicKey == product.seller && response.toByteArray().size <= 64 * 1024 && timestamp >= 0)
            val source = SaitoStoreLineage.body(product.envelope)
            val script = SaitoAccessScript.parse(source.get("access_script").asString)
            val inputs = readSpend(product, response, script)
            val outputs = inputs.mapIndexed { index, slip -> slip.copy(publicKey = if (index == 1) wallet.publicKey else slip.publicKey, position = slip.position.copy(index = index)) }
            val auth = inputs[1].utxoKey() + "|" + digest(outputPreimage(outputs)).hex()
            val executable = JsonParser.parseString(script.source).asJsonObject.apply {
                add("witness", JsonObject().apply { add("signatures", com.google.gson.JsonArray().apply { add(wallet.sign(auth.toByteArray())) }) })
            }.toString()
            val body = message(source, product, script.source, script.hash, executable)
            val payload = body.toString().toByteArray()
            require(payload.size <= SaitoTransaction.MAX_MESSAGE_BYTES)
            val preimage = ByteBuffer.allocate(16 + 6 * 43 + payload.size).putLong(timestamp)
            for (slip in inputs + outputs) preimage.put(Base58.decode(slip.publicKey)).putLong(slip.position.amount.toLong()).put(slip.position.index.toByte()).put(slip.position.type.toByte())
            preimage.putInt(1).putInt(8).put(payload)
            val signature = wallet.sign(preimage.array()).hexBytes()
            val bytes = ByteBuffer.allocate(93 + 6 * 59 + payload.size).putInt(3).putInt(3).putInt(payload.size).putInt(0).put(signature).putLong(timestamp).putInt(1).put(8)
            for (slip in inputs + outputs) bytes.put(Base58.decode(slip.publicKey)).putLong(slip.position.amount.toLong()).putLong(slip.position.block.toLong())
                .putLong(slip.position.ordinal.toLong()).put(slip.position.index.toByte()).put(slip.position.type.toByte())
            bytes.put(payload)
            return restore(Base64.getEncoder().encodeToString(bytes.array()), product)
        }
        fun restore(encoded: String, original: StoreProduct): SaitoStoreDelist {
            val product = authentic(original)
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded); val header = ByteBuffer.wrap(bytes)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size >= 448 && header.getInt(0) == 3 && header.getInt(4) == 3 &&
                header.getInt(12) == 0 && header.getInt(88) == 1 && bytes[92] == 8.toByte() && header.getLong(80) >= 0 &&
                header.getInt(8) in 1..SaitoTransaction.MAX_MESSAGE_BYTES && bytes.size == 447 + header.getInt(8))
            val payload = bytes.copyOfRange(447, bytes.size)
            require(SaitoWallet.verify(saitoTransactionPreimage(bytes, payload), bytes.copyOfRange(16, 80).hex(), product.seller))
            val rows = (0..5).map { index ->
                val p = 93 + index * 59
                SaitoNft.Row(Base58.encode(bytes.copyOfRange(p, p + 33)), SaitoBalanceSnapshot.Slip(
                    BigInteger(1, bytes.copyOfRange(p + 41, p + 49)), BigInteger(1, bytes.copyOfRange(p + 49, p + 57)), bytes[p + 57].toInt() and 255,
                    BigInteger(1, bytes.copyOfRange(p + 33, p + 41)), bytes[p + 58].toInt() and 255))
            }
            val source = SaitoStoreLineage.body(product.envelope)
            val script = SaitoAccessScript.parse(source.get("access_script").asString)
            validateInputs(product, rows.take(3), script)
            require(rows[3] == rows[0].copy(position = rows[0].position.copy(index = 0)) &&
                rows[5] == rows[2].copy(position = rows[2].position.copy(index = 2)) &&
                rows[4] == rows[1].copy(publicKey = product.seller, position = rows[1].position.copy(index = 1)))
            val body = JsonParser.parseString(payload.toString(Charsets.UTF_8)).asJsonObject
            val witnesses = body.getAsJsonArray("access_scripts").also { require(it.size() == 1) }
            val witness = SaitoAccessScript.parse(witnesses[0].asString)
            require(witness.hash == script.hash)
            val signatures = JsonParser.parseString(witness.source).asJsonObject.getAsJsonObject("witness").getAsJsonArray("signatures")
            val auth = rows[1].utxoKey() + "|" + digest(outputPreimage(rows.drop(3))).hex()
            require(signatures.size() == 1 && SaitoWallet.verify(auth.toByteArray(), signatures[0].asString, product.seller))
            require(body == message(source, product, script.source, script.hash, witnesses[0].asString))
            val transaction = SaitoTransaction.fromStoreDelistBytes(bytes, product.seller)
            return SaitoStoreDelist(transaction, product, product.quantity, product.seller, java.util.Collections.unmodifiableList(rows.take(3).map { it.position }))
        }
        private fun readSpend(product: StoreProduct, response: String, script: SaitoAccessScript): List<SaitoNft.Row> {
            require(response.toByteArray().size <= 64 * 1024)
            val row = JsonParser.parseString(response).asJsonObject
            require(row.get("signature").asString == product.signature && row.get("seller").asString == product.seller &&
                row.get("nft_id").asString == product.nftId && row.get("quantity").asString.toBigDecimal().toBigIntegerExact() == product.quantity &&
                row.get("price").asString.toBigDecimal().toBigIntegerExact() == requireNotNull(product.price).amount.movePointRight(8).toBigIntegerExact() &&
                SaitoAccessScript.parse(row.get("access_script").asString).hash == script.hash && row.get("access_hash").asString == script.hash &&
                row.get("p2sh_address").asString == "00" + script.hash)
            val inputs = (1..3).map { index ->
                val raw = row.get("utxo_slip$index")
                val value = if (raw.isJsonPrimitive && raw.asJsonPrimitive.isString) JsonParser.parseString(raw.asString).asJsonObject else raw.asJsonObject
                fun number(name: String) = value.get(name).asString.toBigDecimal().toBigIntegerExact().also { require(it.signum() >= 0 && it.bitLength() <= 64) }
                fun byteNumber(name: String) = number(name).also { require(it <= BigInteger.valueOf(255)) }.toInt()
                val position = SaitoBalanceSnapshot.Slip(number("blockId"), number("txOrdinal"), byteNumber("index"), number("amount"), byteNumber("type"))
                val slip = SaitoNft.Row(value.get("publicKey").asString, position)
                require(value.get("utxoKey").asString == slip.utxoKey()); slip
            }
            validateInputs(product, inputs, script)
            return inputs
        }
        private fun authentic(product: StoreProduct): StoreProduct {
            return SaitoStoreLineage.restore(product).also {
                require(it == product && it.price?.currency == "SAITO" && it.assetType !in setOf("vault-nft-rental", "store-nft-rental"))
            }
        }
        private fun validateInputs(product: StoreProduct, inputs: List<SaitoNft.Row>, script: SaitoAccessScript) {
            val source = Base64.getDecoder().decode(JsonParser.parseString(product.envelope).asJsonObject.get("t").asString)
            val inputCount = ByteBuffer.wrap(source).getInt(0)
            val outputCount = ByteBuffer.wrap(source).getInt(4)
            val custody = (inputCount until inputCount + outputCount).single { index ->
                val offset = 93 + index * 59
                source[offset + 58] in listOf(0.toByte(), 1.toByte()) && source.copyOfRange(offset, offset + 33).hex() == "00" + script.hash
            }
            val p = 93 + custody * 59
            val deposit = BigInteger(1, source.copyOfRange(p + 33, p + 41))
            val rules = JsonParser.parseString(script.source).asJsonObject
            val keys = rules.getAsJsonArray("publickeys").map { it.asString }
            require(rules.get("op").asString == "CHECKMULTISIG" && rules.get("m").asInt == 1 && keys.size == 2 && keys[0] == product.seller &&
                SaitoWallet.isPublicKey(keys[1]) && rules.get("msg").asString == "tx.from.p2sh.utxoset_key")
            require(inputs[0].publicKey == product.creator && inputs[0].position.amount == product.quantity && inputs[0].position.type == 9 &&
                Base58.decode(inputs[1].publicKey).hex() == "00" + script.hash && inputs[1].position.amount == deposit && inputs[1].position.type in setOf(0, 1) &&
                Base58.decode(inputs[2].publicKey).hex() == product.nftId && inputs[2].position.amount == BigInteger.ZERO && inputs[2].position.type == 9)
            require(inputs.all { it.position.block.signum() > 0 && it.position.block == inputs[0].position.block && it.position.ordinal == inputs[0].position.ordinal } &&
                inputs[1].position.index in 1..254 && inputs.map { it.position.index } == (inputs[1].position.index - 1..inputs[1].position.index + 1).toList())
        }
        private fun message(source: JsonObject, product: StoreProduct, script: String, hash: String, witness: String): JsonObject = source.deepCopy().apply {
            listOf("request", "access_scripts", "fulfill_sale", "listing_signature").forEach(::remove)
            addProperty("module", "Store"); addProperty("request", "delist-asset"); addProperty("listing_signature", product.signature)
            addProperty("seller", product.seller); addProperty("nft_id", product.nftId); addProperty("quantity", product.quantity)
            addProperty("access_script", script); addProperty("access_hash", hash); addProperty("p2sh_address", "00" + hash)
            add("access_scripts", com.google.gson.JsonArray().apply { add(witness) })
        }
        private fun outputPreimage(rows: List<SaitoNft.Row>): ByteArray = ByteBuffer.allocate(rows.size * 43).apply {
            for (row in rows) put(Base58.decode(row.publicKey)).putLong(row.position.amount.toLong()).put(row.position.index.toByte()).put(row.position.type.toByte())
        }.array()
        private fun digest(bytes: ByteArray): ByteArray = ByteArray(32).also { result -> Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) } }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

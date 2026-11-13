package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** One exact file/key binding. Transmission and current funding/ownership observations remain caller-owned. */
class SaitoVaultMint private constructor(val author: String, val nftId: String, val file: SaitoTransaction,
    val mint: SaitoTransaction, inputs: List<SaitoBalanceSnapshot.Slip>, val timestamp: Long) {
    val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    val deposit: BigInteger get() = DEPOSIT

    fun uploadRequest(wallet: SaitoWallet, timestamp: Long): SaitoTransaction {
        require(SaitoEnvelope.decode(file.envelope()).author == wallet.publicKey)
        return SaitoTransaction.createVaultMessage(wallet, JsonObject().apply {
            addProperty("request", "vault add file"); addProperty("data", file.envelope())
        }, timestamp)
    }

    class Candidate internal constructor(val transaction: SaitoTransaction, val author: String, val nftId: String,
        val fileId: String, val name: String, inputs: List<SaitoBalanceSnapshot.Slip>, val timestamp: Long,
        val accessScript: SaitoAccessScript?, val keyType: String) {
        val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    }

    companion object {
        val DEPOSIT: BigInteger = BigInteger("100000000")
        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, name: String,
            mime: String, bytes: ByteArray, timestamp: Long, accessScript: SaitoAccessScript? = null,
            rental: Boolean = false): SaitoVaultMint {
            require(inputs.size in 1..254 && name.isNotBlank() && name.length <= 1024 && timestamp >= 0)
            require(mime.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) && bytes.size <= SaitoVault.MAX_FILE_BYTES)
            val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
            var total = BigInteger.ZERO
            inputs.forEach {
                require(it.block.signum() > 0 && it.block.bitLength() <= 64 && it.ordinal.signum() >= 0 && it.ordinal.bitLength() <= 64)
                require(it.index in 0..255 && it.type in setOf(0, 1, 5, 7) && it.amount.signum() > 0 && it.amount.bitLength() <= 64)
                require(locations.add(Triple(it.block, it.ordinal, it.index)))
                total += it.amount; require(total.bitLength() <= 64)
            }
            require(total >= DEPOSIT) { "Insufficient SAITO for the Vault key deposit" }
            val custom = accessScript?.withoutWitnesses()
            require(!rental || custom?.hash == SaitoVaultRules.rental(wallet.publicKey).hash) { "Rental keys use the bound official master rules" }
            val keyType = if (rental) "vault-nft-rental" else "vault-nft-key"
            val source = inputs.first()
            val id = ByteBuffer.allocate(33).putLong(source.block.toLong()).putLong(source.ordinal.toLong())
                .put(source.index.toByte()).put(keyType.toByteArray(Charsets.UTF_8)).array().hex()
            val script = custom?.let { com.google.gson.JsonParser.parseString(it.source).asJsonObject } ?: JsonObject().apply {
                addProperty("op", "CHECKOWNNFT"); addProperty("nftid", id)
                add("witness", JsonObject().apply {
                    addProperty("utxokey1", ""); addProperty("utxokey2", ""); addProperty("utxokey3", "")
                })
            }
            val hash = SaitoAccessScript.parse(script.toString()).hash
            val file = SaitoTransaction.createVaultMessage(wallet, JsonObject().apply {
                addProperty("request", "vault add file"); addProperty("access_script", script.toString()); addProperty("access_hash", hash)
                add("data", JsonObject().apply {
                    addProperty("file", "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)); addProperty("name", name)
                })
            }, timestamp)
            val mint = SaitoTransaction.createVaultMint(wallet, JsonObject().apply {
                addProperty("module", "Vault"); addProperty("request", "mint-vault-key")
                add("data", JsonObject().apply {
                    addProperty("link", "https://saito.io/vault"); addProperty("filename", name); addProperty("file_id", file.id)
                    custom?.let { add("file_access_script", com.google.gson.JsonParser.parseString(it.source)) }
                })
            }, timestamp, inputs, id, DEPOSIT)
            return SaitoVaultMint(wallet.publicKey, id, file, mint, inputs, timestamp)
        }
        /** Authenticate one exact self-mint and its original access rules before restoring its reservation. */
        fun readCandidate(encoded: String): Candidate {
            require(encoded.length <= 128 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size in 330..64 * 1024)
            val header = ByteBuffer.wrap(bytes)
            val inputCount = header.getInt(0); val outputCount = header.getInt(4)
            require(inputCount in 1..254 && outputCount in 3..4 && header.getInt(12) == 0 &&
                header.getInt(88) == 1 && bytes[92] == 8.toByte() && header.getLong(80) >= 0)
            require(header.getInt(8) > 0 && bytes.size == 93 + 59 * (inputCount + outputCount) + header.getInt(8))
            val transaction = SaitoTransaction.fromVaultMintBytes(bytes)
            val signed = SaitoEnvelope.decodeBound(transaction.envelope())
            fun row(index: Int): SaitoNft.Row {
                val start = 93 + index * 59
                return SaitoNft.Row(Base58.encode(bytes.copyOfRange(start, start + 33)), SaitoBalanceSnapshot.Slip(
                    BigInteger(1, bytes.copyOfRange(start + 41, start + 49)), BigInteger(1, bytes.copyOfRange(start + 49, start + 57)),
                    bytes[start + 57].toInt() and 255, BigInteger(1, bytes.copyOfRange(start + 33, start + 41)), bytes[start + 58].toInt() and 255))
            }
            val locations = HashSet<Triple<BigInteger, BigInteger, Int>>()
            val inputs = (0 until inputCount).map { index -> row(index).also {
                require(it.publicKey == signed.author && it.position.type in setOf(0, 1, 5, 7) &&
                    it.position.block.signum() > 0 && it.position.amount.signum() > 0)
                require(locations.add(Triple(it.position.block, it.position.ordinal, it.position.index)))
            }.position }
            val total = inputs.fold(BigInteger.ZERO) { sum, value -> sum + value.amount }
            require(total.bitLength() <= 64 && total >= DEPOSIT)
            val outputs = (0 until outputCount).map { index -> row(inputCount + index).also {
                require(it.position.block.signum() == 0 && it.position.ordinal.signum() == 0 && it.position.index == index)
            } }
            val holding = SaitoNft.fromSnapshot(outputs[1], outputs[0], outputs[2])
            require(holding.type in setOf("vault-nft-key", "vault-nft-rental"))
            val first = inputs.first()
            val expectedId = ByteBuffer.allocate(33).putLong(first.block.toLong()).putLong(first.ordinal.toLong())
                .put(first.index.toByte()).put(holding.type.toByteArray(Charsets.UTF_8)).array().hex()
            require(holding.owner == signed.author && holding.creator == signed.author && holding.quantity == BigInteger.ONE &&
                holding.deposit == DEPOSIT && holding.id == expectedId && outputs[1].position.type == 0)
            val change = total - DEPOSIT
            if (outputs.size == 3) require(change.signum() == 0)
            else require(outputs[3].publicKey == signed.author && outputs[3].position.type == 0 && outputs[3].position.amount == change)
            val body = signed.body
            require(body.keySet() == setOf("module", "request", "data") && body.get("module").asString == "Vault" &&
                body.get("request").asString == "mint-vault-key")
            val data = body.getAsJsonObject("data")
            require(data.keySet().containsAll(setOf("link", "filename", "file_id")) &&
                (data.keySet() - setOf("link", "filename", "file_id")).all { it == "file_access_script" } &&
                data.get("link").asString == "https://saito.io/vault")
            val custom = data.get("file_access_script")?.let { value ->
                SaitoAccessScript.parse(if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else value.toString())
            }
            require(holding.type != "vault-nft-rental" || custom?.hash == SaitoVaultRules.rental(signed.author).hash)
            val fileId = data.get("file_id").asString.also { require(it.matches(Regex("[0-9a-f]{128}"))) }
            val name = data.get("filename").asString.also { require(it.isNotBlank() && it.length <= 1024) }
            return Candidate(transaction, signed.author, holding.id, fileId, name, inputs, signed.createdAt, custom, holding.type)
        }

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    }
}

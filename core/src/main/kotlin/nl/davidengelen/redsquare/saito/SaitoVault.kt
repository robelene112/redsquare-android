package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake3Digest

class VaultKey internal constructor(val holding: SaitoNft, val name: String, val fileId: String,
    val envelope: String, val customScript: String?)

/** Local contents; remote authentication is performed by readFile before constructing this value. */
class VaultFile(val id: String, val name: String, val mime: String, bytes: ByteArray) {
    init {
        require(id.matches(Regex("[0-9a-f]{128}")) && name.isNotBlank() && name.length <= 1024)
        require(mime.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) && bytes.size <= SaitoVault.MAX_FILE_BYTES)
    }
    private val content = bytes.copyOf()
    val bytes: ByteArray get() = content.copyOf()
    val size: Int get() = content.size
}

/** Official standard Vault access. Advanced and rental contracts are admitted by their own later flows. */
object SaitoVault {
    private val signature = Regex("[0-9a-f]{128}")
    private val mime = Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")
    // The nested signed web envelope stays within the existing 8 MiB authentication budget.
    const val MAX_FILE_BYTES = 4 * 1024 * 1024

    fun readKey(holding: SaitoNft, envelope: String): VaultKey {
        require(holding.type in setOf("vault", "vault-nft-key"))
        require(envelope.toByteArray(Charsets.UTF_8).size <= 128 * 1024) { "Unsupported Vault key metadata size" }
        val signed = SaitoEnvelope.decodeBound(envelope)
        require(signed.body.get("module")?.asString == "Vault" && signed.body.get("request")?.asString == "mint-vault-key")
        val detached = Base64.getDecoder().decode(JsonParser.parseString(envelope).asJsonObject.get("t").asString)
        val header = ByteBuffer.wrap(detached)
        val inputs = header.getInt(0)
        val outputs = header.getInt(4)
        fun row(index: Int): SaitoNft.Row {
            val start = 93 + 59 * (inputs + index)
            return SaitoNft.Row(Base58.encode(detached.copyOfRange(start, start + 33)),
                SaitoBalanceSnapshot.Slip(BigInteger(1, detached.copyOfRange(start + 41, start + 49)),
                    BigInteger(1, detached.copyOfRange(start + 49, start + 57)), detached[start + 57].toInt() and 255,
                    BigInteger(1, detached.copyOfRange(start + 33, start + 41)), detached[start + 58].toInt() and 255))
        }
        require((0 until (outputs - 2).coerceAtLeast(0)).any { index ->
            try {
                val tuple = SaitoNft.fromSnapshot(row(index + 1), row(index), row(index + 2))
                tuple.id == holding.id && tuple.creator == holding.creator
            } catch (_: Exception) { false }
        }) { "Metadata does not describe this key" }
        val data = signed.body.getAsJsonObject("data")
        val fileId = data.string("file_id").also { require(signature.matches(it)) }
        val name = data.string("filename").also { require(it.isNotBlank() && it.length <= 1024) }
        val custom = data.get("file_access_script")?.takeUnless { it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else it.toString()
        }
        return VaultKey(holding, name, fileId, envelope, custom)
    }

    fun archiveRequest(holding: SaitoNft): String = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field4", holding.id); addProperty("limit", 10)
        })
    }.toString()

    fun readKeyArchive(holding: SaitoNft, response: String): VaultKey? =
        readArchiveRows(response, { readKey(holding, it) }, { true }, { it.fileId }).entries.singleOrNull()

    fun access(wallet: SaitoWallet, key: VaultKey, timestamp: Long, supplied: SaitoAccessScript? = null): SaitoTransaction {
        require(wallet.publicKey == key.holding.owner)
        val script: String
        val hash: String
        if (key.customScript != null) {
            val witnessed = requireNotNull(supplied) { "Supply the custom access inputs before requesting this file" }
            require(witnessed.hash == SaitoAccessScript.parse(key.customScript).hash) { "Access rules do not match this key" }
            script = witnessed.source; hash = witnessed.hash
        } else {
            require(supplied == null) { "Standard keys use their current ownership witnesses" }
            val witness = key.holding.witnesses
            script = JsonObject().apply {
                addProperty("op", "CHECKOWNNFT"); addProperty("nftid", key.holding.id)
                add("witness", JsonObject().apply {
                    addProperty("utxokey1", witness[0]); addProperty("utxokey2", witness[1]); addProperty("utxokey3", witness[2])
                })
            }.toString()
            hash = standardHash(key.holding.id)
        }
        return SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("request", "vault access file")
            add("data", JsonObject().apply {
                addProperty("request", "vault access file"); addProperty("access_script", script); addProperty("access_hash", hash)
                add("data", JsonObject().apply { addProperty("file_id", key.fileId) })
            })
        }, timestamp)
    }

    fun readFile(key: VaultKey, response: String): VaultFile {
        require(response.toByteArray().size <= 16 * 1024 * 1024)
        val result = JsonParser.parseString(response).asJsonObject
        require(result.get("status")?.asString == "success") { "Vault did not authorise file access" }
        val candidates = result.getAsJsonArray("txs").also { require(it.size() in 1..10) }
        val matched = candidates.mapNotNull { candidate ->
            try {
                val envelope = if (candidate.isJsonPrimitive && candidate.asJsonPrimitive.isString) candidate.asString
                    else if (candidate.isJsonObject && candidate.asJsonObject.has("tx")) candidate.asJsonObject.string("tx")
                    else candidate.toString()
                SaitoEnvelope.decode(envelope).takeIf { it.id == key.fileId && it.author == key.holding.creator }
            } catch (_: Exception) { null }
        }.distinctBy { it.id }.singleOrNull() ?: throw IllegalArgumentException("The requested signed file is unavailable")
        require(matched.body.get("request")?.asString == "vault add file")
        val commitment = key.customScript?.let { SaitoAccessScript.parse(it).hash } ?: standardHash(key.holding.id)
        require(matched.body.string("access_hash") == commitment) { "File access commitment does not match this key" }
        if (key.customScript == null) {
            val contract = JsonParser.parseString(matched.body.string("access_script")).asJsonObject
            require(contract.keySet() == setOf("op", "nftid", "witness") && contract.get("witness").isJsonObject)
            require(contract.string("op") == "CHECKOWNNFT" && contract.string("nftid") == key.holding.id)
        } else {
            require(SaitoAccessScript.parse(matched.body.string("access_script")).hash == commitment) {
                "The signed file uses different access rules"
            }
        }
        val data = matched.body.getAsJsonObject("data")
        val name = data.string("name").also { require(it.isNotBlank() && it.length <= 1024) }
        val encoded = data.string("file")
        val comma = encoded.indexOf(',').also { require(it in 12..4096) }
        val header = encoded.substring(0, comma)
        require(header.startsWith("data:") && header.endsWith(";base64"))
        val contentType = header.substring(5, header.length - 7).substringBefore(';').also { require(mime.matches(it)) }
        val base64 = encoded.substring(comma + 1)
        require(base64.length <= (MAX_FILE_BYTES + 2) / 3 * 4)
        val bytes = Base64.getDecoder().decode(base64)
        require(bytes.size <= MAX_FILE_BYTES && Base64.getEncoder().encodeToString(bytes) == base64)
        return VaultFile(key.fileId, name, contentType, bytes)
    }

    private fun standardHash(id: String): String {
        // Rust Script::hash strips witness recursively and sorts the remaining object keys.
        val commitment = JsonObject().apply { addProperty("nftid", id); addProperty("op", "CHECKOWNNFT") }
            .toString().toByteArray(Charsets.UTF_8)
        return ByteArray(32).also { digest ->
            Blake3Digest(256).apply { update(commitment, 0, commitment.size); doFinal(digest, 0) }
        }.joinToString("") { "%02x".format(it) }
    }

    private fun JsonObject.string(name: String): String {
        val value = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true)
        return value.asString
    }
}

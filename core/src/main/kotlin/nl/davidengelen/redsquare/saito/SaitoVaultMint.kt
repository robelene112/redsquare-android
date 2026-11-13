package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake3Digest

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

    companion object {
        val DEPOSIT: BigInteger = BigInteger("100000000")
        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, name: String,
            mime: String, bytes: ByteArray, timestamp: Long): SaitoVaultMint {
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
            val source = inputs.first()
            val id = ByteBuffer.allocate(33).putLong(source.block.toLong()).putLong(source.ordinal.toLong())
                .put(source.index.toByte()).put("vault-nft-key".toByteArray(Charsets.UTF_8)).array().hex()
            val script = JsonObject().apply {
                addProperty("op", "CHECKOWNNFT"); addProperty("nftid", id)
                add("witness", JsonObject().apply {
                    addProperty("utxokey1", ""); addProperty("utxokey2", ""); addProperty("utxokey3", "")
                })
            }
            val commitment = JsonObject().apply { addProperty("nftid", id); addProperty("op", "CHECKOWNNFT") }
                .toString().toByteArray(Charsets.UTF_8)
            val hash = ByteArray(32).also { digest ->
                Blake3Digest(256).apply { update(commitment, 0, commitment.size); doFinal(digest, 0) }
            }.hex()
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
                })
            }, timestamp, inputs, id, DEPOSIT)
            return SaitoVaultMint(wallet.publicKey, id, file, mint, inputs, timestamp)
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    }
}

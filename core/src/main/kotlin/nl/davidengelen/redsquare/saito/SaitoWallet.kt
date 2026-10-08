package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.security.SecureRandom
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator

/** Saito-compatible identity. toString intentionally never exposes signing material. */
class SaitoWallet private constructor(
    private val scalar: BigInteger,
    private val backupOptions: JsonObject? = null,
) {
    val publicKey: String = Base58.encode(curve.g.multiply(scalar).getEncoded(true))

    fun sign(message: ByteArray): String {
        val digest = hash(message)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(scalar, domain))
        val (r, originalS) = signer.generateSignature(digest)
        val lowS = if (originalS > curve.n.shiftRight(1)) curve.n - originalS else originalS
        return r.toString(16).padStart(64, '0') + lowS.toString(16).padStart(64, '0')
    }

    /** Contains private material. Only pass to protected storage or an authenticated export. */
    fun exportJson(): String {
        val options = backupOptions?.deepCopy() ?: JsonObject().apply {
            add("wallet", JsonObject().apply {
                addProperty("version", 5.677)
                addProperty("balance", "0")
                addProperty("preferred_crypto", "SAITO")
                add("preferred_txs", JsonArray())
                addProperty("default_fee", "0")
                add("slips", JsonArray())
            })
        }
        options.getAsJsonObject("wallet").apply {
            addProperty("publicKey", publicKey)
            addProperty("privateKey", scalar.toString(16).padStart(64, '0'))
        }
        return gson.toJson(options)
    }

    override fun toString(): String = "SaitoWallet(publicKey=$publicKey)"

    companion object {
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()
        private val curve = CustomNamedCurves.getByName("secp256k1")
        private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)

        fun verify(message: ByteArray, signature: String, publicKey: String): Boolean {
            if (!signature.matches(Regex("[0-9a-fA-F]{128}"))) return false
            return try {
                val r = BigInteger(signature.substring(0, 64), 16)
                val s = BigInteger(signature.substring(64), 16)
                if (r.signum() <= 0 || r >= curve.n || s.signum() <= 0 || s > curve.n.shiftRight(1)) return false
                val encoded = Base58.decode(publicKey)
                if (encoded.size != 33 || encoded[0] !in listOf(2.toByte(), 3.toByte())) return false
                val point = curve.curve.decodePoint(encoded)
                val signer = ECDSASigner()
                signer.init(false, ECPublicKeyParameters(point, domain))
                signer.verifySignature(hash(message), r, s)
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        private fun hash(message: ByteArray): ByteArray = ByteArray(32).also { output ->
            Blake3Digest(256).apply { update(message, 0, message.size); doFinal(output, 0) }
        }

        fun create(): SaitoWallet {
            val random = SecureRandom()
            while (true) {
                val scalar = BigInteger(1, ByteArray(32).also(random::nextBytes))
                if (scalar.signum() > 0 && scalar < curve.n) {
                    val wallet = SaitoWallet(scalar)
                    if (wallet.publicKey.length == 44) return wallet
                }
            }
        }

        fun fromBackup(json: String): SaitoWallet {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Wallet backup is too large" }
            try {
                val options = gson.fromJson(json, JsonObject::class.java)
                    ?: throw IllegalArgumentException()
                val wallet = options.getAsJsonObject("wallet")
                    ?: throw IllegalArgumentException()
                val version = wallet.get("version")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                require(version != null && (version.isNumber || version.isString))
                val privateKey = wallet.get("privateKey")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                val publicKey = wallet.get("publicKey")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
                require(privateKey?.isString == true && publicKey?.isString == true)
                val restored = fromPrivateKey(privateKey.asString)
                require(restored.publicKey == publicKey.asString)
                return SaitoWallet(restored.scalar, options)
            } catch (_: RuntimeException) {
                // Parser errors may contain file values; never include their cause in a log/UI error.
                throw IllegalArgumentException("Invalid wallet backup")
            }
        }

        fun fromPrivateKey(privateKey: String): SaitoWallet {
            require(privateKey.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid wallet key" }
            val scalar = BigInteger(privateKey, 16)
            require(scalar.signum() > 0 && scalar < curve.n) { "Invalid wallet key" }
            return SaitoWallet(scalar)
        }
    }
}

internal object Base58 {
    private const val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val radix = BigInteger.valueOf(58)

    fun decode(text: String): ByteArray {
        require(text.isNotEmpty() && text.length <= 64) { "Invalid public address" }
        var number = BigInteger.ZERO
        text.forEach { character ->
            val digit = alphabet.indexOf(character)
            require(digit >= 0) { "Invalid public address" }
            number = number * radix + BigInteger.valueOf(digit.toLong())
        }
        val raw = number.toByteArray().let {
            if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        return ByteArray(text.takeWhile { it == '1' }.length) + raw
    }

    fun encode(bytes: ByteArray): String {
        var number = BigInteger(1, bytes)
        val reversed = StringBuilder()
        while (number.signum() > 0) {
            val (quotient, remainder) = number.divideAndRemainder(radix)
            reversed.append(alphabet[remainder.toInt()])
            number = quotient
        }
        bytes.takeWhile { it == 0.toByte() }.forEach { reversed.append('1') }
        return reversed.reverse().toString()
    }
}

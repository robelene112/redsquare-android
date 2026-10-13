package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.security.SecureRandom
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.Strictness
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECPoint

/** Saito-compatible identity. toString intentionally never exposes signing material. */
class SaitoWallet private constructor(
    private val scalar: BigInteger,
    private val backupOptions: JsonObject? = null,
) {
    val publicKey: String = Base58.encode(curve.g.multiply(scalar).getEncoded(true))

    /** Safe, cached indicators only. No module execution or asset balance claim. */
    data class ReplacementRisks(val savedName: String?, val otherAssetsUnknown: Boolean, val pendingWork: Boolean)

    fun replacementRisks(): ReplacementRisks {
        val options = backupOptions ?: return ReplacementRisks(null, false, false)
        fun populated(value: JsonElement?): Boolean = when {
            value == null || value.isJsonNull -> false
            value.isJsonArray -> !value.asJsonArray.isEmpty
            value.isJsonObject -> value.asJsonObject.size() > 0
            else -> true // Malformed or unsupported values are not evidence of absence.
        }
        val wallet = options.getAsJsonObject("wallet")
        val preferred = wallet.get("preferred_crypto")
        val otherCrypto = options.get("crypto")?.let { value ->
            if (!value.isJsonObject) populated(value)
            else value.asJsonObject.entrySet().any { (ticker, data) -> ticker != "SAITO" && populated(data) }
        } ?: false
        val specialSlips = wallet.get("slips")?.let { value ->
            if (!value.isJsonArray) populated(value)
            else value.asJsonArray.any { slip ->
                val type = slip.takeIf { it.isJsonObject }?.asJsonObject?.get("slipType")
                // WalletSlip.toJson emits this numeric enum. A SAITO snapshot
                // excludes Bound/NFT slips and cannot clear cached special assets.
                type == null || !type.isJsonPrimitive || !type.asJsonPrimitive.isNumber ||
                    type.asString !in listOf("0", "1", "5", "7")
            }
        } ?: false
        val otherAssets = specialSlips || otherCrypto ||
            (preferred != null && !(preferred.isJsonPrimitive && preferred.asJsonPrimitive.isString && preferred.asString == "SAITO")) ||
            listOf("mixin", "mixin_legacy", "nfts", "nft").any { populated(options.get(it)) || populated(wallet.get(it)) }
        val pending = listOf(options.get("pending_txs"), wallet.get("pending_txs"), wallet.get("pending")).any(::populated)
        val name = options.get("keys")?.takeIf { it.isJsonArray }?.asJsonArray?.firstNotNullOfOrNull { entry ->
            if (!entry.isJsonObject) return@firstNotNullOfOrNull null
            val key = entry.asJsonObject
            if (key.get("publicKey")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString != publicKey)
                return@firstNotNullOfOrNull null
            val identifier = key.get("identifier")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: return@firstNotNullOfOrNull null
            val local = identifier.lastIndexOf('@').let { if (it > 0) identifier.substring(0, it) else identifier }
                .trim().replace(Regex("\\s+"), " ")
            // Compatible Saito names can have 45 ASCII base characters. This is a
            // cached replacement warning, not a verified ownership assertion.
            local.takeIf { identifier.matches(Regex("[A-Za-z0-9]{1,45}@saito")) ||
                (it.codePointCount(0, it.length) in 1..16 && it.matches(Regex("[\\p{L}\\p{N} _-]+"))) }
        }
        return ReplacementRisks(name, otherAssets, pending)
    }

    fun sign(message: ByteArray): String {
        val digest = hash(message)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(scalar, domain))
        val (r, originalS) = signer.generateSignature(digest)
        val lowS = if (originalS > curve.n.shiftRight(1)) curve.n - originalS else originalS
        return r.toString(16).padStart(64, '0') + lowS.toString(16).padStart(64, '0')
    }

    /** Private Chat secret: raw shared-point X, matching Saito's pinned Rust implementation. */
    internal fun chatSharedSecret(counterparty: String): ByteArray =
        publicPoint(counterparty).multiply(scalar).normalize().affineXCoord.encoded

    /** Saved key first, then implicit identity ECDH for older messages. Never export secrets to the UI. */
    internal fun chatSharedSecrets(counterparty: String): List<String> {
        require(isPublicKey(counterparty) && counterparty != publicKey)
        val keys = backupOptions?.get("keys")
        require(keys == null || keys.isJsonArray) { "Invalid saved Chat keys" }
        val entry = keys?.asJsonArray?.firstOrNull { value ->
            if (!value.isJsonObject) false else {
                val row = value.asJsonObject
                val address = row.get("publicKey")?.takeUnless { it.isJsonNull ||
                    (it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isEmpty()) } ?: row.get("publickey")
                address?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == counterparty } == true
            }
        }?.asJsonObject
        val saved = entry?.get("aes_secret")?.takeUnless { it.isJsonNull ||
            (it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isEmpty()) }
        val secret = saved?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Invalid saved Chat key"
            }
            it.asString // CryptoJS treats this as a case-sensitive passphrase, not decoded key bytes.
        }
        return listOfNotNull(secret, chatSharedSecret(counterparty).joinToString("") { "%02x".format(it) })
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
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).serializeNulls().disableHtmlEscaping().create()
        private val curve = CustomNamedCurves.getByName("secp256k1")
        private val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)

        /** A canonical compressed Saito address must encode an actual curve point. */
        fun isPublicKey(publicKey: String): Boolean = try {
            publicPoint(publicKey)
            true
        } catch (_: IllegalArgumentException) { false }

        private fun publicPoint(publicKey: String): ECPoint {
            val encoded = Base58.decode(publicKey)
            require(encoded.size == 33 && encoded[0] in listOf(2.toByte(), 3.toByte()))
            require(Base58.encode(encoded) == publicKey)
            return curve.curve.decodePoint(encoded).also { require(!it.isInfinity && it.isValid) }
        }

        fun verify(message: ByteArray, signature: String, publicKey: String): Boolean {
            if (!signature.matches(Regex("[0-9a-fA-F]{128}"))) return false
            return try {
                val r = BigInteger(signature.substring(0, 64), 16)
                val s = BigInteger(signature.substring(64), 16)
                if (r.signum() <= 0 || r >= curve.n || s.signum() <= 0 || s > curve.n.shiftRight(1)) return false
                val point = publicPoint(publicKey)
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

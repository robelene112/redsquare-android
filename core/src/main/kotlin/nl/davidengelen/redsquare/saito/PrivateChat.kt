package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PrivateChatMessage(val id: String, val author: String, val conversationId: String,
    val createdAt: Long, val text: String)

/** Authenticated private two-person Chat. Never return plaintext on decryption failure. */
object PrivateChat {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).disableHtmlEscaping().create()

    fun read(wallet: SaitoWallet, signedInner: JsonObject, counterparty: String): PrivateChatMessage =
        read(wallet, SaitoEnvelope.fromSignedJson(signedInner), counterparty)

    fun create(wallet: SaitoWallet, counterparty: String, text: String, timestamp: Long,
        randomSalt: () -> ByteArray = { ByteArray(8).also(SecureRandom()::nextBytes) }): SaitoTransaction {
        val group = conversationId(wallet.publicKey, counterparty)
        require(timestamp >= 0 && text.isNotBlank())
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat message")
            addProperty("group_id", group)
            addProperty("message", text)
            addProperty("timestamp", timestamp)
            add("mentioned", com.google.gson.JsonArray())
        }
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(body.toString()))
        require(encoded.remaining() <= SaitoTransaction.MAX_MESSAGE_BYTES)
        val plaintext = ByteArray(encoded.remaining()).also { encoded.get(it) }
        val salt = randomSalt().copyOf()
        require(salt.size == 8)
        val derived = derive(wallet.chatSharedSecrets(counterparty).first(), salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived.copyOfRange(0, 32), "AES"),
            IvParameterSpec(derived.copyOfRange(32, 48)))
        val formatter = JsonObject().apply {
            addProperty("ct", Base64.getEncoder().encodeToString(cipher.doFinal(plaintext)))
            addProperty("iv", derived.copyOfRange(32, 48).joinToString("") { "%02x".format(it) })
            addProperty("s", salt.joinToString("") { "%02x".format(it) })
        }
        return SaitoTransaction.createEncrypted(wallet, formatter.toString(), timestamp, counterparty)
    }

    fun read(wallet: SaitoWallet, envelope: String, counterparty: String): PrivateChatMessage {
        require(SaitoWallet.isPublicKey(counterparty) && counterparty != wallet.publicKey)
        val signed = SaitoEnvelope.decodePayload(envelope)
        val participants = setOf(wallet.publicKey, counterparty)
        require(signed.author in participants && signed.recipients.toSet() == participants)
        require(signed.body.isJsonPrimitive && signed.body.asJsonPrimitive.isString)
        val formatter = gson.fromJson(signed.body.asString, JsonObject::class.java)
        fun cipherString(name: String): String {
            val value = formatter.get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
        val salt = hex(cipherString("s"), 8)
        hex(cipherString("iv"), 16)
        val ciphertext = Base64.getDecoder().decode(cipherString("ct"))
        require(ciphertext.isNotEmpty() && ciphertext.size <= SaitoTransaction.MAX_MESSAGE_BYTES && ciphertext.size % 16 == 0)
        val body = wallet.chatSharedSecrets(counterparty).firstNotNullOfOrNull { secret ->
            try {
                val derived = derive(secret, salt)
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived.copyOfRange(0, 32), "AES"),
                    IvParameterSpec(derived.copyOfRange(32, 48)))
                val plaintext = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cipher.doFinal(ciphertext))).toString()
                gson.fromJson(plaintext, JsonObject::class.java)
            } catch (_: Exception) { null }
        } ?: throw IllegalArgumentException("Unable to decrypt private message")
        fun string(name: String): String {
            val value = body.get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
        require(string("module") == "Chat" && string("request") == "chat message")
        val group = conversationId(wallet.publicKey, counterparty)
        require(string("group_id") == group)
        val text = string("message")
        return PrivateChatMessage(signed.id, signed.author, group, signed.createdAt, text)
    }

    fun conversationId(owner: String, counterparty: String): String {
        require(SaitoWallet.isPublicKey(owner) && SaitoWallet.isPublicKey(counterparty) && owner != counterparty)
        val bytes = listOf(owner, counterparty).sorted().joinToString("_").toByteArray(Charsets.UTF_8)
        val hash = ByteArray(32)
        Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(hash, 0) }
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hex(value: String, bytes: Int): ByteArray {
        require(value.matches(Regex("[0-9a-fA-F]{${bytes * 2}}")))
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun derive(secret: String, salt: ByteArray): ByteArray {
        val passphrase = secret.toByteArray(Charsets.US_ASCII)
        val derived = ByteArray(48)
        var previous = ByteArray(0)
        repeat(3) { index ->
            previous = MessageDigest.getInstance("MD5").digest(previous + passphrase + salt)
            previous.copyInto(derived, index * 16)
        }
        return derived
    }
}

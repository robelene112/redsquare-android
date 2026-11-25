package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import nl.davidengelen.redsquare.saito.Stun
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** One peer's key for a single live admission. The durable wallet signs its binding. */
class SpaceVoiceSignalKey private constructor(
    private val address: Address,
    val rtcWallet: SaitoWallet,
    val contact: Contact,
) {
    data class Address(val authority: String, val space: String, val channel: String) {
        init {
            require(SaitoWallet.isPublicKey(authority))
            require(space.matches(ROOM_ID) && channel.matches(ROOM_ID))
        }
        internal fun domain() = "$authority/$space/$channel"
    }

    data class Contact(val wallet: String, val session: String, val key: String, val signature: String) {
        init {
            require(SaitoWallet.isPublicKey(wallet) && SaitoWallet.isPublicKey(key) && wallet != key)
            require(SpaceVoiceRoom.validSession(session) && signature.matches(SIGNATURE))
        }

        fun verify(address: Address): Boolean = SaitoWallet.verify(binding(address, wallet, session, key), signature, wallet)

        fun json() = JsonObject().apply {
            addProperty("wallet", wallet)
            addProperty("session", session)
            addProperty("key", key)
            addProperty("signature", signature)
        }

        companion object {
            fun read(body: JsonObject): Contact {
                require(body.keySet() == setOf("wallet", "session", "key", "signature"))
                fun text(field: String) = body.getAsJsonPrimitive(field).let { require(it.isString); it.asString }
                return Contact(text("wallet"), text("session"), text("key"), text("signature"))
            }
        }
    }

    /** Relay metadata contains session identifiers and ciphertext only. */
    data class Packet(val from: String, val to: String, val ciphertext: String) {
        init {
            require(SpaceVoiceRoom.validSession(from) && SpaceVoiceRoom.validSession(to) && from != to)
            require(ciphertext.length in 40..MAX_ENCODED)
        }

        fun json() = JsonObject().apply {
            addProperty("from", from)
            addProperty("to", to)
            addProperty("ciphertext", ciphertext)
        }

        companion object {
            fun read(body: JsonObject): Packet {
                require(body.keySet() == setOf("from", "to", "ciphertext"))
                fun text(field: String) = body.getAsJsonPrimitive(field).let { require(it.isString); it.asString }
                return Packet(text("from"), text("to"), text("ciphertext"))
            }
        }
    }

    fun seal(recipient: Contact, envelope: String): Packet {
        require(recipient.verify(address) && recipient.wallet != contact.wallet)
        val plain = envelope.toByteArray(Charsets.UTF_8)
        require(plain.size in 1..MAX_PLAIN)
        val signed = requireNotNull(Stun.read(envelope, contact.key, recipient.key))
        require(signed.body.get("request").asString in SIGNALS)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encrypted = cipher(Cipher.ENCRYPT_MODE, recipient.key, contact, recipient, nonce).doFinal(plain)
        return Packet(contact.session, recipient.session, Base64.getEncoder().encodeToString(nonce + encrypted))
    }

    fun open(sender: Contact, packet: Packet): SignedSaitoMessage {
        require(sender.verify(address) && sender.wallet != contact.wallet)
        require(packet.from == sender.session && packet.to == contact.session)
        val encoded = Base64.getDecoder().decode(packet.ciphertext)
        require(encoded.size in 29..MAX_PLAIN + 28)
        val nonce = encoded.copyOfRange(0, 12)
        val plain = cipher(Cipher.DECRYPT_MODE, sender.key, sender, contact, nonce).doFinal(encoded, 12, encoded.size - 12)
        val signed = requireNotNull(Stun.read(plain.decodeToString(throwOnInvalidSequence = true), sender.key, contact.key))
        require(signed.body.get("request").asString in SIGNALS)
        return signed
    }

    private fun cipher(mode: Int, counterparty: String, sender: Contact, recipient: Contact, nonce: ByteArray): Cipher {
        val domain = ("SaitoSpacesRoomSignal/v1/${address.domain()}/" +
            "${sender.wallet}/${sender.session}/${sender.key}/${recipient.wallet}/${recipient.session}/${recipient.key}")
            .toByteArray(Charsets.UTF_8)
        val secret = rtcWallet.chatSharedSecret(counterparty)
        val key = ByteArray(32)
        try {
            HKDFBytesGenerator(SHA256Digest()).apply {
                init(HKDFParameters(secret, null, domain))
                generateBytes(key, 0, key.size)
            }
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(domain)
            }
        } finally {
            secret.fill(0)
            key.fill(0)
        }
    }

    companion object {
        private const val MAX_PLAIN = 48 * 1024
        private const val MAX_ENCODED = ((MAX_PLAIN + 28 + 2) / 3) * 4
        private val ROOM_ID = Regex("[0-9a-f]{64}")
        private val SIGNATURE = Regex("[0-9a-f]{128}")
        private val SIGNALS = setOf("peer-joined", "peer-description", "peer-candidate")

        fun create(address: Address, wallet: SaitoWallet, session: String = UUID.randomUUID().toString().replace("-", "")): SpaceVoiceSignalKey {
            require(SpaceVoiceRoom.validSession(session))
            val rtc = SaitoWallet.create()
            val binding = wallet.sign(binding(address, wallet.publicKey, session, rtc.publicKey))
            return SpaceVoiceSignalKey(address, rtc, Contact(wallet.publicKey, session, rtc.publicKey, binding))
        }

        private fun binding(address: Address, wallet: String, session: String, key: String) =
            "SaitoSpacesRoomIdentity/v1/${address.domain()}/$wallet/$session/$key".toByteArray(Charsets.UTF_8)
    }
}

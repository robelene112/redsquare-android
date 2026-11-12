package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/** Signed Saito signalling shared by calls and files. It never authenticates unsigned Relay claims. */
object Stun {
    private val requests = setOf("peer-joined", "peer-left", "peer-description", "peer-candidate", "peer-kicked", "peer-kick-broadcast")
    fun signal(wallet: SaitoWallet, peer: String, request: String, details: JsonObject, now: Long): SaitoTransaction {
        require(request in requests && SaitoWallet.isPublicKey(peer))
        return SaitoTransaction.create(wallet, details.deepCopy().apply {
            addProperty("module", "Stun"); addProperty("request", request)
        }, now, listOf(peer))
    }

    fun network(bytes: ByteArray, peer: String, owner: String): SignedSaitoMessage? =
        read(SaitoEnvelope.unverifiedNetworkEnvelope(bytes), peer, owner)

    fun read(envelope: String, peer: String, owner: String): SignedSaitoMessage? {
        if (envelope.length > 256 * 1024) return null
        val outer = JsonParser.parseString(envelope).asJsonObject
        val body = JsonParser.parseString(Base64.getDecoder().decode(outer.get("m").asString).toString(Charsets.UTF_8))
        if (!body.isJsonObject || body.asJsonObject.get("module") != com.google.gson.JsonPrimitive("Stun") ||
            body.asJsonObject.get("request")?.asString !in requests) return null
        val header = Base64.getDecoder().decode(outer.get("t").asString)
        require(header.size >= 152)
        // Ownership filtering grants no trust. The matching receiver still verifies the signature below.
        if (Base58.encode(header.copyOfRange(93, 126)) != peer) return null
        return SaitoEnvelope.decode(envelope).also { require(it.author == peer && owner in it.recipients) }
    }
}

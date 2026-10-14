package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject

/** Compatible unencrypted named-group controls; participants sign with their own wallet. */
object GroupChat {
    fun create(wallet: SaitoWallet, groupId: String, name: String, invitees: List<String>,
        timestamp: Long): SaitoTransaction {
        require(SaitoWallet.isPublicKey(groupId) && groupId != wallet.publicKey)
        require(name.isNotBlank())
        require(invitees.all { SaitoWallet.isPublicKey(it) && it != groupId })
        val recipients = invitees.distinct().filter { it != wallet.publicKey }
        require(recipients.size <= 254)
        val body = JsonObject().apply {
            addProperty("module", "Chat")
            addProperty("request", "chat group")
            addProperty("name", name)
            addProperty("admin", wallet.publicKey)
            addProperty("id", groupId)
        }
        return SaitoTransaction.create(wallet, body, timestamp, recipients)
    }
}

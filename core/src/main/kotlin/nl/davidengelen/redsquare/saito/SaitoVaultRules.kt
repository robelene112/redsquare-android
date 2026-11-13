package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser

/** Official Vault constitutions. Building a rule does not establish present access. */
object SaitoVaultRules {
    fun rental(creator: String): SaitoAccessScript = SaitoAccessScript.parse(template("rental", creator))

    fun loan(creator: String, renter: String, expiresAt: Long): SaitoAccessScript {
        require(SaitoWallet.isPublicKey(renter) && expiresAt >= 0)
        val script = JsonParser.parseString(template("loan", creator)).asJsonObject
        val branches = script.getAsJsonArray("args")
        val renterConditions = branches[0].asJsonObject.getAsJsonArray("args")
        val creatorConditions = branches[1].asJsonObject.getAsJsonArray("args")
        renterConditions[0].asJsonObject.addProperty("publickey", renter)
        renterConditions[1].asJsonObject.addProperty("value", expiresAt)
        creatorConditions[1].asJsonObject.addProperty("value", expiresAt)
        return SaitoAccessScript.parse(script.toString())
    }

    private fun template(name: String, creator: String): String {
        require(SaitoWallet.isPublicKey(creator))
        // The resource is the pinned official builder's output. Its only dynamic
        // master parameter is the validated creator, including the embedded loan.
        return requireNotNull(javaClass.getResourceAsStream("/vault-$name-script.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("CREATOR_PUBLICKEY_PLACEHOLDER", creator)
    }
}

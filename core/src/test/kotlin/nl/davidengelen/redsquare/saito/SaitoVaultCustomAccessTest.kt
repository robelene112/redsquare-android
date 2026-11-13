package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SaitoVaultCustomAccessTest {
    @Test fun aCustomKeyUsesTheOfficialWitnessRequestAndReturnsItsAuthenticBoundFile() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-vault-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val custom = reference.getAsJsonObject("custom")
        val holding = SaitoBalanceSnapshot.parse(reference.get("snapshot").asString.toByteArray(),
            reference.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, custom.getAsJsonObject("key").get("envelope").asString)
        val rules = SaitoAccessScript.parse(custom.get("script").asString).withWitnesses(custom.get("witnessInputs").asString)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
        val request = SaitoVault.access(wallet, key, custom.get("timestamp").asLong, rules)
        assertEquals(custom.getAsJsonObject("request").get("networkBase64").asString, request.encoded())
        val response = JsonObject().apply {
            addProperty("status", "success")
            add("txs", JsonArray().apply { add(custom.getAsJsonObject("file").get("envelope").asString) })
        }
        val file = SaitoVault.readFile(key, response.toString())
        assertEquals(custom.get("name").asString, file.name)
        assertEquals("text/plain", file.mime)
        assertArrayEquals(Base64.getDecoder().decode(reference.get("fileContentsBase64").asString), file.bytes)
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaitoRegistryTest {
    @Test fun `excessively nested unsigned metadata is rejected within the response byte limit`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
        val row = reference.getAsJsonArray("rows")[0].asJsonObject.toString()
        val response = "[" + row.dropLast(1) + ",\"metadata\":" +
            "[".repeat(15000) + "0" + "]".repeat(15000) + "}]"
        assertTrue(response.toByteArray(Charsets.UTF_8).size < 64 * 1024)
        assertThrows(JsonParseException::class.java) {
            SaitoRegistry.readNamecheck(response, "NativeCase7@saito", reference.get("registrar").asString)
        }
    }

    @Test fun `a saved registrar directed request restores the exact original signed bytes`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
            .getAsJsonObject("registration")
        val restored = SaitoTransaction.restore(reference.get("networkBase64").asString)
        assertEquals(reference.get("networkBase64").asString, restored.encoded())
        assertEquals(reference.get("envelope").asString, restored.envelope())
    }

    @Test fun `registration addresses the published registrar with the exact independent zero payment bytes`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
            .getAsJsonObject("registration")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val transaction = SaitoRegistry.registration(wallet, "NativeCase7", 1700002000000)
        assertEquals(reference.get("networkBase64").asString, transaction.encoded())
        assertEquals(reference.get("envelope").asString, transaction.envelope())
    }

    @Test fun `an independent synthetic registrar proof resolves the exact registered name and public identity`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
        val name = SaitoRegistry.readNamecheck(reference.getAsJsonArray("rows").toString(),
            "NativeCase7@saito", reference.get("registrar").asString)
        assertEquals("NativeCase7@saito", name?.identifier)
        assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", name?.publicKey)
    }
}

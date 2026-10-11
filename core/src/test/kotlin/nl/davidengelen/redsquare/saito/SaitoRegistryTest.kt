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

    @Test fun `only an exact registrar proof can resolve a name and only an empty array means available`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
        val original = reference.getAsJsonArray("rows")[0].asJsonObject
        val authority = reference.get("registrar").asString
        assertEquals(null, SaitoRegistry.readNamecheck("[]", "NativeCase7@saito", authority))
        val unavailable = listOf("{\"err\":\"no response\"}", "null", "{}", "[null]", "[] trailing")
        for (response in unavailable) assertThrows(Exception::class.java) {
            SaitoRegistry.readNamecheck(response, "NativeCase7@saito", authority)
        }
        // Mutations of the independent signed fixture must fail, never become availability.
        for ((field, value) in listOf("identifier" to "OtherName@saito", "publickey" to SaitoRegistry.REGISTRAR,
            "bid" to "18", "bsh" to "b".repeat(64), "sig" to "0".repeat(128), "signer" to SaitoRegistry.REGISTRAR)) {
            val row = original.deepCopy().apply { addProperty(field, value) }
            assertThrows(Exception::class.java) {
                SaitoRegistry.readNamecheck("[$row]", "NativeCase7@saito", authority)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoRegistry.readNamecheck("[$original,$original]", "NativeCase7@saito", authority)
        }
        // lc is an unsigned server hint; it can mark the valid proof inactive without changing ownership.
        val inactive = original.deepCopy().apply { addProperty("lc", 0) }
        val record = SaitoRegistry.readNamecheck("[$inactive]", "NativeCase7@saito", authority)!!
        assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", record.publicKey)
        assertTrue(!record.active)
        val invalidHint = original.deepCopy().apply { addProperty("lc", 2) }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoRegistry.readNamecheck("[$invalidHint]", "NativeCase7@saito", authority)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoRegistry.readNamecheck(reference.getAsJsonArray("rows").toString(), "NativeCase7@saito")
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

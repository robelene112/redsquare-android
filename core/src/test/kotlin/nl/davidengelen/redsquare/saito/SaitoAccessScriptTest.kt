package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SaitoAccessScriptTest {
    @Test fun officialCustomAndRentalContractsKeepTheirIndependentWasmAccessCommitments() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-vault-access-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        reference.getAsJsonArray("scripts").forEach { value ->
            val row = value.asJsonObject
            assertEquals(row.get("name").asString, row.get("hash").asString,
                SaitoAccessScript.parse(row.get("source").asString).hash)
        }
    }

    @Test fun numericEditorInputRetainsTheSameCommitmentAsTheRustJsonParser() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-vault-access-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        reference.getAsJsonArray("numericScripts").forEach { value ->
            val row = value.asJsonObject
            assertEquals(row.get("name").asString, row.get("hash").asString,
                SaitoAccessScript.parse(row.get("source").asString).hash)
        }
    }
}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SaitoProfilesTest {
    @Test fun `numeric one is authoritative regardless of JSON decimal scale`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-profile-reference.json")!!.reader()).asJsonObject
        val rows = JsonArray().apply {
            add(reference.getAsJsonObject("newerLegacy"))
            add(reference.getAsJsonObject("numericSnapshot"))
        }
        val author = reference.getAsJsonObject("numericSnapshot").get("field2").asString
        assertEquals("Numeric snapshot", SaitoProfiles.readArchive(rows.toString(), author).description)
    }

    @Test fun `editing a bio and removing the avatar signs a complete snapshot preserving other fields`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-profile-reference.json")!!.reader()).asJsonObject
        val row = reference.getAsJsonObject("withMedia")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture, never fund.
        val profile = SaitoProfiles.readArchive(JsonArray().apply { add(row) }.toString(), wallet.publicKey)
        val changes = com.google.gson.JsonObject().apply {
            addProperty("description", "New bio 🌱\nStill public")
            add("image", com.google.gson.JsonNull.INSTANCE)
        }
        val message = SaitoProfiles.message(profile, changes)
        val signed = SaitoEnvelope.decode(SaitoTransaction.create(wallet, message, 1700001000005).envelope())
        assertEquals(wallet.publicKey, signed.author)
        assertEquals(listOf(wallet.publicKey), signed.recipients)
        assertEquals("Profile", signed.body.get("module").asString)
        assertEquals("update profile", signed.body.get("request").asString)
        assertEquals(1, signed.body.get("snapshot").asInt)
        val fields = signed.body.getAsJsonObject("data")
        assertEquals("New bio 🌱\nStill public", fields.get("description").asString)
        org.junit.Assert.assertFalse(fields.has("image"))
        assertEquals("https://images.example.test/banner.png", fields.get("banner").asString)
        assertEquals("https://archive.example.test", fields.getAsJsonArray("archive")[0].asString)
        assertEquals("blue", fields.getAsJsonObject("extension").get("theme").asString)
    }

    @Test fun `the newest signed snapshot overrides later legacy fields and rejects another signer`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-profile-reference.json")!!.reader()).asJsonObject
        val rows = JsonArray().apply {
            for (name in listOf("newerLegacy", "foreign", "legacy", "snapshot")) add(reference.getAsJsonObject(name))
        }
        val author = reference.getAsJsonObject("snapshot").get("field2").asString
        val profile = SaitoProfiles.readArchive(rows.toString(), author)
        assertEquals("Signed bio 🌱\nSecond line", profile.description)
        val fields = JsonParser.parseString(profile.fields).asJsonObject
        assertEquals("https://archive.example.test", fields.getAsJsonArray("archive")[0].asString)
        assertEquals("blue", fields.getAsJsonObject("extension").get("theme").asString)
        assertEquals(1, profile.ignored)
    }
}

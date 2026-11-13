package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SaitoVaultRentalTest {
    private fun reference() = requireNotNull(javaClass.getResourceAsStream("/native-vault-rental-reference.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun aVerifiedDelegatedPathKeepsTheFirstRenterAndActualCreatorRightsAcrossExpiry() {
        val row = reference()
        val path = SaitoVaultRental.parse(row.get("creator").asString, row.get("fileId").asString,
            row.get("masterSource").asString, row.getAsJsonArray("extended").toString())
        val grant = requireNotNull(path.grant)
        assertEquals(row.get("renter").asString, grant.renter)
        assertNotEquals(row.get("next").asString, grant.renter)
        assertEquals(row.get("fileId").asString, grant.fileId)
        assertEquals(row.get("loanHash").asString, grant.loanScript.hash)
        assertEquals(JsonParser.parseString(row.get("loanSource").asString), JsonParser.parseString(grant.loanScript.source))
        row.getAsJsonArray("roles").forEach { value ->
            val clock = value.asJsonObject
            val actors = buildSet {
                if (clock.get("creator").asInt == 1) add(row.get("creator").asString)
                if (clock.get("renter").asInt == 1) add(row.get("renter").asString)
            }
            assertEquals("Actual WASM rules at ${clock.get("time")}", actors, grant.eligibleActorsAt(clock.get("time").asLong))
        }
    }
    @Test fun creatorCustodianAndRenterAppendTheSameSignedHopsAsTheOfficialStoreHook() {
        val row = reference()
        var path = SaitoVaultRental.parse(row.get("creator").asString, row.get("fileId").asString,
            row.get("masterSource").asString, "[]")
        assertNull(path.grant)
        row.getAsJsonArray("extended").forEachIndexed { index, value ->
            val hop = value.asJsonObject
            val payload = JsonParser.parseString(java.util.Base64.getDecoder().decode(hop.get("value").asString).decodeToString()).asJsonObject
            val scalar = listOf("2", "3", "1")[index] // Published unsafe identities only.
            path = path.append(SaitoWallet.fromPrivateKey("0".repeat(63) + scalar), hop.get("to").asString,
                payload.get("expires_at").asLong, payload.get("timestamp").asLong, payload.get("delegated").asInt == 1)
            val expected = com.google.gson.JsonArray().apply { row.getAsJsonArray("extended").take(index + 1).forEach { add(it) } }
            assertEquals(expected, JsonParser.parseString(path.source))
            if (index == 0) assertNull(path.grant)
            else assertEquals(row.get("renter").asString, requireNotNull(path.grant).renter)
        }
        assertThrows(IllegalArgumentException::class.java) {
            path.append(SaitoWallet.fromPrivateKey("0".repeat(63) + "2"), row.get("custodian").asString,
                1700001001000, 1700001000003, true)
        }
    }

    @Test fun rentalCheckoutAndReadUseTheOfficialBodiesAndAuthenticateTheOriginalMasterLockedFile() {
        val row = reference()
        val path = SaitoVaultRental.parse(row.get("creator").asString, row.get("fileId").asString,
            row.get("masterSource").asString, row.getAsJsonArray("hops").toString())
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe identity only.
        val checkout = path.checkout(wallet, 1700001000001)
        val outer = SaitoEnvelope.decode(checkout.envelope())
        assertEquals("vault checkout rental", outer.body.get("request").asString)
        val inner = SaitoEnvelope.decode(outer.body.get("data").asString)
        assertEquals(wallet.publicKey, inner.author)
        assertEquals(row.getAsJsonObject("checkoutMessage"), inner.body)
        val grant = requireNotNull(path.grant)
        // Creator retrieval after expiry still uses the instantiated loan; the
        // returned immutable file remains signed under the original master.
        val request = SaitoEnvelope.decode(grant.access(wallet, 1700001001001).envelope())
        assertEquals(row.getAsJsonObject("accessMessage"), request.body)
        val reply = com.google.gson.JsonObject().apply {
            addProperty("status", "success")
            add("txs", com.google.gson.JsonArray().apply { add(row.getAsJsonObject("file").get("envelope").asString) })
        }
        val file = grant.readFile(reply.toString())
        assertEquals(row.get("fileId").asString, file.id)
        assertArrayEquals(java.util.Base64.getDecoder().decode(row.get("contentsBase64").asString), file.bytes)
        assertThrows(IllegalArgumentException::class.java) {
            SaitoVaultRental.parse(row.get("creator").asString, row.get("fileId").asString,
                row.get("masterSource").asString, row.getAsJsonArray("invalidHops").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoVaultRental.parse(row.get("creator").asString, "00".repeat(64),
                row.get("masterSource").asString, row.getAsJsonArray("hops").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaitoVaultRental.parse(row.get("creator").asString, row.get("fileId").asString,
                """{"op":"UNSUPPORTED_RENTAL"}""", row.getAsJsonArray("hops").toString())
        }
    }

}

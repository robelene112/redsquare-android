package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class SaitoVaultTest {
    private fun reference() = requireNotNull(javaClass.getResourceAsStream("/native-vault-reference.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    @Test fun aWalletWithAnOfficialNftTupleDoesNotCountItsQuantityOrDepositAsSpendableSaito() {
        val vector = reference()
        val snapshot = SaitoBalanceSnapshot.parse(vector.get("snapshot").asString.toByteArray(),
            vector.get("owner").asString, 1700001000000L)
        assertEquals(BigInteger(vector.get("snapshotTotal").asString), snapshot.total)
    }
    @Test fun aStandardKeyBuildsTheOfficialWitnessRequestAndReturnsOnlyItsSignedFile() {
        val vector = reference()
        val holding = SaitoBalanceSnapshot.parse(vector.get("snapshot").asString.toByteArray(),
            vector.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, vector.getAsJsonObject("key").get("envelope").asString)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
        val request = SaitoVault.access(wallet, key, 1700001000001L)
        val body = SaitoEnvelope.decode(request.envelope()).body
        assertEquals(vector.getAsJsonObject("script"), JsonParser.parseString(body.getAsJsonObject("data").get("access_script").asString))
        assertEquals(vector.get("scriptHash").asString, body.getAsJsonObject("data").get("access_hash").asString)
        val response = com.google.gson.JsonObject().apply {
            addProperty("status", "success")
            add("txs", com.google.gson.JsonArray().apply { add(vector.getAsJsonObject("file").get("envelope").asString) })
        }
        val file = SaitoVault.readFile(key, response.toString())
        assertEquals("Field notes.txt", file.name)
        assertEquals("text/plain", file.mime)
        assertArrayEquals(java.util.Base64.getDecoder().decode(vector.get("fileContentsBase64").asString), file.bytes)
    }

    @Test fun anNftOnlyWalletStillUsesTheNonemptyAssetBackupGuard() {
        val vector = reference()
        val withoutOrdinarySaito = vector.get("snapshot").asString.lines().filterIndexed { index, _ -> index != 1 }.joinToString("\n")
        assertEquals(ImportBalance.POSITIVE, ImportBalance.assess(withoutOrdinarySaito.toByteArray(),
            vector.get("owner").asString, 1700001000000L))
    }

    @Test fun anOtherwiseSignedFileMustCarryTheAccessCommitmentForThisVaultKey() {
        val vector = reference()
        val holding = SaitoBalanceSnapshot.parse(vector.get("snapshot").asString.toByteArray(),
            vector.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, vector.getAsJsonObject("missingCommitmentKey").get("envelope").asString)
        val response = com.google.gson.JsonObject().apply {
            addProperty("status", "success")
            add("txs", com.google.gson.JsonArray().apply { add(vector.getAsJsonObject("missingCommitmentFile").get("envelope").asString) })
        }
        assertThrows(IllegalArgumentException::class.java) { SaitoVault.readFile(key, response.toString()) }
    }

    @Test fun theBoundedFourMiBFileMatchesTheIndependentWasmSignatureAndDigest() {
        val vector = nl.davidengelen.redsquare.vaultLargeReference(reference())
        val holding = SaitoBalanceSnapshot.parse(vector.get("snapshot").asString.toByteArray(),
            vector.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, vector.getAsJsonObject("key").get("envelope").asString)
        val response = com.google.gson.JsonObject().apply {
            addProperty("status", "success")
            add("txs", com.google.gson.JsonArray().apply { add(vector.getAsJsonObject("file").get("envelope").asString) })
        }
        val file = SaitoVault.readFile(key, response.toString())
        assertEquals(4 * 1024 * 1024, file.size)
        assertEquals(vector.getAsJsonObject("large").get("sha256").asString,
            java.security.MessageDigest.getInstance("SHA-256").digest(file.bytes).joinToString("") { "%02x".format(it) })
    }

    @Test fun officialNamedDataUrisExportWithTheirMimeEssenceAndExactContents() {
        val vector = reference()
        val holding = SaitoBalanceSnapshot.parse(vector.get("snapshot").asString.toByteArray(),
            vector.get("owner").asString, 1700001000000L).nfts.single()
        val key = SaitoVault.readKey(holding, vector.getAsJsonObject("parameterKey").get("envelope").asString)
        val response = com.google.gson.JsonObject().apply {
            addProperty("status", "success")
            add("txs", com.google.gson.JsonArray().apply { add(vector.getAsJsonObject("parameterFile").get("envelope").asString) })
        }
        val file = SaitoVault.readFile(key, response.toString())
        assertEquals("Field notes.json", file.name)
        assertEquals("application/json", file.mime)
        assertArrayEquals(java.util.Base64.getDecoder().decode(vector.get("fileContentsBase64").asString), file.bytes)
    }

}

package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

class SaitoVaultMintTest {
    @Test fun aStandardUploadBindsItsExactFileToTheIndependentWasmWalletMint() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-vault-mint-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val inputs = vector.getAsJsonArray("inputs").map { value -> value.asJsonObject.let {
            SaitoBalanceSnapshot.Slip(BigInteger(it.get("block").asString), BigInteger(it.get("ordinal").asString),
                it.get("index").asInt, BigInteger(it.get("amount").asString), it.get("type").asInt)
        } }
        val plan = SaitoVaultMint.create(wallet, inputs, vector.get("name").asString, vector.get("mime").asString,
            Base64.getDecoder().decode(vector.get("contentsBase64").asString), vector.get("timestamp").asLong)
        assertEquals(vector.get("nftId").asString, plan.nftId)
        assertEquals(BigInteger("100000000"), plan.deposit)
        assertEquals(vector.getAsJsonObject("file").get("networkBase64").asString, plan.file.encoded())
        assertEquals(vector.getAsJsonObject("mint").get("networkBase64").asString, plan.mint.encoded())
        assertEquals(vector.getAsJsonObject("upload").get("networkBase64").asString,
            plan.uploadRequest(wallet, vector.get("timestamp").asLong + 1).encoded())
        assertThrows(IllegalArgumentException::class.java) { SaitoTransaction.restore(plan.mint.encoded()) }
        assertEquals(vector.get("mintBroadcastBase64").asString, Base64.getEncoder().encodeToString(
            plan.mint.broadcast(wallet, vector.get("destination").asString)))
    }
    @Test fun aFourMiBUploadMatchesTheIndependentSignaturesWithoutWideningGenericEnvelopeAdmission() {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-vault-mint-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val large = vector.getAsJsonObject("large")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val inputs = vector.getAsJsonArray("inputs").map { value -> value.asJsonObject.let {
            SaitoBalanceSnapshot.Slip(BigInteger(it.get("block").asString), BigInteger(it.get("ordinal").asString),
                it.get("index").asInt, BigInteger(it.get("amount").asString), it.get("type").asInt)
        } }
        val bytes = ByteArray(large.get("size").asInt) { ((it * 31 + 7) and 255).toByte() }
        val plan = SaitoVaultMint.create(wallet, inputs, large.get("name").asString, large.get("mime").asString,
            bytes, vector.get("timestamp").asLong)
        assertEquals(large.get("fileSignature").asString, plan.file.id)
        assertEquals(large.getAsJsonObject("mint").get("networkBase64").asString, plan.mint.encoded())
        val upload = plan.uploadRequest(wallet, vector.get("timestamp").asLong + 1)
        assertEquals(large.get("uploadSignature").asString, upload.id)
        assertEquals(large.get("uploadWireSha256").asString,
            java.security.MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(upload.encoded()))
                .joinToString("") { "%02x".format(it) })
        assertThrows(IllegalArgumentException::class.java) { SaitoEnvelope.decode(upload.envelope()) }
    }

}

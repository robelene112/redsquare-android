package nl.davidengelen.redsquare

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Independent Saito fixtures in the separate test APK, never an installed user's wallet. */
@RunWith(AndroidJUnit4::class)
class NativeCompatibilityTest {
    private val syntheticKey = "0".repeat(63) + "1"
    private val expectedPublicKey = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
    private val expectedSignature = "8008843d4e0261c035aea14623aba44be40389f0492f3979f8a4b5ac1742a0337cacc2c608402a297695beb98398f52f406c6ed1247f713d97fe8b19975a16e4"

    @Test fun installedAndroidRuntimeMatchesIndependentSaitoIdentityAndSignatures() {
        val wallet = SaitoWallet.fromPrivateKey(syntheticKey)
        assertEquals(expectedPublicKey, wallet.publicKey)
        assertEquals(expectedSignature, wallet.sign(byteArrayOf()))
        assertTrue(SaitoWallet.verify(byteArrayOf(), expectedSignature, expectedPublicKey))
        assertFalse(SaitoWallet.verify(byteArrayOf(1), expectedSignature, expectedPublicKey))
    }

    @Test fun compatibleWalletRoundTripPreservesModuleDataAndRejectsInvalidIdentity() {
        val original = JsonParser.parseString(SaitoWallet.fromPrivateKey(syntheticKey).exportJson()).asJsonObject
        original.add("unknownModule", JsonNull.INSTANCE)
        original.add("keychain", JsonParser.parseString("""{"keys":[{"aes_secret":"public synthetic test state"}]}"""))
        val restored = SaitoWallet.fromBackup(original.toString())
        assertEquals(expectedPublicKey, restored.publicKey)
        assertEquals(original, JsonParser.parseString(restored.exportJson()))
        original.getAsJsonObject("wallet").addProperty("publicKey", "invalid")
        assertThrows(IllegalArgumentException::class.java) { SaitoWallet.fromBackup(original.toString()) }
    }
}

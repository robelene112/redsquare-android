package nl.davidengelen.redsquare

import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/** Controlled crash-state fixtures on fresh .dev installs, never user storage. */
class WalletInterruptedInitializationTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun incompleteFirstWriteCanResumeCreation() {
        checkInterruptedCreation(completeSave = false)
    }

    @Test fun validSaveBeforePublicationResumesTheSavedIdentity() {
        checkInterruptedCreation(completeSave = true)
    }

    private fun checkInterruptedCreation(completeSave: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val directory = context.noBackupFilesDir
        assertFalse(File(directory, "wallet.v1").exists())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .containsAlias("redsquare.wallet.v1"))
        File(directory, "wallet.initializing").writeBytes(byteArrayOf())
        val key = KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("redsquare.wallet.v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        val envelope = if (completeSave) {
            // Public WASM reference identity, synthetic and unsuitable for funds.
            val backup = """{"wallet":{"version":5.677,"publicKey":"jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR","privateKey":"0000000000000000000000000000000000000000000000000000000000000001"}}"""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD("${context.packageName}:wallet.v1".toByteArray())
            byteArrayOf(1) + cipher.iv + cipher.doFinal(backup.toByteArray())
        } else byteArrayOf(1, 0, 0)
        File(directory, "wallet.v1").writeBytes(envelope)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { activity ->
            ui.onNodeWithText("Wallet").performClick()
            val address = displayedWalletAddress(ui)
            if (completeSave) assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", address)
            else assertEquals(44, address.length)
            ui.onNodeWithText("Done").performClick()
            activity.recreate()
            ui.onNodeWithText("Wallet").performClick()
            assertEquals(address, displayedWalletAddress(ui))
        }
    }
}

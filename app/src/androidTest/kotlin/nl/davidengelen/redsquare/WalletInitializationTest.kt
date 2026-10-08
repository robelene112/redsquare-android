package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.KeyStore

/** Run first on a fresh isolated .dev installation, before any wallet UI test. */
class WalletInitializationTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun failedFirstSaveCanRetryWithoutPublishingAnUnsavedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("nl.davidengelen.redsquare.dev", context.packageName)
        val directory = context.noBackupFilesDir
        assertFalse(File(directory, "wallet.v1").exists())
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .containsAlias("redsquare.wallet.v1"))
        assertTrue(directory.setWritable(false, false))
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                ui.waitUntil(10_000) { ui.onAllNodes(hasText("Retry")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
                ui.onNodeWithText("Done").performClick()
                assertTrue(directory.setWritable(true, true))
                ui.onNodeWithText("Retry").performClick()
                ui.onNodeWithText("Wallet").performClick()
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasContentDescription("Wallet address")).fetchSemanticsNodes().isNotEmpty()
                }
                val address = ui.onNodeWithContentDescription("Wallet address")
                    .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
                assertEquals(44, address.length)
            }
        } finally {
            assertTrue(directory.setWritable(true, true))
        }
    }
}

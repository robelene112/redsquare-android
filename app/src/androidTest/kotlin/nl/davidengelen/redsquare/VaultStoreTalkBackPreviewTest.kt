package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The existing real TalkBack service and completed English speech, with no device/payment writes. */
class VaultStoreTalkBackPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun vaultOfflineResultAndFileActionsHaveNativeContextAndCompletedSpeech() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "vault-store-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            withVaultReadingFixture("talkback-file") { scene ->
                ui.openNativeVault()
                ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
                focusAndHear("vault-keep", "Keep offline", folder, textLabel = true)
                ui.onNodeWithText("Keep offline").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
                focusAndHear("vault-offline-status", "Available offline", folder, textLabel = true, requireClickAction = false)
                focusAndHear("vault-export", "Save as…", folder, textLabel = true)
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            }
        }
    }
    @Test fun rentalPurchaseTermsTotalAndPaymentControlHaveCompletedContextualSpeechWithoutSending() {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "vault-store-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            withStorePurchaseFixture("talkback-rental", rental = true) { scene ->
                ui.openStorePurchaseProduct(illustrated = true)
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                val terms = hasText("Temporary file access") and hasAnyAncestor(isDialog())
                ui.onNode(terms).performScrollTo()
                focusAndHear("rental-terms", "Temporary file access", folder, textLabel = true, requireClickAction = false)
                ui.onNode(hasText("Total: 2.5 SAITO") and hasAnyAncestor(isDialog())).performScrollTo()
                focusAndHear("rental-total", "Total: 2.5 SAITO", folder, textLabel = true, requireClickAction = false)
                ui.onNodeWithText("Confirm and pay").performScrollTo()
                focusAndHear("rental-confirm", "Confirm and pay", folder, textLabel = true)
                assertTrue(scene.node.peer.transactions.isEmpty())
            }
        }
    }
}

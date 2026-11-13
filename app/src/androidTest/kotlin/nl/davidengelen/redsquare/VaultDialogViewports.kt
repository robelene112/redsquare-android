package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.MotionDurationScale
import org.junit.Assert.*
import java.io.File

/** Match the system's zero scale in the Compose rule's substituted test recomposer. */
internal object VaultViewportMotionScale : MotionDurationScale {
    override val scaleFactor: Float = 0f
}

/** Existing real device configuration sweep, through the actual module/window controls. */
internal fun ComposeTestRule.withVaultDialogViewports(name: String, custom: Boolean = false,
    prepare: ComposeTestRule.(VaultReadingScene) -> Unit = {},
    cell: ComposeTestRule.(VaultReadingScene, String) -> Unit) {
    withNativeModuleMotionScale(0) {
    for (width in listOf(320, 412)) {
        withVaultReadingFixture("$name-viewport-$width", custom = custom, retainTransfers = true,
            captureFolder = "vault-$name-viewport-evidence") { scene ->
            openNativeVault()
            waitUntil(15_000) { onNodeWithText(if (custom) "Custom field notes.txt" else "Field notes.txt").isDisplayed() }
            prepare(scene)
            switchStackModule("Vault", "Red Square", scrollTarget = true)
            withPostViewports(scene.activity, scene.evidence, "vault-$name-$width", listOf(1f, 1.3f, 2f), width) { label, _ ->
                switchStackModule("Red Square", "Vault", scrollTarget = true)
                observeModuleWindowMotionScale(scene.activity, 0, File(scene.evidence, "$label-motion").apply { mkdirs() })
                cell(scene, label)
                assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
                switchStackModule("Vault", "Red Square", scrollTarget = true)
                waitUntil(5_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
    }
}
